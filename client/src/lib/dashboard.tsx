import {
  createContext,
  use,
  useCallback,
  useRef,
  useState,
  type PropsWithChildren,
} from 'react';

import { apiClient, UnauthorizedError } from './apiClient';
import { useAuth } from './auth';
import type { DashboardSummary, SeriesBucket } from './types';

/** How often the strip and the Dashboard refresh while someone is looking at them. Largata's
 *  outbox delivers every minute or so, so "live" is one to two minutes behind whatever this is. */
export const DASHBOARD_POLL_MS = 30_000;

interface DashboardContextValue {
  /** The last good numbers — null until the first fetch succeeds. A failed refresh never
   *  clears them; their `asOf` says how old they are. */
  summary: DashboardSummary | null;
  /** Set only by a loud load that failed; the screens show it without hiding `summary`. */
  error: string | null;
  /** The latest refresh of any kind failed, so `summary` is older than it looks. Quiet ticks
   *  raise no banner, but the screen must still be able to say "couldn't refresh" — otherwise a
   *  worklog-side outage reads exactly like Largata going quiet. */
  stale: boolean;
  /** Fetch the summary. Quiet loads (poll ticks) fail silently, per useFocusedPolling. */
  load: (options: { quiet: boolean }) => void;
}

const DashboardContext = createContext<DashboardContextValue | null>(null);

export function useDashboard(): DashboardContextValue {
  const value = use(DashboardContext);
  if (!value) {
    throw new Error('useDashboard must be used within a DashboardProvider');
  }
  return value;
}

/**
 * The calendar the Dashboard counts in: UTC, for everyone (developer's call, 2026-10-05 — the
 * team bases its times on UTC). "Today", "active today" and every day / month / year bucket are
 * UTC days, so every Member sees the same numbers wherever they are. Times of day ("last event
 * 4m ago", "as of 2:31 PM") still display in the viewer's local time. The API still accepts any
 * IANA zone; this is the one place to change if that call is revisited.
 */
export const DASHBOARD_ZONE = 'UTC';

/** Today's date in the Dashboard's calendar (UTC), `n` days back, as `YYYY-MM-DD`. */
function utcDateDaysAgo(n: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
}

/**
 * One fetch of the Dashboard's summary, shared by the Reports-tab strip and the Dashboard
 * screen so the two can never disagree and never poll twice. The provider itself never polls:
 * whichever of the two screens is focused drives it through useFocusedPolling, so nothing is
 * fetched while neither is on screen.
 */
export function DashboardProvider({ children }: PropsWithChildren) {
  const { session, logout } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [stale, setStale] = useState(false);
  // Only the newest request may write: a slow response must not overwrite a newer one.
  const latest = useRef(0);

  const token = session?.token ?? null;

  const load = useCallback(
    ({ quiet }: { quiet: boolean }) => {
      if (!token) return;
      const request = ++latest.current;
      apiClient
        .dashboardSummary(DASHBOARD_ZONE, token)
        .then((result) => {
          if (request !== latest.current) return;
          setSummary(result);
          setError(null);
          setStale(false);
        })
        .catch((e: unknown) => {
          if (e instanceof UnauthorizedError) return void logout();
          if (request !== latest.current) return;
          setStale(true);
          if (quiet) return;
          setError(e instanceof Error ? e.message : 'Could not load the Largata numbers.');
        });
    },
    [token, logout],
  );

  // Signed out, there are no numbers — derived rather than cleared, as with the inbox, so a
  // previous session's figures can never flash on screen.
  return (
    <DashboardContext
      value={{
        summary: token ? summary : null,
        error: token ? error : null,
        stale: token ? stale : false,
        load,
      }}>
      {children}
    </DashboardContext>
  );
}

/** What the Over-time chart shows: Travelers active per bucket (the hero card), or one
 *  counter's created count per bucket (a totals tile). */
export type ChartPick = { kind: 'active' } | { kind: 'counter'; counter: string };

/** One chart, whichever series feeds it: a value per bucket, oldest first, zeros included. */
export interface ChartSeries {
  key: string;
  bucket: SeriesBucket;
  points: { start: string; value: number }[];
}

/** How many day buckets the chart shows — two weeks reads at a glance on a phone. Months and
 *  years use the server's defaults (the last 12; every year with data). */
export const CHART_DAYS = 14;

export function chartKey(pick: ChartPick, bucket: SeriesBucket): string {
  return `${pick.kind === 'active' ? 'active' : `counter:${pick.counter}`}|${bucket}`;
}

/**
 * The Dashboard's chart series for the current pick and bucket. Local to the screen — the
 * strip never shows it — but refreshed on the same poll: the screen folds `load` into its
 * polling callback, and because `load` changes identity with the selection, a pick or bucket
 * change restarts the poll and fetches the new series at once.
 *
 * `series` is only ever the one matching the current selection: numbers for "trips by day"
 * must never sit under a picker that already says "postcards by month".
 */
export function useChartSeries(pick: ChartPick | null, bucket: SeriesBucket) {
  const { session, logout } = useAuth();
  const [series, setSeries] = useState<ChartSeries | null>(null);
  const [error, setError] = useState<{ key: string; message: string } | null>(null);
  const latest = useRef(0);
  const token = session?.token ?? null;
  const key = pick ? chartKey(pick, bucket) : null;
  // Plain values, so a new-but-equal pick object never restarts the poll.
  const hasPick = pick !== null;
  const counter = pick?.kind === 'counter' ? pick.counter : null;

  const load = useCallback(
    ({ quiet }: { quiet: boolean }) => {
      if (!token || !hasPick || !key) return;
      const request = ++latest.current;
      const zone = DASHBOARD_ZONE;
      // The UTC date, matching the calendar the server buckets in.
      const from = bucket === 'day' ? utcDateDaysAgo(CHART_DAYS - 1) : undefined;
      const fetched: Promise<ChartSeries> =
        counter === null
          ? apiClient.dashboardActive(bucket, zone, token, from).then((r) => ({
              key,
              bucket,
              points: r.points.map((p) => ({ start: p.start, value: p.active })),
            }))
          : apiClient.dashboardSeries(counter, bucket, zone, token, from).then((r) => ({
              key,
              bucket,
              points: r.points.map((p) => ({ start: p.start, value: p.created })),
            }));
      fetched
        .then((result) => {
          if (request !== latest.current) return;
          setSeries(result);
          setError(null);
        })
        .catch((e: unknown) => {
          if (e instanceof UnauthorizedError) return void logout();
          if (quiet || request !== latest.current) return;
          setError({ key, message: e instanceof Error ? e.message : 'Could not load the chart.' });
        });
    },
    [token, logout, key, hasPick, counter, bucket],
  );

  return {
    series: token && series && series.key === key ? series : null,
    error: token && error && error.key === key ? error.message : null,
    load,
  };
}

import { useCallback, useMemo, useState } from 'react';
import { router } from 'expo-router';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import {
  ReportListEmpty,
  ReportListError,
  ReportListSkeleton,
} from '@/components/ReportListStates';
import { LargataStrip } from '@/components/dashboard/LargataStrip';
import { useTabBarAction, useTabBarCenter } from '@/components/nav/TabBarAction';
import { ReportRow } from '@/components/ReportRow';
import { StatusSheet } from '@/components/StatusSheet';
import { Card, Eyebrow, FadeInView, Scroll } from '@/components/ui';
import { noTextSelect, type PressState } from '@/components/ui/press';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { buildHandoffText } from '@/lib/handoffText';
import { DASHBOARD_POLL_MS, useDashboard } from '@/lib/dashboard';
import { useReports } from '@/lib/reports';
import { useNow } from '@/lib/useNow';
import { OPEN_STATUSES, STATUS_LABELS } from '@/lib/reportStatus';
import type { ReportResponse, ReportStatus } from '@/lib/types';
import { useFocusedPolling } from '@/lib/useFocusedPolling';
import { colors, fonts, radius, space, TAB_BAR_CLEARANCE, type } from '@/theme';

// The top-level split: what's still live, versus the two archives. Everything open shares one
// segment because the point of the inbox is "what needs us", not "which of five buckets".
type Segment = 'open' | 'done' | 'dismissed';

const SEGMENTS: { key: Segment; label: string }[] = [
  { key: 'open', label: 'Open' },
  { key: 'done', label: 'Done' },
  { key: 'dismissed', label: 'Dismissed' },
];

// Slow enough to be invisible on a 4-person team's volume, quick enough that a report filed
// mid-conversation shows up before anyone asks "did it come through?".
const POLL_INTERVAL_MS = 60_000;

// The inbox opens on New: untriaged feedback is the thing that actually needs someone, and
// landing on the full open list made "nothing is selected" look like "something is broken".
// Clearing the chip still gets you every open report — that view is now something you choose,
// not where you land.
const DEFAULT_OPEN_FILTER: ReportStatus = 'new';

// Extra list padding while a hand-off error sits above the pill, so the last row can still
// scroll clear of it. The action itself takes the pill's own place, so needs none.
const HANDOFF_ERROR_CLEARANCE = 64;

export default function ReportsInbox() {
  const { reports, error, refresh, refreshQuietly, changeStatus } = useReports();
  const [segment, setSegment] = useState<Segment>('open');
  // Which slice of Open is showing; null means every open report. Starts on New, and resets
  // to New whenever you come back to the Open segment.
  const [narrow, setNarrow] = useState<ReportStatus | null>(DEFAULT_OPEN_FILTER);
  const [sheetFor, setSheetFor] = useState<ReportResponse | null>(null);
  const [saving, setSaving] = useState<ReportStatus | null>(null);
  const [writeError, setWriteError] = useState<string | null>(null);
  // Hand-off selection (Story 28, web only for now). Ids, not rows, and independent of the
  // chip filter: a Member can tick New and In-progress Reports across chips.
  const { session, logout } = useAuth();
  const [selecting, setSelecting] = useState(false);
  const [selected, setSelected] = useState<ReadonlySet<string>>(() => new Set());
  const [handingOff, setHandingOff] = useState(false);
  const [handoffError, setHandoffError] = useState<string | null>(null);

  // Feedback lands while you are elsewhere in the app, so the inbox refetches on focus — and
  // then keeps itself current while you sit here, because a triage session can outlast the
  // reports it started with. Neither refetch clears the badge: the count follows the data,
  // not the visit.
  //
  // The timer exists only while this tab is focused AND the app is in the foreground, and
  // ticks are quiet — both rules live in the shared hook. Coming back to the foreground
  // refetches through the provider already, so the hook only restarts the clock. (This only
  // shortens the *display* wait; the upstream delivery lag is a separate problem.)
  const loadInbox = useCallback(
    ({ quiet }: { quiet: boolean }) => (quiet ? refreshQuietly() : refresh()),
    [refresh, refreshQuietly],
  );
  useFocusedPolling(loadInbox, POLL_INTERVAL_MS);

  // The Largata strip has its own, faster clock: it is a pulse, not a to-do list. Its provider
  // does not refetch on foreground by itself, so the hook does it here.
  const dashboard = useDashboard();
  useFocusedPolling(dashboard.load, DASHBOARD_POLL_MS, { refetchOnForeground: true });
  const now = useNow(DASHBOARD_POLL_MS, dashboard.summary?.asOf);

  const openReports = useMemo(
    () => (reports ?? []).filter((r) => OPEN_STATUSES.includes(r.status)),
    [reports],
  );

  const visible = useMemo(() => {
    if (!reports) return [];
    if (segment === 'open') {
      return narrow ? openReports.filter((r) => r.status === narrow) : openReports;
    }
    return reports.filter((r) => r.status === segment);
  }, [reports, openReports, segment, narrow]);

  // A polled refetch can move a ticked Report out of Open mid-selection; only the ones still
  // open count, and only they are handed off.
  const chosen = useMemo(
    () => openReports.filter((r) => selected.has(r.id)),
    [openReports, selected],
  );

  function selectSegment(next: Segment) {
    setSegment(next);
    setNarrow(DEFAULT_OPEN_FILTER);
    // Selection belongs to the Open view; leaving it is leaving the selection.
    if (next !== 'open') cancelSelection();
  }

  function startSelection() {
    setSelected(new Set());
    setHandoffError(null);
    setSelecting(true);
  }

  function cancelSelection() {
    setSelecting(false);
    setSelected(new Set());
    setHandoffError(null);
  }

  function toggle(report: ReportResponse) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(report.id)) next.delete(report.id);
      else next.add(report.id);
      return next;
    });
  }

  // No confirmation: the deliberate act of selecting is the only step (spec, story 7).
  async function handOff() {
    if (!session || chosen.length === 0 || handingOff) return;
    setHandingOff(true);
    setHandoffError(null);
    try {
      const draft = buildHandoffText(chosen, session.user.name, new Date());
      const handoff = await apiClient.createHandoff(draft.reportIds, draft.text, session.token);
      cancelSelection();
      router.push({ pathname: '/handoff/[id]', params: { id: handoff.id } });
    } catch (e) {
      if (e instanceof UnauthorizedError) return void logout();
      // Nothing was recorded; the selection stays so a retry is one press.
      setHandoffError(e instanceof Error ? e.message : 'Could not hand those reports off.');
    } finally {
      setHandingOff(false);
    }
  }

  const canHandOff = Platform.OS === 'web' && segment === 'open';

  // Hand off starts from the centre disc (web only, Open only): on this tab the disc's own job
  // is this screen's action. Kept while selecting too, so cancelling lands back on it.
  useTabBarCenter(
    canHandOff ? { icon: 'send', label: 'Hand off reports', onPress: startSelection } : null,
  );

  // While ticking, the tab pill becomes the hand-off action: leaving the tab would only lose
  // the selection, so the navigation's place goes to the one thing that finishes the task.
  // It grows out of the centre disc that started it.
  useTabBarAction(
    selecting
      ? {
          label: `Hand off (${chosen.length})`,
          icon: 'send',
          onPress: handOff,
          onCancel: cancelSelection,
          cancelLabel: 'Cancel hand-off',
          disabled: chosen.length === 0,
          loading: handingOff,
          error: handoffError,
          origin: 'center',
        }
      : null,
  );

  async function move(report: ReportResponse, status: ReportStatus) {
    if (status === report.status) {
      setSheetFor(null);
      return; // Selecting the current status is a no-op — no request (spec §4).
    }
    setSaving(status);
    setWriteError(null);
    try {
      await changeStatus(report.id, status);
      setSheetFor(null);
    } catch (e) {
      setWriteError(e instanceof Error ? e.message : 'Could not update that report.');
    } finally {
      setSaving(null);
    }
  }

  const showSkeleton = reports === null && !error;
  // An error never blanks a list we already have — it sits above the stale data (spec §3).
  const showError = error !== null;

  return (
    <View style={styles.screen}>
      <Scroll
        contentContainerStyle={[
          styles.content,
          { paddingBottom: TAB_BAR_CLEARANCE + (selecting && handoffError ? HANDOFF_ERROR_CLEARANCE : 0) },
        ]}
        showsVerticalScrollIndicator={false}>
        <FadeInView>
          {/* 1b's header is compact — the eyebrow carries §6's "From Largata" provenance so
              the source of this feedback stays stated. The open count that sat beside the title
              was dropped (2026-10-07): the status chips below already carry each count. */}
          <Eyebrow>From Largata</Eyebrow>
          <View style={styles.titleRow}>
            <Text style={styles.title}>Inbox</Text>
            <View style={styles.titleSide}>
              {/* Every past Handoff, to reopen and copy again — web only, like making one. While
                  selecting it stays mounted but invisible and inert (the pill carries the
                  action), so the header keeps its exact size and nothing below it jumps. */}
              {Platform.OS === 'web' && (
                <Pressable
                  onPress={() => router.push('/handoffs')}
                  disabled={selecting}
                  accessibilityRole="button"
                  accessibilityLabel="Past handoffs"
                  aria-hidden={selecting}
                  accessibilityElementsHidden={selecting}
                  importantForAccessibility={selecting ? 'no-hide-descendants' : 'auto'}
                  style={({ pressed, hovered }: PressState) => [
                    styles.handOffBtn,
                    styles.handoffsBtn,
                    hovered && styles.subChipHover,
                    pressed && styles.subChipPressed,
                    selecting && styles.headerBtnResting,
                  ]}>
                  <Feather name="clock" size={14} color={colors.text} />
                  <Text style={styles.handoffsText}>Handoffs</Text>
                </Pressable>
              )}
            </View>
          </View>
        </FadeInView>

        {/* Usage sits above the chips and outside the list: Events are never Inbox items. */}
        <FadeInView delay={30}>
          <LargataStrip
            summary={dashboard.summary}
            failed={dashboard.error !== null}
            now={now}
            onPress={() => router.push('/dashboard')}
          />
        </FadeInView>

        <FadeInView delay={50}>
          <View style={styles.segmented}>
            {SEGMENTS.map((s) => (
              <Pressable
                key={s.key}
                onPress={() => selectSegment(s.key)}
                accessibilityRole="button"
                accessibilityState={{ selected: segment === s.key }}
                style={({ pressed }: PressState) => [
                  styles.segment,
                  segment === s.key && styles.segmentActive,
                  pressed && styles.segmentPressed,
                ]}>
                <Text style={[styles.segmentText, segment === s.key && styles.segmentTextActive]}>
                  {s.label}
                </Text>
              </Pressable>
            ))}
          </View>
        </FadeInView>

        {/* Sub-chips narrow *within* Open — the three archives need no further slicing.
            The screen lands on New; clearing the active chip widens to every open report. */}
        {segment === 'open' && (
          <FadeInView delay={90}>
            <View style={styles.subChips}>
              {OPEN_STATUSES.map((status) => {
                const count = openReports.filter((r) => r.status === status).length;
                const active = narrow === status;
                return (
                  <Pressable
                    key={status}
                    onPress={() => setNarrow(active ? null : status)}
                    accessibilityRole="button"
                    accessibilityState={{ selected: active }}
                    accessibilityLabel={`${STATUS_LABELS[status]}, ${count}`}
                    style={({ pressed, hovered }: PressState) => [
                      styles.subChip,
                      active && styles.subChipActive,
                      hovered && !active && styles.subChipHover,
                      pressed && styles.subChipPressed,
                    ]}>
                    <Text style={[styles.subChipText, active && styles.subChipTextActive]}>
                      {STATUS_LABELS[status]}
                    </Text>
                    {count > 0 && (
                      <Text style={[styles.subChipCount, active && styles.subChipTextActive]}>
                        {count}
                      </Text>
                    )}
                  </Pressable>
                );
              })}
            </View>
          </FadeInView>
        )}

        {showError && <ReportListError onRetry={refresh} />}
        {writeError && <Text style={styles.writeError}>{writeError}</Text>}

        {showSkeleton && <ReportListSkeleton />}

        {reports !== null && visible.length === 0 && !showError && (
          <ReportListEmpty
            title={emptyTitle(segment, narrow)}
            body={
              segment === 'open' && !narrow
                ? 'No reports are waiting. New feedback from Largata lands here.'
                : 'Try another filter to see the rest of the inbox.'
            }
          />
        )}

        {visible.length > 0 && (
          // One flush card holding every row, so the list reads as a single surface with
          // hairline dividers rather than a stack of floating cards.
          <Card flush style={styles.list}>
            {visible.map((report, i) => (
              <FadeInView key={report.id} delay={Math.min(140 + i * 60, 480)}>
                <View style={i > 0 ? styles.divider : undefined}>
                  <ReportRow
                    report={report}
                    selection={
                      selecting
                        ? { selected: selected.has(report.id), onToggle: toggle }
                        : undefined
                    }
                    onPress={(r) => router.push({ pathname: '/report/[id]', params: { id: r.id } })}
                    // A fresh sheet, not one still showing last attempt's failure.
                    onTriage={(r) => {
                      setWriteError(null);
                      setSheetFor(r);
                    }}
                  />
                </View>
              </FadeInView>
            ))}
          </Card>
        )}

        {visible.length > 0 && (
          /* The long-press is the only way to triage from this list and nothing on screen
             advertises it, so this hint is load-bearing rather than decorative. */
          <Text style={styles.hint}>
            {selecting
              ? 'Tap to tick the reports to hand off'
              : 'Tap to read · press and hold to move'}
          </Text>
        )}
      </Scroll>

      <StatusSheet
        report={sheetFor}
        saving={saving}
        error={writeError}
        onSelect={(status) => sheetFor && move(sheetFor, status)}
        onClose={() => {
          setSheetFor(null);
          setWriteError(null);
        }}
      />
    </View>
  );
}

function emptyTitle(segment: Segment, narrow: ReportStatus | null): string {
  if (segment === 'open') {
    return narrow ? `No ${STATUS_LABELS[narrow].toLowerCase()} reports` : 'Nothing open';
  }
  return `No ${STATUS_LABELS[segment].toLowerCase()} reports`;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { paddingHorizontal: space.lg, paddingTop: space.lg, gap: space.md },

  titleRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  titleSide: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  handOffBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    minHeight: 36,
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.brand,
    backgroundColor: colors.surface,
    cursor: 'pointer',
    ...noTextSelect,
  },
  handoffsBtn: { borderColor: colors.cardBorder },
  headerBtnResting: { opacity: 0, cursor: 'auto' },
  handoffsText: { fontFamily: fonts.bold, fontSize: 12.5, color: colors.text },

  title: { ...type.display },

  segmented: {
    flexDirection: 'row',
    backgroundColor: colors.hairline,
    borderRadius: radius.pill,
    padding: 3,
  },
  segment: {
    flex: 1,
    minHeight: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.pill,
    cursor: 'pointer',
    ...noTextSelect,
  },
  segmentActive: { backgroundColor: colors.surface },
  segmentPressed: { opacity: 0.85 },
  segmentText: { fontFamily: fonts.bold, fontSize: 13, color: colors.textMuted },
  segmentTextActive: { color: colors.text },

  subChips: { flexDirection: 'row', gap: space.sm, flexWrap: 'wrap' },
  subChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    minHeight: 44,
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface,
    cursor: 'pointer',
    ...noTextSelect,
  },
  subChipActive: { backgroundColor: colors.brand, borderColor: colors.brand },
  subChipHover: { backgroundColor: colors.brandSoft },
  subChipPressed: { transform: [{ scale: 0.97 }] },
  subChipText: { fontFamily: fonts.bold, fontSize: 12.5, color: colors.text },
  subChipTextActive: { color: colors.onBrand },
  subChipCount: { fontFamily: fonts.bold, fontSize: 11.5, color: colors.textMuted },

  list: { marginTop: space.xs },
  divider: { borderTopWidth: 1, borderTopColor: colors.hairline },
  hint: { ...type.caption, textAlign: 'center', marginTop: space.xs },
  writeError: { ...type.body, color: colors.brand },
});

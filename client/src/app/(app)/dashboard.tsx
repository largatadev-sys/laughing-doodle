import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { DashboardHeader } from '@/components/dashboard/DashboardHeader';
import { DashboardSkeleton } from '@/components/dashboard/DashboardSkeleton';
import { HeroCard } from '@/components/dashboard/HeroCard';
import { OverTimeCard } from '@/components/dashboard/OverTimeCard';
import { SilentBanner } from '@/components/dashboard/SilentBanner';
import { TotalsRow } from '@/components/dashboard/TotalsRow';
import { Card, FadeInView, PillButton, Scroll } from '@/components/ui';
import { DASHBOARD_POLL_MS, useChartSeries, useDashboard, type ChartPick } from '@/lib/dashboard';
import type { SeriesBucket } from '@/lib/types';
import { useFocusedPolling } from '@/lib/useFocusedPolling';
import { useNow } from '@/lib/useNow';
import { colors, space, type } from '@/theme';

const ACTIVE: ChartPick = { kind: 'active' };

/**
 * Largata usage, live, laid out for a phone (design v2, "Activity"): whether Largata is alive
 * sits in the header, today's active Travelers are the hero, the totals scroll sideways and
 * double as the chart's picker, and the chart is tap-to-inspect. Read-only — nothing here
 * acts. It refreshes every 30 s while focused and foregrounded; a failed refresh leaves the
 * last good numbers on screen and the header says so.
 *
 * This file is the wiring — state, polling, layout. The views live in components/dashboard/.
 */
export default function Dashboard() {
  const { summary, error, stale, load } = useDashboard();

  // The chart shows the hero's series until someone taps a totals tile.
  const [pick, setPick] = useState<ChartPick>(ACTIVE);
  const [bucket, setBucket] = useState<SeriesBucket>('day');
  const [selected, setSelected] = useState(-1); // −1 = the latest bucket
  const chart = useChartSeries(pick, bucket);
  const loadChart = chart.load;

  // One poll for both: the headline numbers and the chart refresh together, and a pick or
  // bucket change (a new loadChart) restarts the poll with an immediate fetch.
  const loadAll = useCallback(
    (options: { quiet: boolean }) => {
      load(options);
      loadChart(options);
    },
    [load, loadChart],
  );
  useFocusedPolling(loadAll, DASHBOARD_POLL_MS, { refetchOnForeground: true });
  const now = useNow(DASHBOARD_POLL_MS, summary?.asOf);

  // The header floats over the content, so the content starts below wherever it measures.
  const [headerHeight, setHeaderHeight] = useState(0);

  // A new pick or bucket inspects its latest bar again.
  function choose(next: ChartPick) {
    setPick(next);
    setSelected(-1);
  }
  function chooseBucket(next: SeriesBucket) {
    setBucket(next);
    setSelected(-1);
  }

  return (
    <View style={styles.screen}>
      <Scroll
        contentContainerStyle={[styles.content, { paddingTop: headerHeight + space.lg }]}
        showsVerticalScrollIndicator={false}>
        {summary === null && error === null && <DashboardSkeleton />}

        {summary === null && error !== null && (
          <Card style={[styles.inset, styles.errorCard]}>
            <View style={styles.row}>
              <Feather name="wifi-off" size={18} color={colors.brand} />
              <Text style={type.heading}>Couldn’t load the numbers</Text>
            </View>
            <Text style={type.caption}>{error}</Text>
            <PillButton label="Try again" variant="outline" onPress={() => load({ quiet: false })} style={styles.retry} />
          </Card>
        )}

        {summary !== null && (
          <>
            {summary.silent && (
              <FadeInView style={styles.inset}>
                <SilentBanner summary={summary} now={now} />
              </FadeInView>
            )}

            <FadeInView style={styles.inset}>
              <HeroCard
                activeToday={summary.activeToday}
                selected={pick.kind === 'active'}
                onPress={() => choose(ACTIVE)}
              />
            </FadeInView>

            <FadeInView delay={80}>
              <Text style={[styles.eyebrow, styles.inset]}>Totals</Text>
              {summary.totals.length === 0 ? (
                <Text style={[type.caption, styles.inset]}>
                  No counters yet. Totals appear with Largata’s first Snapshot or Event.
                </Text>
              ) : (
                <TotalsRow
                  totals={summary.totals}
                  picked={pick.kind === 'counter' ? pick.counter : null}
                  onPick={(counter) => choose({ kind: 'counter', counter })}
                />
              )}
            </FadeInView>

            <FadeInView delay={160} style={styles.inset}>
              <Text style={styles.eyebrow}>Over time</Text>
              <OverTimeCard
                pick={pick}
                bucket={bucket}
                onBucket={chooseBucket}
                series={chart.series}
                error={chart.error}
                selected={selected}
                onSelect={setSelected}
              />
            </FadeInView>
          </>
        )}
      </Scroll>

      <DashboardHeader
        summary={summary}
        stale={stale}
        now={now}
        onLayout={(e) => setHeaderHeight(e.nativeEvent.layout.height)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { gap: space.xl, paddingBottom: 48 },
  inset: { marginHorizontal: space.lg },
  row: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  eyebrow: { ...type.eyebrow, marginBottom: space.sm },
  errorCard: { gap: space.sm },
  retry: { alignSelf: 'flex-start', marginTop: space.xs },
});

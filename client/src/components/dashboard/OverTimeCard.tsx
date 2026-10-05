import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Card } from '@/components/ui';
import { noTextSelect, type PressState } from '@/components/ui/press';
import type { ChartPick, ChartSeries } from '@/lib/dashboard';
import { bucketLabel, counterLabel, currentPeriod, periodSpan } from '@/lib/dashboardFormat';
import type { SeriesBucket } from '@/lib/types';
import { colors, fonts, radius, shadow, space, tabularNums, type } from '@/theme';

import { SkeletonBlock } from './DashboardSkeleton';
import { SeriesChart } from './SeriesChart';

const BUCKETS: { key: SeriesBucket; label: string }[] = [
  { key: 'day', label: 'Day' },
  { key: 'month', label: 'Month' },
  { key: 'year', label: 'Year' },
];

/**
 * The chart card: Day / Month / Year, the current period's value, the bars (tap one to
 * inspect it), the inspected bucket, and the range in one line. No deleted figures anywhere —
 * deletions only move the tiles' totals.
 */
export function OverTimeCard({
  pick,
  bucket,
  onBucket,
  series,
  error,
  selected,
  onSelect,
}: {
  pick: ChartPick;
  bucket: SeriesBucket;
  onBucket: (bucket: SeriesBucket) => void;
  series: ChartSeries | null;
  error: string | null;
  /** The inspected bar; −1 (or out of range) means the latest. */
  selected: number;
  onSelect: (index: number) => void;
}) {
  return (
    <Card style={styles.card}>
      <View style={styles.segmented}>
        {BUCKETS.map((b) => {
          const active = bucket === b.key;
          return (
            <Pressable
              key={b.key}
              onPress={() => onBucket(b.key)}
              accessibilityRole="button"
              accessibilityLabel={b.label}
              accessibilityState={{ selected: active }}
              style={({ pressed }: PressState) => [
                styles.segment,
                active && styles.segmentActive,
                pressed && styles.segmentPressed,
              ]}>
              <Text style={[styles.segmentText, active && styles.segmentTextActive]}>{b.label}</Text>
            </Pressable>
          );
        })}
      </View>

      <Body pick={pick} bucket={bucket} series={series} error={error} selected={selected} onSelect={onSelect} />
    </Card>
  );
}

function Body({
  pick,
  bucket,
  series,
  error,
  selected,
  onSelect,
}: {
  pick: ChartPick;
  bucket: SeriesBucket;
  series: ChartSeries | null;
  error: string | null;
  selected: number;
  onSelect: (index: number) => void;
}) {
  if (series === null) {
    return error !== null ? <Text style={type.caption}>{error}</Text> : <SkeletonBlock height={272} />;
  }
  if (series.points.length === 0) {
    return (
      <Text style={[type.caption, styles.empty]}>
        {pick.kind === 'active'
          ? 'No Traveler has been named on an Event yet — the history starts with the first one.'
          : `Nothing created yet — the history starts with Largata’s first ${counterLabel(pick.counter, 1)} Event.`}
      </Text>
    );
  }

  const words = wording(pick);
  const points = series.points;
  const latest = points[points.length - 1];
  const index = selected >= 0 && selected < points.length ? selected : points.length - 1;
  const chosen = points[index];

  return (
    <>
      <View>
        <Text style={styles.readoutNumber}>{latest.value}</Text>
        <Text style={styles.readoutLabel}>
          {words.readout(latest.value)} {currentPeriod(bucket)}
        </Text>
      </View>

      <SeriesChart
        series={series}
        selected={index}
        onSelect={onSelect}
        describe={(start, value) => `${bucketLabel(bucket, start)}, ${words.detail(value)}`}
      />

      <View style={styles.detail}>
        <Text style={styles.detailLabel}>{bucketLabel(bucket, chosen.start)}</Text>
        <Text style={styles.detailValue}>{words.detail(chosen.value)}</Text>
      </View>

      <Text style={styles.footer}>
        {words.footer(points)} over {periodSpan(bucket, points.length)}
      </Text>
    </>
  );
}

/**
 * The chart's words. The hero's series counts Travelers active; a tile's counts what was
 * created — except the `traveler` counter, whose `.created` is a sign-up, so it says so
 * (design v2). That one name is the only counter worklog words specially; any other counter,
 * including ones Largata adds later, reads as "created".
 */
function wording(pick: ChartPick) {
  if (pick.kind === 'active') {
    return {
      readout: (n: number) => `${n === 1 ? 'traveler' : 'travelers'} active`,
      detail: (n: number) => `${n} active`,
      footer: (points: ChartSeries['points']) => `peak ${Math.max(0, ...points.map((p) => p.value))} active`,
    };
  }
  if (pick.counter === 'traveler') {
    return {
      readout: (n: number) => `traveler ${n === 1 ? 'signup' : 'signups'}`,
      detail: (n: number) => `${n} signed up`,
      footer: (points: ChartSeries['points']) => `${sum(points)} signups`,
    };
  }
  const counter = pick.counter;
  return {
    readout: (n: number) => `${counterLabel(counter, n)} created`,
    detail: (n: number) => `${n} created`,
    footer: (points: ChartSeries['points']) => `${sum(points)} created`,
  };
}

function sum(points: ChartSeries['points']): number {
  return points.reduce((total, p) => total + p.value, 0);
}

const styles = StyleSheet.create({
  card: {
    gap: space.lg,
    paddingVertical: 18,
    paddingHorizontal: space.lg,
    borderRadius: radius.xl - 2,
  },
  segmented: {
    flexDirection: 'row',
    backgroundColor: colors.hairline,
    borderRadius: radius.pill,
    padding: 3,
  },
  segment: {
    flex: 1,
    minHeight: 38,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.pill,
    cursor: 'pointer',
    ...noTextSelect,
  },
  segmentActive: { backgroundColor: colors.surface, ...shadow.segment },
  segmentPressed: { opacity: 0.85 },
  segmentText: { fontFamily: fonts.bold, fontSize: 13, color: colors.textMuted },
  segmentTextActive: { color: colors.text },

  readoutNumber: {
    fontFamily: fonts.extrabold,
    fontSize: 34,
    lineHeight: 40,
    color: colors.brand,
    fontVariant: tabularNums,
  },
  readoutLabel: { ...type.caption, lineHeight: 17 },
  detail: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space.md,
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 14,
    backgroundColor: colors.bg,
  },
  detailLabel: { fontFamily: fonts.bold, fontSize: 12.5, lineHeight: 17, color: colors.text },
  detailValue: { ...type.caption, lineHeight: 17, fontVariant: tabularNums, textAlign: 'right' },
  footer: { ...type.caption, fontSize: 11.5, color: colors.textFaint, fontVariant: tabularNums },
  empty: { paddingVertical: space.lg, textAlign: 'center' },
});

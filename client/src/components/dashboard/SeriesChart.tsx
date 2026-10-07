import { useEffect, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View } from 'react-native';

import type { ChartSeries } from '@/lib/dashboard';
import { bucketLabel } from '@/lib/dashboardFormat';
import { startToEnd } from '@/lib/animation';
import { useReducedMotion } from '@/lib/useReducedMotion';
import { colors, fonts, tabularNums, type } from '@/theme';

const CHART_HEIGHT = 150;
const BAR_MAX = CHART_HEIGHT - 1; // leave the baseline hairline visible under a full bar

/**
 * One bar per bucket, tap to inspect (design v2). The selected bucket — the latest until
 * someone taps another — is the brand red; the rest are the soft wash; an empty bucket keeps a
 * hairline stub so a quiet day reads as a day that happened, not a gap. Dashed guides at the
 * top and the middle give the eye a scale without a y-axis. The bars grow in when a new series
 * arrives (a new pick or bucket), not on every poll.
 */
export function SeriesChart({
  series,
  selected,
  onSelect,
  describe,
}: {
  series: ChartSeries;
  selected: number;
  onSelect: (index: number) => void;
  /** What a bar says to a screen reader, e.g. "Sep 24, 3 created". */
  describe: (start: string, value: number) => string;
}) {
  const { points, bucket, key } = series;
  const max = Math.max(1, ...points.map((p) => p.value));
  const reduced = useReducedMotion();

  // A fresh grow per series, keyed on what the chart shows rather than on the data object,
  // so a 30-second refresh never replays it.
  const [grow] = useState(() => new Animated.Value(reduced ? 1 : 0));
  useEffect(() => {
    if (reduced) {
      grow.setValue(1);
      return;
    }
    grow.setValue(0);
    const anim = Animated.timing(grow, {
      toValue: 1,
      duration: 450,
      easing: Easing.bezier(0.2, 0.7, 0.2, 1),
      useNativeDriver: true,
    });
    return startToEnd(anim, () => grow.setValue(1));
  }, [key, reduced, grow]);

  return (
    <View>
      <View style={styles.chart}>
        <Dashes style={styles.guideTop} />
        <Dashes style={styles.guideMid} />
        <Text style={styles.maxLabel}>{max}</Text>
        <View style={styles.bars}>
          {points.map((p, i) => {
            const isSelected = i === selected;
            const height = p.value === 0 ? 2 : Math.max(4, (p.value / max) * BAR_MAX);
            return (
              <Pressable
                key={p.start}
                onPress={() => onSelect(i)}
                accessibilityRole="button"
                accessibilityLabel={describe(p.start, p.value)}
                accessibilityState={{ selected: isSelected }}
                style={styles.barSlot}>
                <Animated.View
                  style={[
                    styles.bar,
                    { height, transform: [{ scaleY: grow }] },
                    p.value === 0 ? styles.barZero : isSelected ? styles.barSelected : styles.barPast,
                  ]}
                />
              </Pressable>
            );
          })}
        </View>
      </View>

      <View style={styles.axis}>
        <Text style={styles.axisLabel}>{bucketLabel(bucket, points[0].start)}</Text>
        {points.length > 1 && (
          <Text style={styles.axisLabel}>{bucketLabel(bucket, points[points.length - 1].start)}</Text>
        )}
      </View>
    </View>
  );
}

/** A dashed hairline built from segments — React Native's dashed borders are unreliable on
 *  one side of a view, and this reads the same on every platform. */
function Dashes({ style }: { style: object }) {
  return (
    <View style={[styles.dashes, style]} pointerEvents="none">
      {Array.from({ length: 60 }, (_, i) => (
        <View key={i} style={styles.dash} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  chart: { height: CHART_HEIGHT, position: 'relative' },
  dashes: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 1,
    flexDirection: 'row',
    gap: 3,
    overflow: 'hidden',
  },
  dash: { width: 3, height: 1, backgroundColor: colors.hairline },
  guideTop: { top: 0 },
  guideMid: { top: CHART_HEIGHT / 2 },
  maxLabel: {
    position: 'absolute',
    top: -15,
    right: 0,
    fontFamily: fonts.bold,
    fontSize: 10.5,
    color: colors.textFaint,
    fontVariant: tabularNums,
  },
  bars: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 3,
    borderBottomWidth: 1,
    borderBottomColor: colors.hairline,
  },
  barSlot: {
    flex: 1,
    height: '100%',
    alignItems: 'center',
    justifyContent: 'flex-end',
    cursor: 'pointer',
    userSelect: 'none',
  },
  bar: {
    width: '100%',
    maxWidth: 34,
    borderTopLeftRadius: 4,
    borderTopRightRadius: 4,
    transformOrigin: 'bottom',
  },
  barPast: { backgroundColor: colors.salmon },
  barSelected: { backgroundColor: colors.brand },
  barZero: { backgroundColor: colors.hairline },

  axis: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 6 },
  axisLabel: { ...type.caption, fontSize: 11 },
});

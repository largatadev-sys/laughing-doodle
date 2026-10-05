import { useRef } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, Text, View, type ViewStyle } from 'react-native';

import { noTextSelect, type PressState } from '@/components/ui/press';
import { counterLabel } from '@/lib/dashboardFormat';
import type { CounterTotal } from '@/lib/types';
import { colors, fonts, radius, shadow, space, tabularNums } from '@/theme';

export const TILE_WIDTH = 124;
const TILE_GAP = 10;
const INSET = space.lg;

/**
 * One tile per counter, scrolling sideways and snapping to the screen's inset. The tiles are
 * also the chart's picker: tapping one charts that counter and brings the tile fully into view.
 * Counters are Largata's words and arrive in the order its latest Snapshot lists them — never a
 * hard-coded list.
 */
export function TotalsRow({
  totals,
  picked,
  onPick,
}: {
  totals: CounterTotal[];
  /** The counter the chart shows, or null when it shows the hero's series. */
  picked: string | null;
  onPick: (counter: string) => void;
}) {
  const row = useRef<ScrollView>(null);
  const metrics = useRef({ x: 0, width: 0, content: 0 });

  /** Bring a partially hidden tile fully into view, snapping it to the left inset. */
  function reveal(index: number) {
    const { x, width, content } = metrics.current;
    const left = INSET + index * (TILE_WIDTH + TILE_GAP); // tiles are fixed-width
    const right = left + TILE_WIDTH;
    if (left - INSET >= x && right + INSET <= x + width) return;
    const target = Math.max(0, Math.min(left - INSET, content - width));
    row.current?.scrollTo({ x: target, animated: true });
  }

  return (
    <ScrollView
      ref={row}
      horizontal
      showsHorizontalScrollIndicator={false}
      snapToInterval={TILE_WIDTH + TILE_GAP}
      snapToAlignment="start"
      decelerationRate="fast"
      scrollEventThrottle={32}
      onScroll={(e) => {
        metrics.current.x = e.nativeEvent.contentOffset.x;
      }}
      onLayout={(e) => {
        metrics.current.width = e.nativeEvent.layout.width;
      }}
      onContentSizeChange={(w) => {
        metrics.current.content = w;
      }}
      style={webSnapRow}
      contentContainerStyle={styles.row}>
      {totals.map((total, i) => {
        const active = picked === total.counter;
        const label = counterLabel(total.counter, total.value);
        return (
          <Pressable
            key={total.counter}
            onPress={() => {
              onPick(total.counter);
              reveal(i);
            }}
            accessibilityRole="button"
            accessibilityLabel={`${total.value} ${label}${total.baseline === 'none' ? ', since events began' : ''}. Show history`}
            accessibilityState={{ selected: active }}
            style={({ pressed }: PressState) => [
              styles.tile,
              webSnapTile,
              active && styles.tileActive,
              pressed && styles.tilePressed,
            ]}>
            <Text style={[styles.number, active && styles.numberActive]}>{total.value}</Text>
            <Text style={styles.label}>{label}</Text>
          </Pressable>
        );
      })}
      {/* A real spacer, not trailing padding: some renderers drop the latter. */}
      <View style={styles.end} />
    </ScrollView>
  );
}

// Web-only CSS scroll snap: react-native-web ignores snapToInterval, so the browser's own
// snapping stands in, with the inset as the snap edge.
const webSnapRow = (Platform.OS === 'web'
  ? { scrollSnapType: 'x mandatory', scrollPaddingLeft: INSET, overscrollBehaviorX: 'contain' }
  : {}) as ViewStyle;
const webSnapTile = (Platform.OS === 'web' ? { scrollSnapAlign: 'start' } : {}) as ViewStyle;

const styles = StyleSheet.create({
  row: { gap: TILE_GAP, paddingLeft: INSET, paddingTop: 2, paddingBottom: space.sm },
  end: { width: 6 },
  tile: {
    width: TILE_WIDTH,
    gap: 1,
    padding: 14,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface,
    cursor: 'pointer',
    ...shadow.card,
    ...noTextSelect,
  },
  tileActive: { backgroundColor: colors.brandSoft, borderColor: colors.brand },
  tilePressed: { transform: [{ scale: 0.97 }] },
  number: {
    fontFamily: fonts.extrabold,
    fontSize: 26,
    lineHeight: 32,
    color: colors.text,
    fontVariant: tabularNums,
  },
  numberActive: { color: colors.brand },
  label: { fontFamily: fonts.semibold, fontSize: 13.5, lineHeight: 18, color: colors.text },
});

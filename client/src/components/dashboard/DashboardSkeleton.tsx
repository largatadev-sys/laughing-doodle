import { StyleSheet, View } from 'react-native';

import { colors, radius, space } from '@/theme';

import { TILE_WIDTH } from './TotalsRow';

/** First-load placeholder in the shape of the screen: hero, a row of tiles, the chart card. */
export function DashboardSkeleton() {
  return (
    <View style={styles.skeleton} accessibilityLabel="Loading usage">
      <View style={[styles.block, styles.hero]} />
      <View style={styles.tiles}>
        {[0, 1, 2].map((i) => (
          <View key={i} style={[styles.block, styles.tile]} />
        ))}
      </View>
      <View style={[styles.block, styles.card]} />
    </View>
  );
}

/** A grey block standing in for content while it loads. */
export function SkeletonBlock({ height }: { height: number }) {
  return <View style={[styles.block, { height }]} />;
}

const styles = StyleSheet.create({
  skeleton: { gap: space.xl, marginHorizontal: space.lg },
  block: { borderRadius: radius.lg, backgroundColor: colors.hairline },
  hero: { height: 118, borderRadius: radius.xl - 2 },
  tiles: { flexDirection: 'row', gap: 10 },
  tile: { width: TILE_WIDTH, height: 82 },
  card: { height: 360, borderRadius: radius.xl - 2 },
});

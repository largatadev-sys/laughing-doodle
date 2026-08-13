import { StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { Card, PillButton } from '@/components/ui';
import { colors, radius, space, type } from '@/theme';

/**
 * First-fetch placeholder. A skeleton rather than a spinner: it's calmer, and it shows the
 * shape of what's coming instead of a shrug. Only ever shown when there is no data at all —
 * a refetch over an existing list stays silent.
 */
export function ReportListSkeleton() {
  return (
    <Card flush style={styles.skeletonCard}>
      {[0, 1, 2, 3].map((i) => (
        <View key={i} style={[styles.skeletonRow, i > 0 && styles.skeletonDivider]}>
          <View style={styles.skeletonEdge} />
          <View style={styles.skeletonGlyph} />
          <View style={styles.skeletonBody}>
            {/* Varying widths so it reads as content, not as a loading bar. */}
            <View style={[styles.skeletonLine, { width: `${88 - i * 9}%` }]} />
            <View style={[styles.skeletonLine, styles.skeletonLineShort, { width: `${52 - i * 4}%` }]} />
          </View>
          <View style={styles.skeletonPill} />
        </View>
      ))}
    </Card>
  );
}

/** Connection failure. Keeps any cached list visible beneath it, and never blames the reader. */
export function ReportListError({ message, onRetry }: { message?: string; onRetry: () => void }) {
  return (
    <Card style={styles.errorCard}>
      <View style={styles.errorTop}>
        <Feather name="wifi-off" size={18} color={colors.brand} />
        <Text style={styles.errorTitle}>Couldn’t reach the inbox</Text>
      </View>
      <Text style={styles.errorBody}>
        {message ?? 'Your reports are safe — this is just a connection problem.'}
      </Text>
      <PillButton label="Try again" variant="outline" onPress={onRetry} style={styles.retry} />
    </Card>
  );
}

export function ReportListEmpty({ title, body }: { title: string; body: string }) {
  return (
    <View style={styles.empty}>
      <Feather name="inbox" size={30} color={colors.textFaint} />
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.emptyBody}>{body}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  skeletonCard: { marginTop: space.xs },
  skeletonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    minHeight: 62,
    paddingHorizontal: space.md,
    paddingVertical: space.sm + 2,
  },
  skeletonDivider: { borderTopWidth: 1, borderTopColor: colors.hairline },
  skeletonEdge: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: 3,
    backgroundColor: colors.hairline,
  },
  skeletonGlyph: { width: 18, height: 18, borderRadius: 9, backgroundColor: colors.hairline },
  skeletonBody: { flex: 1, gap: 6 },
  skeletonLine: { height: 10, borderRadius: radius.sm, backgroundColor: colors.hairline },
  skeletonLineShort: { height: 8, opacity: 0.7 },
  skeletonPill: { width: 52, height: 16, borderRadius: radius.pill, backgroundColor: colors.hairline },

  errorCard: { gap: space.sm, marginTop: space.xs },
  errorTop: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  errorTitle: { ...type.heading },
  errorBody: { ...type.body, color: colors.textMuted },
  retry: { alignSelf: 'flex-start', marginTop: space.xs },

  empty: { alignItems: 'center', gap: space.sm, paddingVertical: space.xxl },
  emptyTitle: { ...type.heading },
  emptyBody: {
    ...type.body,
    color: colors.textMuted,
    textAlign: 'center',
    paddingHorizontal: space.xl,
  },
});

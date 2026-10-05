import { StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { clockTimeOrDate, relativeWhen } from '@/lib/datetime';
import type { DashboardSummary } from '@/lib/types';
import { colors, fonts, radius, space, type } from '@/theme';

/** Largata has gone quiet: the first thing on the screen when its hourly Snapshot stops. */
export function SilentBanner({ summary, now }: { summary: DashboardSummary; now: Date }) {
  return (
    <View style={styles.banner} accessibilityRole="alert">
      <Feather name="alert-triangle" size={18} color={colors.brand} style={styles.icon} />
      <View style={styles.body}>
        <Text style={styles.title}>
          {summary.silentSince
            ? `Largata silent since ${relativeWhen(summary.silentSince, now)}`
            : 'Largata silent — no Snapshot received yet'}
        </Text>
        <Text style={type.caption}>
          {summary.silentSince
            ? `Its hourly Snapshot stopped at ${clockTimeOrDate(summary.silentSince, now)}. Numbers as of ${clockTimeOrDate(summary.asOf, now)}.`
            : `Totals count only the Events worklog has seen. Numbers as of ${clockTimeOrDate(summary.asOf, now)}.`}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.md,
    paddingVertical: 14,
    paddingHorizontal: space.lg,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.brand,
    backgroundColor: colors.brandSoft,
  },
  icon: { marginTop: 2 },
  body: { flex: 1, gap: 2 },
  title: { fontFamily: fonts.bold, fontSize: 15, lineHeight: 20, color: colors.brandDeep },
});

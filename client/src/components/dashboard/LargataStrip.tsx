import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { noTextSelect, type PressState } from '@/components/ui/press';
import { counterLabel } from '@/lib/dashboardFormat';
import { relativeWhen } from '@/lib/datetime';
import type { DashboardSummary } from '@/lib/types';
import { colors, fonts, radius, space, tabularNums, type } from '@/theme';

/**
 * The pulse of Largata on the Reports tab: Travelers active today and the first two totals,
 * or — when the Snapshots have stopped — "Largata silent since …" in the warning treatment,
 * because a dead backend should be the first thing anyone sees, not something they infer
 * from flat numbers. The whole strip is one control: tapping it opens the Dashboard.
 */
export function LargataStrip({
  summary,
  failed,
  now,
  onPress,
}: {
  summary: DashboardSummary | null;
  /** The first load failed and there is nothing to show yet. */
  failed: boolean;
  now: Date;
  onPress: () => void;
}) {
  const silent = summary?.silent ?? false;

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibleLabel(summary, failed)}
      accessibilityHint="Opens the Largata usage dashboard"
      style={({ pressed, hovered }: PressState) => [
        styles.strip,
        silent && styles.stripSilent,
        hovered && !silent && styles.stripHover,
        pressed && styles.stripPressed,
      ]}>
      <View style={[styles.glyph, silent && styles.glyphSilent]}>
        <Feather
          name={silent ? 'alert-triangle' : 'activity'}
          size={16}
          color={silent ? colors.onBrand : colors.brand}
        />
      </View>

      <View style={styles.body}>
        <View style={styles.topLine}>
          <Text style={styles.eyebrow}>Largata</Text>
          {summary && (
            <Text style={styles.updated} numberOfLines={1}>
              updated {relativeWhen(summary.asOf, now)}
            </Text>
          )}
        </View>
        <Text style={[styles.line, silent && styles.lineSilent]} numberOfLines={2}>
          {headline(summary, failed, now)}
        </Text>
      </View>

      <Feather name="chevron-right" size={18} color={silent ? colors.brand : colors.textFaint} />
    </Pressable>
  );
}

function headline(summary: DashboardSummary | null, failed: boolean, now: Date): string {
  if (!summary) return failed ? 'Usage numbers unavailable right now' : 'Loading usage…';
  if (summary.silent) {
    return summary.silentSince
      ? `Silent since ${relativeWhen(summary.silentSince, now)}`
      : 'Silent — no Snapshot received yet';
  }
  const parts = [`${summary.activeToday} active today`];
  for (const total of summary.totals.slice(0, 2)) {
    parts.push(`${total.value} ${counterLabel(total.counter, total.value)}`);
  }
  return parts.join(' · ');
}

function accessibleLabel(summary: DashboardSummary | null, failed: boolean): string {
  return `Largata usage. ${headline(summary, failed, new Date())}`;
}

const styles = StyleSheet.create({
  strip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    minHeight: 56,
    paddingVertical: space.sm + 2,
    paddingHorizontal: space.md,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface,
    cursor: 'pointer',
    ...noTextSelect,
  },
  // The warning treatment: the brand's own alarm colour, washed, with a solid glyph.
  stripSilent: { backgroundColor: colors.brandSoft, borderColor: colors.brand },
  stripHover: { backgroundColor: colors.bg },
  stripPressed: { transform: [{ scale: 0.99 }], opacity: 0.9 },

  glyph: {
    width: 32,
    height: 32,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.brandSoft,
  },
  glyphSilent: { backgroundColor: colors.brand },

  body: { flex: 1, gap: 2 },
  topLine: { flexDirection: 'row', alignItems: 'baseline', gap: space.sm },
  eyebrow: { ...type.eyebrow },
  updated: { ...type.caption, fontSize: 11.5, flexShrink: 1 },
  line: {
    fontFamily: fonts.bold,
    fontSize: 13.5,
    lineHeight: 18,
    color: colors.text,
    fontVariant: tabularNums,
  },
  lineSilent: { color: colors.brandDeep },
});

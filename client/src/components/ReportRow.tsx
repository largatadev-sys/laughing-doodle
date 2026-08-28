import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import type { PressState } from '@/components/ui/press';
import { activityLabel } from '@/lib/datetime';
import { STATUS_EDGE, STATUS_LABELS, STATUS_TONES, TYPE_ICONS } from '@/lib/reportStatus';
import type { ReportResponse } from '@/lib/types';
import { colors, fonts, radius, space, type } from '@/theme';

interface ReportRowProps {
  report: ReportResponse;
  onPress: (report: ReportResponse) => void;
  /** Opens the full status sheet — the list's triage path, on long-press. */
  onTriage: (report: ReportResponse) => void;
}

/**
 * One report as a dense triage row: a single ellipsized line of the reporter's words above a
 * metadata line, with status carried by a coloured left edge rather than a card border.
 *
 * Tap opens the report; press-and-hold opens the status sheet. Unlike 1b's swipe (removed —
 * it never worked with a mouse), long-press works on both pointer types, so the one control
 * covers phone and desktop. It is invisible by nature, which is why the list carries a hint
 * line spelling it out.
 */
export function ReportRow({ report, onPress, onTriage }: ReportRowProps) {
  const when = activityLabel(report.submittedAt, report.submittedAt).when;
  const tone = STATUS_TONES[report.status];
  const shots = report.screenshotOrdinals.length;
  // A signed-out reporter is shown honestly, not hidden — auth screens are exactly where
  // "I can't get in" reports come from (contract v1.1).
  const reporter = report.reporterName ?? 'Signed out';

  return (
    <View style={styles.rowWrap}>
      <Pressable
        onPress={() => onPress(report)}
        onLongPress={() => onTriage(report)}
        delayLongPress={400}
        accessibilityRole="button"
        accessibilityLabel={[
          report.type === 'problem' ? 'Problem' : 'Idea',
          `from ${reporter},`,
          STATUS_LABELS[report.status],
          shots > 0 ? `, ${shots} screenshot${shots > 1 ? 's' : ''}` : '',
        ].join(' ')}
        // Screen readers can't press-and-hold, so the triage path is exposed as a named action
        // rather than left as a gesture they have no way to perform.
        accessibilityActions={[{ name: 'longpress', label: 'Move to another status' }]}
        onAccessibilityAction={(e) => {
          if (e.nativeEvent.actionName === 'longpress') onTriage(report);
        }}
        style={({ pressed, hovered }: PressState) => [
          styles.row,
          hovered && styles.rowHovered,
          pressed && styles.rowPressed,
        ]}>
        <View style={[styles.edge, { backgroundColor: STATUS_EDGE[report.status] }]} />

        <Feather
          name={TYPE_ICONS[report.type]}
          size={16}
          color={report.status === 'new' ? colors.brand : colors.textMuted}
          style={styles.glyph}
        />

        <View style={styles.body}>
          {/* One line only, ellipsized — the reporter's words are never rewritten, just cut. */}
          <Text style={styles.snippet} numberOfLines={1}>
            {report.description}
          </Text>
          <Text style={styles.meta} numberOfLines={1}>
            {reporter} · {platformShort(report)} · {when}
            {shots > 0 ? ` · ${shots} shot${shots > 1 ? 's' : ''}` : ''}
          </Text>
        </View>

        <View style={[styles.pill, { backgroundColor: tone.bg }]}>
          <Text style={[styles.pillText, { color: tone.fg }]}>{STATUS_LABELS[report.status]}</Text>
        </View>
      </Pressable>
    </View>
  );
}

// "iOS 1.4.2" — tighter than a card's "iOS · 1.4.2" because the dense row's metadata line
// already carries three separators.
function platformShort(report: ReportResponse): string {
  const platform = report.platform === 'ios' ? 'iOS' : report.platform === 'web' ? 'Web' : 'Android';
  return `${platform} ${report.appVersion}`;
}

const styles = StyleSheet.create({
  rowWrap: { position: 'relative' },

  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    // 44px floor is met with room to spare: two text lines plus padding.
    minHeight: 62,
    paddingHorizontal: space.md,
    paddingVertical: space.sm + 2,
    backgroundColor: colors.surface,
    cursor: 'pointer',
  },
  rowHovered: { backgroundColor: colors.brandSoft },
  rowPressed: { backgroundColor: colors.brandSoft },

  edge: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 3 },
  glyph: { width: 18 },

  body: { flex: 1, gap: 1 },
  snippet: { ...type.bodyMedium, fontSize: 14 },
  meta: { ...type.caption, fontSize: 11.5 },

  pill: {
    paddingHorizontal: space.sm,
    paddingVertical: 3,
    borderRadius: radius.pill,
  },
  pillText: { fontFamily: fonts.bold, fontSize: 10.5, letterSpacing: 0.2 },
});

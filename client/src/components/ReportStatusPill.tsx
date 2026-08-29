import { StyleSheet, Text, View } from 'react-native';

import { STATUS_LABELS, STATUS_TONES } from '@/lib/reportStatus';
import type { ReportStatus } from '@/lib/types';
import { fonts, radius, space } from '@/theme';

/**
 * A report's status as a chip. Lifted out of the list row so the row and the detail screen's
 * top line render the identical pill — where a report stands should look the same wherever you
 * meet it, and one component is what guarantees that rather than two sets of matching numbers.
 */
export function ReportStatusPill({ status }: { status: ReportStatus }) {
  const tone = STATUS_TONES[status];
  return (
    <View style={[styles.pill, { backgroundColor: tone.bg }]}>
      <Text style={[styles.pillText, { color: tone.fg }]}>{STATUS_LABELS[status]}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    paddingHorizontal: space.sm,
    paddingVertical: 3,
    borderRadius: radius.pill,
    alignSelf: 'flex-start',
  },
  pillText: { fontFamily: fonts.bold, fontSize: 10.5, letterSpacing: 0.2 },
});

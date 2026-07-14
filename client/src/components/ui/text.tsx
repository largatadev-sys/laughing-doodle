import { StyleSheet, Text, View, type TextStyle } from 'react-native';

import { formatDuration } from '@/lib/datetime';
import { colors, fonts, radius, space, type } from '@/theme';

// Red small-caps category label — Largata's eyebrow ("Accommodation" / "Restaurant"). Here it
// tags an entry's day-of-week or a section, because a timesheet has no product category.
export function Eyebrow({ children, color, style }: { children: React.ReactNode; color?: string; style?: TextStyle }) {
  return <Text style={[type.eyebrow, color ? { color } : null, style]}>{children}</Text>;
}

// A duration as the "readout": tabular figures, bold. The timesheet's core material.
export function Duration({ minutes, style }: { minutes: number; style?: TextStyle }) {
  return <Text style={[type.number, style]}>{formatDuration(minutes)}</Text>;
}

type PillTone = 'salmon' | 'brand' | 'neutral';

// Rounded status chip, echoing Largata's "booked!" badge — here "you", "logged", "edited".
export function StatusPill({ label, tone = 'salmon' }: { label: string; tone?: PillTone }) {
  const toneStyle = pillTones[tone];
  return (
    <View style={[styles.pill, { backgroundColor: toneStyle.bg }]}>
      <Text style={[styles.pillText, { color: toneStyle.fg }]}>{label}</Text>
    </View>
  );
}

const pillTones: Record<PillTone, { bg: string; fg: string }> = {
  salmon: { bg: colors.brandSoft, fg: colors.brandDeep },
  brand: { bg: colors.brand, fg: colors.onBrand },
  neutral: { bg: colors.hairline, fg: colors.textMuted },
};

const styles = StyleSheet.create({
  pill: {
    paddingHorizontal: space.sm + 2,
    paddingVertical: 3,
    borderRadius: radius.pill,
    alignSelf: 'flex-start',
  },
  pillText: { fontFamily: fonts.bold, fontSize: 11.5, letterSpacing: 0.2 },
});

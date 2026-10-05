import { Pressable, StyleSheet, Text, View } from 'react-native';

import { noTextSelect, type PressState } from '@/components/ui/press';
import { colors, fonts, radius, shadow, tabularNums, type } from '@/theme';

/**
 * Travelers active today, as the screen's hero. Tapping it charts the active history below —
 * a different series from the travelers tile, which charts sign-ups.
 */
export function HeroCard({
  activeToday,
  selected,
  onPress,
}: {
  activeToday: number;
  /** The chart below currently shows this card's series. */
  selected: boolean;
  onPress: () => void;
}) {
  const noun = activeToday === 1 ? 'traveler' : 'travelers';
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${activeToday} ${noun} active today. Show active history`}
      accessibilityState={{ selected }}
      style={({ pressed }: PressState) => [styles.hero, pressed && styles.pressed]}>
      <View style={[styles.circle, styles.circleLarge]} />
      <View style={[styles.circle, styles.circleSmall]} />
      <Text style={styles.eyebrow}>Today</Text>
      <View style={styles.row}>
        <Text style={styles.number}>{activeToday}</Text>
        <Text style={styles.label}>{noun} active</Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  hero: {
    position: 'relative',
    overflow: 'hidden',
    gap: 2,
    padding: 20,
    borderRadius: radius.xl - 2,
    backgroundColor: colors.brand,
    cursor: 'pointer',
    ...shadow.hero,
    ...noTextSelect,
  },
  pressed: { transform: [{ scale: 0.98 }] },
  circle: { position: 'absolute', borderRadius: radius.pill, backgroundColor: colors.onBrandDecor },
  circleLarge: { width: 140, height: 140, right: -32, top: -32 },
  circleSmall: { width: 64, height: 64, right: 8, top: 36 },
  eyebrow: { ...type.eyebrow, color: colors.onBrandMuted },
  row: { flexDirection: 'row', alignItems: 'baseline', gap: 10 },
  number: {
    fontFamily: fonts.extrabold,
    fontSize: 52,
    lineHeight: 58,
    color: colors.onBrand,
    fontVariant: tabularNums,
  },
  label: { fontFamily: fonts.bold, fontSize: 17, lineHeight: 22, color: colors.onBrand },
});

import { ActivityIndicator, Pressable, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { colors, fonts, radius, space } from '@/theme';
import type { PressState } from './press';

type Variant = 'primary' | 'outline' | 'soft';

interface PillButtonProps {
  label: string;
  onPress: () => void;
  variant?: Variant;
  icon?: keyof typeof Feather.glyphMap;
  loading?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
}

// Every call-to-action in the app. Fully-round, red-forward — Largata's button language.
export function PillButton({
  label,
  onPress,
  variant = 'primary',
  icon,
  loading = false,
  disabled = false,
  style,
}: PillButtonProps) {
  const v = variants[variant];
  const isDisabled = disabled || loading;
  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      style={({ pressed, hovered }: PressState) => [
        styles.base,
        { backgroundColor: v.bg, borderColor: v.border },
        hovered && !isDisabled && { backgroundColor: v.pressed, borderColor: v.pressed },
        pressed && !isDisabled && { transform: [{ scale: 0.97 }] },
        isDisabled && styles.disabled,
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={v.fg} />
      ) : (
        <View style={styles.content}>
          {icon && <Feather name={icon} size={18} color={v.fg} />}
          <Text style={[styles.label, { color: v.fg }]}>{label}</Text>
        </View>
      )}
    </Pressable>
  );
}

const variants: Record<Variant, { bg: string; fg: string; border: string; pressed: string }> = {
  primary: { bg: colors.brand, fg: colors.onBrand, border: colors.brand, pressed: colors.brandDeep },
  outline: { bg: colors.surface, fg: colors.brand, border: colors.brand, pressed: colors.brandSoft },
  soft: { bg: colors.brandSoft, fg: colors.brandDeep, border: colors.brandSoft, pressed: '#F8D2D4' },
};

const styles = StyleSheet.create({
  base: {
    minHeight: 52,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space.xl,
  },
  content: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  label: { fontFamily: fonts.bold, fontSize: 16 },
  disabled: { opacity: 0.5 },
});

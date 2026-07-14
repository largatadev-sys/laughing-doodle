import {
  PlusJakartaSans_400Regular,
  PlusJakartaSans_500Medium,
  PlusJakartaSans_600SemiBold,
  PlusJakartaSans_700Bold,
  PlusJakartaSans_800ExtraBold,
} from '@expo-google-fonts/plus-jakarta-sans';

import type { TextStyle } from 'react-native';

import { colors } from './colors';

// Tabular figures, correctly typed once so screens can drop it into StyleSheet.create
// without fighting React Native's fontVariant array type.
export const tabularNums = ['tabular-nums'] as TextStyle['fontVariant'];
const TABULAR = tabularNums;

// Plus Jakarta Sans — a friendly geometric sans that matches Largata's bold, rounded
// wordmark. In React Native a custom font's weight comes from the *family name*, not
// fontWeight, so each weight is its own family string.
export const fonts = {
  regular: 'PlusJakartaSans_400Regular',
  medium: 'PlusJakartaSans_500Medium',
  semibold: 'PlusJakartaSans_600SemiBold',
  bold: 'PlusJakartaSans_700Bold',
  extrabold: 'PlusJakartaSans_800ExtraBold',
} as const;

// The map handed to useFonts() at the root. Keep in sync with `fonts` above.
export function fontAssets() {
  return {
    [fonts.regular]: PlusJakartaSans_400Regular,
    [fonts.medium]: PlusJakartaSans_500Medium,
    [fonts.semibold]: PlusJakartaSans_600SemiBold,
    [fonts.bold]: PlusJakartaSans_700Bold,
    [fonts.extrabold]: PlusJakartaSans_800ExtraBold,
  };
}

// Type scale as ready-to-spread style objects. Numbers use `tabular-nums` so durations
// and totals align in columns (the timesheet's own material deserves the care).
export const type = {
  // Screen / hero titles
  display: { fontFamily: fonts.extrabold, fontSize: 26, lineHeight: 32, color: colors.text },
  title: { fontFamily: fonts.bold, fontSize: 20, lineHeight: 26, color: colors.text },
  // Card headings, names
  heading: { fontFamily: fonts.bold, fontSize: 16, lineHeight: 22, color: colors.text },
  // Default reading text
  body: { fontFamily: fonts.regular, fontSize: 15, lineHeight: 22, color: colors.text },
  bodyMedium: { fontFamily: fonts.medium, fontSize: 15, lineHeight: 22, color: colors.text },
  // Secondary / captions
  caption: { fontFamily: fonts.medium, fontSize: 12.5, lineHeight: 16, color: colors.textMuted },
  // Red small-caps eyebrow (Largata's category labels)
  eyebrow: {
    fontFamily: fonts.bold,
    fontSize: 11,
    lineHeight: 14,
    letterSpacing: 0.9,
    textTransform: 'uppercase' as const,
    color: colors.brand,
  },
  // Numbers — durations, totals. Bold + tabular figures = the "readout" feel.
  number: {
    fontFamily: fonts.bold,
    fontSize: 15,
    fontVariant: TABULAR,
    color: colors.text,
  },
  numberLarge: {
    fontFamily: fonts.extrabold,
    fontSize: 22,
    fontVariant: TABULAR,
    color: colors.text,
  },
} satisfies Record<string, TextStyle>;

import { StyleSheet, View, type ViewProps } from 'react-native';

import { colors, radius, shadow, space } from '@/theme';

interface CardProps extends ViewProps {
  accent?: boolean; // full red outline for emphasis (e.g. today / selected)
  flush?: boolean; // no inner padding, for cards that manage their own
}

// The Largata surface: white, generously rounded, soft red-tinted outline, one shared shadow.
export function Card({ accent, flush, style, children, ...rest }: CardProps) {
  return (
    <View
      style={[styles.card, flush && styles.flush, accent && styles.accent, style]}
      {...rest}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    padding: space.lg,
    ...shadow.card,
  },
  flush: { padding: 0, overflow: 'hidden' },
  accent: { borderColor: colors.brand, borderWidth: 1.5 },
});

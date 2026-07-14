import { forwardRef } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { TabTriggerSlotProps } from 'expo-router/ui';

import { colors, radius, shadow } from '@/theme';

type TabButtonProps = TabTriggerSlotProps & {
  icon: keyof typeof Feather.glyphMap;
  a11y: string;
};

// One tab in the floating red pill. Icon-only (like Largata's bar); the active tab gets a
// soft translucent halo behind its glyph. `asChild` on TabTrigger forwards press + isFocused.
export const TabBarButton = forwardRef<View, TabButtonProps>(function TabBarButton(
  { icon, a11y, isFocused, ...props },
  ref,
) {
  return (
    <Pressable
      ref={ref}
      {...props}
      accessibilityRole="tab"
      accessibilityLabel={a11y}
      accessibilityState={{ selected: !!isFocused }}
      style={styles.tabItem}>
      <View style={[styles.iconHalo, isFocused && styles.iconHaloActive]}>
        <Feather name={icon} size={22} color={colors.onBrand} />
      </View>
    </Pressable>
  );
});

// The compose action — NOT a tab; it pushes the modal entry form. A white disc on the red
// bar makes "log time" the anchor of the whole navigation.
export function ComposeButton({ onPress }: { onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="Log time"
      hitSlop={6}
      style={({ pressed }) => [styles.compose, pressed && { transform: [{ scale: 0.92 }] }]}>
      <Feather name="plus" size={26} color={colors.brand} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tabItem: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconHalo: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconHaloActive: {
    backgroundColor: 'rgba(255,255,255,0.22)',
  },
  compose: {
    width: 52,
    height: 52,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow.card,
  },
});

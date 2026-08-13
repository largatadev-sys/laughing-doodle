import { forwardRef, useEffect, useState } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { TabTriggerSlotProps } from 'expo-router/ui';

import { useReducedMotion } from '@/lib/useReducedMotion';
import { colors, fonts, radius, shadow, tabularNums } from '@/theme';

type TabButtonProps = TabTriggerSlotProps & {
  icon: keyof typeof Feather.glyphMap;
  a11y: string;
  /** Count shown in a corner badge; hidden at zero. */
  badge?: number;
};

// One tab in the floating red pill. Icon-only (like Largata's bar); the active tab gets a
// soft translucent halo behind its glyph. `asChild` on TabTrigger forwards press + isFocused.
export const TabBarButton = forwardRef<View, TabButtonProps>(function TabBarButton(
  { icon, a11y, badge = 0, isFocused, ...props },
  ref,
) {
  const showBadge = badge > 0;
  return (
    <Pressable
      ref={ref}
      {...props}
      accessibilityRole="tab"
      accessibilityLabel={showBadge ? `${a11y}, ${badge} new` : a11y}
      accessibilityState={{ selected: !!isFocused }}
      style={styles.tabItem}>
      <View style={[styles.iconHalo, isFocused && styles.iconHaloActive]}>
        <Feather name={icon} size={22} color={colors.onBrand} />
      </View>
      {showBadge && <Badge count={badge} />}
    </Pressable>
  );
});

// White-on-red is already the pill's palette, so the badge inverts it — a white disc with red
// digits reads as "something is waiting" without adding a third colour. It pops in with a
// fade+scale so a report arriving while you're on another tab catches the eye; under reduced
// motion it simply fades.
function Badge({ count }: { count: number }) {
  const reduced = useReducedMotion();
  const [progress] = useState(() => new Animated.Value(0));

  useEffect(() => {
    const anim = Animated.spring(progress, {
      toValue: 1,
      useNativeDriver: true,
      speed: 14,
      bounciness: 10,
    });
    if (reduced) {
      // Fade only: no overshoot, nothing that reads as motion.
      Animated.timing(progress, { toValue: 1, duration: 160, useNativeDriver: true }).start();
    } else {
      anim.start();
    }
    return () => anim.stop();
  }, [reduced, progress]);

  return (
    <Animated.View
      style={[
        styles.badge,
        { opacity: progress, transform: reduced ? [] : [{ scale: progress }] },
      ]}
      pointerEvents="none">
      <Text style={styles.badgeText} numberOfLines={1}>
        {count > 99 ? '99+' : count}
      </Text>
    </Animated.View>
  );
}

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
  badge: {
    position: 'absolute',
    top: 2,
    right: 1,
    minWidth: 18,
    height: 18,
    paddingHorizontal: 4,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: {
    fontFamily: fonts.extrabold,
    fontSize: 10.5,
    lineHeight: 13,
    color: colors.brand,
    fontVariant: tabularNums,
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

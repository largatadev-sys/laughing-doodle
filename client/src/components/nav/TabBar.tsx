import { forwardRef, useEffect, useRef, useState } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { TabTriggerSlotProps } from 'expo-router/ui';

import { CenteredIcon } from '@/components/nav/CenteredIcon';
import type { TabBarCenter } from '@/components/nav/TabBarAction';
import { startToEnd } from '@/lib/animation';
import { useReducedMotion } from '@/lib/useReducedMotion';
import { colors, fonts, radius, shadow, tabularNums } from '@/theme';

// Where keyboard focus lands when the pill hands navigation back (see MorphingPill), so a
// keyboard user ends up on the disc they started from.
export const CENTER_DISC_ID = 'tab-center-disc';

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
    const anim = reduced
      ? // Fade only: no overshoot, nothing that reads as motion.
        Animated.timing(progress, { toValue: 1, duration: 160, useNativeDriver: true })
      : Animated.spring(progress, { toValue: 1, useNativeDriver: true, speed: 14, bounciness: 10 });
    return startToEnd(anim, () => progress.setValue(1));
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

// The centre disc — NOT a tab. By default it is the compose action ("log time", pushing the
// modal entry form): a white disc on the red bar makes it the anchor of the navigation. A tab
// can lend it another action (TabBarCenter), e.g. Reports' "Hand off"; the glyphs trade places
// with a quarter turn — the old one spins out and shrinks as the new one spins in and grows —
// and the disc gives a small pop, so the change of job is noticed. Reduced motion: a crossfade.
export const CENTER_DISC_SIZE = 52;

export function CenterButton({
  override,
  onCompose,
}: {
  override: TabBarCenter | null;
  onCompose: () => void;
}) {
  const reduced = useReducedMotion();
  const swapped = override !== null;
  const [swap] = useState(() => new Animated.Value(swapped ? 1 : 0));
  const [pop] = useState(() => new Animated.Value(1));
  // The override's glyph stays rendered while it spins back out after the override is gone.
  const [overrideIcon, setOverrideIcon] = useState(override?.icon ?? 'send');
  if (override && override.icon !== overrideIcon) setOverrideIcon(override.icon);
  const settled = useRef(swapped);

  useEffect(() => {
    const toValue = swapped ? 1 : 0;
    if (settled.current === swapped) {
      // First mount (no entrance), or a re-run for another reason mid-animation: land cleanly.
      swap.setValue(toValue);
      pop.setValue(1);
      return;
    }
    settled.current = swapped;
    if (reduced) {
      const fade = Animated.timing(swap, { toValue, duration: 140, useNativeDriver: true });
      return startToEnd(fade, () => swap.setValue(toValue));
    }
    pop.setValue(0.86);
    const anim = Animated.parallel([
      Animated.spring(swap, { toValue, useNativeDriver: true, speed: 14, bounciness: 4 }),
      Animated.spring(pop, { toValue: 1, useNativeDriver: true, speed: 12, bounciness: 14 }),
    ]);
    return startToEnd(anim, () => {
      swap.setValue(toValue);
      pop.setValue(1);
    });
  }, [swapped, reduced, swap, pop]);

  const clamp = { extrapolate: 'clamp' as const };
  const turn = (from: string, to: string) =>
    reduced ? [] : [{ rotate: swap.interpolate({ inputRange: [0, 1], outputRange: [from, to] }) }];
  const plusStyle = {
    opacity: swap.interpolate({ inputRange: [0, 0.5], outputRange: [1, 0], ...clamp }),
    transform: [
      ...turn('0deg', '90deg'),
      { scale: swap.interpolate({ inputRange: [0, 1], outputRange: [1, reduced ? 1 : 0.4], ...clamp }) },
    ],
  };
  const overrideStyle = {
    opacity: swap.interpolate({ inputRange: [0.5, 1], outputRange: [0, 1], ...clamp }),
    transform: [
      ...turn('-90deg', '0deg'),
      { scale: swap.interpolate({ inputRange: [0, 1], outputRange: [reduced ? 1 : 0.4, 1], ...clamp }) },
    ],
  };

  return (
    <Animated.View style={{ transform: [{ scale: pop }] }}>
      <Pressable
        onPress={override ? override.onPress : onCompose}
        nativeID={CENTER_DISC_ID}
        accessibilityRole="button"
        accessibilityLabel={override ? override.label : 'Log time'}
        hitSlop={6}
        style={({ pressed }) => [styles.compose, pressed && { transform: [{ scale: 0.92 }] }]}>
        <Animated.View style={[styles.glyph, plusStyle]} pointerEvents="none">
          <CenteredIcon name="plus" size={26} color={colors.brand} />
        </Animated.View>
        <Animated.View style={[styles.glyph, overrideStyle]} pointerEvents="none">
          <CenteredIcon name={overrideIcon} size={22} color={colors.brand} />
        </Animated.View>
      </Pressable>
    </Animated.View>
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
    width: CENTER_DISC_SIZE,
    height: CENTER_DISC_SIZE,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow.card,
  },
  glyph: { ...StyleSheet.absoluteFill, alignItems: 'center', justifyContent: 'center' },
});

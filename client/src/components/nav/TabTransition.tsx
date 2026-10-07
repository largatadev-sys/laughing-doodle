import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useState } from 'react';
import { Animated, Easing, StyleSheet } from 'react-native';
import { Screen } from 'react-native-screens';
import { TabSlot, type TabSlotProps } from 'expo-router/ui';

import { startToEnd } from '@/lib/animation';
import { useReducedMotion } from '@/lib/useReducedMotion';

// The headless `expo-router/ui` <TabSlot /> swaps the focused screen with a hard cut — the
// trade-off for the fully-custom red pill bar. This slot gives that swap the app's motion
// language: the incoming screen settles in with a quick fade and a hair of sideways drift (tabs
// are lateral siblings, so the axis encodes the relationship; FadeInView's rise means
// "arriving"). Skipped under reduced motion.
//
// Two rules keep it smooth and identical on every tap:
// 1. The animation is attached to each screen and starts from *that screen's own* focus change,
//    reset in a layout effect — before the browser paints — so the new screen never shows one
//    full-opacity frame before fading (the flicker the old pathname-driven wrapper had).
// 2. The new screen never dips to an empty page: it starts part-visible (the old one vanishes
//    instantly, so starting at 0 would show bare background for several frames) and settles on
//    a decelerating curve, so most of it is there within the first few frames.
// 3. A tab switch is the only motion. On a tab's first visit (reached by a switch), everything
//    that mounts while it is focused — its static blocks and the rows its first fetch brings —
//    skips the FadeInView cascade, so a first visit looks like every later one, where the rows
//    are already there. After that first visit, newly arriving rows fade in as usual. The first
//    screen the app opens on keeps its page-load cascade.

type ScreenRenderFn = NonNullable<TabSlotProps['renderFn']>;

// Where the incoming screen's fade starts. High enough that the content area is never close to
// empty, low enough that the change still reads as motion rather than a cut.
const START_OPACITY = 0.35;

// Shared by the screens of one tab slot: which tab was focused last (-1 until the first screen
// has been shown), for the drift direction and the entrance rule. Module-level so a screen can
// read it during render without a ref; written only in layout effects; reset when the slot
// unmounts (log out), so the next session starts fresh.
const slotState = { lastFocused: -1 };

// Answers "is this screen still mounting as part of a tab switch?" A closure, not state, so a
// FadeInView can ask once at its own mount without re-rendering anything.
type EntranceGate = { settling: () => boolean };
const EntranceContext = createContext<EntranceGate>({ settling: () => false });

/**
 * For FadeInView: false when this component is mounting together with a tab screen that arrived
 * by a tab switch — the tab transition is that content's entrance, and both read as a stutter.
 */
export function useScreenEntrance(): boolean {
  const gate = useContext(EntranceContext);
  const [entrance] = useState(() => !gate.settling());
  return entrance;
}

export function AnimatedTabSlot() {
  useEffect(
    () => () => {
      slotState.lastFocused = -1;
    },
    [],
  );

  // The library's default render, with the screen's content wrapped. The wrapper sits *inside*
  // <Screen> so the library's focused/unfocused display rules still decide layout.
  const renderFn = useCallback<ScreenRenderFn>((descriptor, { index, isFocused, loaded, detachInactiveScreens }) => {
    const { lazy = true, unmountOnBlur, freezeOnBlur } = descriptor.options;
    if (unmountOnBlur && !isFocused) return null;
    if (lazy && !loaded && !isFocused) return null;
    return (
      <Screen
        key={descriptor.route.key}
        enabled={detachInactiveScreens}
        activityState={isFocused ? 2 : 0}
        freezeOnBlur={freezeOnBlur}
        style={[styles.screen, isFocused ? styles.focused : styles.unfocused]}>
        <TabScreen index={index} focused={isFocused}>
          {descriptor.render()}
        </TabScreen>
      </Screen>
    );
  }, []);

  return <TabSlot renderFn={renderFn} />;
}

function TabScreen({
  index,
  focused,
  children,
}: {
  index: number;
  focused: boolean;
  children: React.ReactNode;
}) {
  const reduced = useReducedMotion();
  const [progress] = useState(() => new Animated.Value(1));
  const [shift] = useState(() => new Animated.Value(0));
  // The session's first screen mounts before any tab has been focused, so it starts settled and
  // keeps its page-load cascade; a screen mounted later got here by a tab switch and settles
  // when it is first left, so its first fetch's rows don't cascade either. Read-only at
  // creation, so a dev double-render can't flip it.
  const [gate] = useState(() => {
    let settling = slotState.lastFocused !== -1;
    return { settling: () => settling, settle: () => void (settling = false) };
  });
  useEffect(() => {
    if (!focused) gate.settle();
  }, [focused, gate]);

  useLayoutEffect(() => {
    if (!focused) return;
    const from = slotState.lastFocused;
    slotState.lastFocused = index;
    if (from === -1 || from === index || reduced) {
      progress.setValue(1);
      shift.setValue(0);
      return;
    }
    // Both set before paint: the first frame the new tab shows is already transparent and
    // offset toward the side it came from.
    progress.setValue(START_OPACITY);
    shift.setValue(index > from ? 8 : -8);
    const settle = { duration: 220, easing: Easing.out(Easing.cubic), useNativeDriver: true };
    const anim = Animated.parallel([
      Animated.timing(progress, { toValue: 1, ...settle }),
      Animated.timing(shift, { toValue: 0, ...settle }),
    ]);
    return startToEnd(anim, () => {
      progress.setValue(1);
      shift.setValue(0);
    });
  }, [focused, index, reduced, progress, shift]);

  return (
    <EntranceContext.Provider value={gate}>
      <Animated.View
        style={[styles.fill, { opacity: progress, transform: [{ translateX: shift }] }]}>
        {children}
      </Animated.View>
    </EntranceContext.Provider>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  // Mirrors expo-router/ui's defaultTabsSlotRender, which this replaces only to wrap content.
  screen: { flex: 1, position: 'relative', height: '100%' },
  focused: { zIndex: 1, display: 'flex', flexShrink: 0, flexGrow: 1 },
  unfocused: { zIndex: -1, display: 'none', flexShrink: 1, flexGrow: 0 },
});

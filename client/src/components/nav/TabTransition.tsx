import { useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet } from 'react-native';
import { usePathname } from 'expo-router';

import { useReducedMotion } from '@/lib/useReducedMotion';

// The headless `expo-router/ui` <TabSlot /> swaps the focused screen with a hard cut — the
// trade-off for the fully-custom red pill bar. This wrapper gives that swap the same motion
// language as the rest of the app: content settles in with a quick fade. Where FadeInView
// rises *up* (page load = arriving), tabs are lateral siblings, so this drifts *sideways* a
// hair — the axis honestly encodes the relationship. Skipped whole under reduced motion.
export function TabTransition({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const reduced = useReducedMotion();
  // Lazy `useState` (not `useRef().current`) keeps one stable value across renders without
  // reading a ref during render — the same idiom FadeInView uses.
  const [progress] = useState(() => new Animated.Value(1));
  const prev = useRef(pathname);
  // Direction: moving toward a tab further right drifts in from the right, and vice versa.
  const [dir, setDir] = useState(1);

  useEffect(() => {
    if (prev.current === pathname) return;
    setDir(orderOf(pathname) >= orderOf(prev.current) ? 1 : -1);
    prev.current = pathname;

    if (reduced) {
      progress.setValue(1);
      return;
    }
    progress.setValue(0);
    const anim = Animated.timing(progress, {
      toValue: 1,
      duration: 220,
      useNativeDriver: true,
    });
    anim.start();
    return () => anim.stop();
  }, [pathname, reduced, progress]);

  return (
    <Animated.View
      style={[
        styles.fill,
        {
          opacity: progress,
          transform: [
            {
              translateX: progress.interpolate({
                inputRange: [0, 1],
                outputRange: [8 * dir, 0],
              }),
            },
          ],
        },
      ]}>
      {children}
    </Animated.View>
  );
}

// Left-to-right order of the tab bar, so the drift direction matches the tabs' arrangement.
const TAB_ORDER = ['/', '/calendar', '/profile'];
function orderOf(path: string): number {
  const i = TAB_ORDER.indexOf(path);
  return i === -1 ? 0 : i;
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
});

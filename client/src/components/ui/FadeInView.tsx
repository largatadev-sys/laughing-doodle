import { useEffect, useState } from 'react';
import { Animated, type ViewStyle } from 'react-native';

import { useScreenEntrance } from '@/components/nav/TabTransition';
import { startToEnd } from '@/lib/animation';
import { useReducedMotion } from '@/lib/useReducedMotion';

interface FadeInViewProps {
  children: React.ReactNode;
  delay?: number; // ms — stagger successive items
  distance?: number; // px it rises from
  style?: ViewStyle;
}

// The page-load atom: content fades up into place. Staggered by `delay`, it turns a screen
// mount into one calm cascade rather than a hard cut. Skipped entirely under reduced motion,
// and inside a tab screen that arrived by a tab switch (the tab transition is that screen's
// entrance; both at once read as a stutter, and only on first visits).
export function FadeInView({ children, delay = 0, distance = 20, style }: FadeInViewProps) {
  const reduced = useReducedMotion();
  const entrance = useScreenEntrance();
  const still = reduced || !entrance;
  const [progress] = useState(() => new Animated.Value(still ? 1 : 0));

  useEffect(() => {
    if (still) {
      progress.setValue(1);
      return;
    }
    const anim = Animated.timing(progress, {
      toValue: 1,
      duration: 340,
      delay,
      useNativeDriver: true,
    });
    return startToEnd(anim, () => progress.setValue(1));
  }, [still, delay, progress]);

  return (
    <Animated.View
      style={[
        style,
        {
          opacity: progress,
          transform: [
            {
              translateY: progress.interpolate({
                inputRange: [0, 1],
                outputRange: [distance, 0],
              }),
            },
          ],
        },
      ]}>
      {children}
    </Animated.View>
  );
}

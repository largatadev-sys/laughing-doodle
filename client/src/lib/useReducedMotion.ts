import { useEffect, useState } from 'react';
import { AccessibilityInfo, Platform } from 'react-native';

function initialReduced(): boolean {
  if (Platform.OS === 'web' && typeof window !== 'undefined' && window.matchMedia) {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
  return false;
}

// True when the viewer asked for less motion — via the OS setting on native, or the
// `prefers-reduced-motion` media query on web. Every animation in the app checks this and
// renders its final state immediately when it's on (the skill's quality floor). The mount
// value is read lazily during render; the effect only *subscribes* to later changes.
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState<boolean>(initialReduced);

  useEffect(() => {
    if (Platform.OS === 'web') {
      if (typeof window === 'undefined' || !window.matchMedia) return;
      const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
      const onChange = (e: MediaQueryListEvent) => setReduced(e.matches);
      mq.addEventListener?.('change', onChange);
      return () => mq.removeEventListener?.('change', onChange);
    }

    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then((v) => {
      if (mounted) setReduced(v);
    });
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => {
      mounted = false;
      sub?.remove?.();
    };
  }, []);

  return reduced;
}

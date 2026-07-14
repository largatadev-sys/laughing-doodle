import { useEffect, useState } from 'react';
import { Animated, View, type ViewStyle } from 'react-native';

import { useReducedMotion } from '@/lib/useReducedMotion';
import { colors } from '@/theme';

interface TallyBarProps {
  minutes: number;
  max: number; // the duration that fills the whole track
  color?: string;
  height?: number;
  track?: boolean; // show the empty track behind the fill
  animate?: boolean; // grow the fill on mount (the signature motion)
  delay?: number;
  style?: ViewStyle;
}

// THE signature — a duration rendered as accumulated length, and it *grows into place* on
// mount: in a timesheet, that motion literally is the subject (time adding up). It measures
// how much, never when, so it stays honest to date-only + duration data.
export function TallyBar({
  minutes,
  max,
  color = colors.brand,
  height = 8,
  track = true,
  animate = true,
  delay = 0,
  style,
}: TallyBarProps) {
  const reduced = useReducedMotion();
  // Floor a visible sliver so even a 15-minute entry reads as present, not empty.
  const pct = max > 0 ? Math.max(0.05, Math.min(1, minutes / max)) : 0;
  const [grow] = useState(() => new Animated.Value(animate && !reduced ? 0 : 1));

  useEffect(() => {
    if (!animate || reduced) {
      grow.setValue(1);
      return;
    }
    grow.setValue(0);
    const anim = Animated.timing(grow, {
      toValue: 1,
      duration: 620,
      delay,
      useNativeDriver: true,
    });
    anim.start();
    return () => anim.stop();
  }, [animate, reduced, pct, delay, grow]);

  return (
    <View
      style={[
        {
          height,
          borderRadius: height / 2,
          backgroundColor: track ? colors.hairline : 'transparent',
          overflow: 'hidden',
        },
        style,
      ]}>
      {/* Fill width is a plain static %; the grow-in is a numeric scaleX anchored left — no
          Animated '%'-string interpolation (unreliable on web), native-driver friendly. */}
      <Animated.View
        style={{
          width: `${pct * 100}%`,
          height: '100%',
          borderRadius: height / 2,
          backgroundColor: color,
          transform: [{ scaleX: grow }],
          transformOrigin: 'left',
        }}
      />
    </View>
  );
}

// A stacked, multi-person load bar: several people's minutes as coloured segments in one
// track. Used in the week view so a day shows both its total load and who carried it.
export function StackedTallyBar({
  segments,
  max,
  height = 10,
  style,
}: {
  segments: { minutes: number; color: string }[];
  max: number;
  height?: number;
  style?: ViewStyle;
}) {
  const total = segments.reduce((s, seg) => s + seg.minutes, 0);
  const scale = max > 0 ? Math.min(1, total / max) / (total || 1) : 0;
  return (
    <View
      style={[
        { height, borderRadius: height / 2, backgroundColor: colors.hairline, overflow: 'hidden', flexDirection: 'row' },
        style,
      ]}>
      {segments.map((seg, i) => (
        <View
          key={i}
          style={{
            width: `${seg.minutes * scale * 100}%`,
            height: '100%',
            backgroundColor: seg.color,
          }}
        />
      ))}
    </View>
  );
}

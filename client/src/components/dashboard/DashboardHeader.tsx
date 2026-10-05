import { useEffect, useState } from 'react';
import { router } from 'expo-router';
import {
  Animated,
  Easing,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  type LayoutChangeEvent,
  type ViewStyle,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { noTextSelect, type PressState } from '@/components/ui/press';
import { clockTimeOrDate, relativeWhen } from '@/lib/datetime';
import type { DashboardSummary } from '@/lib/types';
import { useReducedMotion } from '@/lib/useReducedMotion';
import { colors, fonts, radius, space, type } from '@/theme';

/**
 * The Dashboard's translucent header: back, the title, and one line saying whether Largata is
 * alive — the phone's status bar already has a clock. It floats over the content, so the
 * screen pads its scroll by whatever height this reports through `onLayout`.
 */
export function DashboardHeader({
  summary,
  stale,
  now,
  onLayout,
}: {
  summary: DashboardSummary | null;
  /** The latest refresh failed, so `summary` is older than it looks. */
  stale: boolean;
  now: Date;
  onLayout: (event: LayoutChangeEvent) => void;
}) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.header, { paddingTop: insets.top + space.md }]} onLayout={onLayout}>
      <Pressable
        onPress={() => router.back()}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel="Back"
        style={({ hovered }: PressState) => [styles.backBtn, hovered && styles.backBtnHover]}>
        <Feather name="chevron-left" size={24} color={colors.brand} />
      </Pressable>
      <View style={styles.headerText}>
        <Text style={styles.title}>Activity</Text>
        {summary && <Status summary={summary} stale={stale} now={now} />}
      </View>
    </View>
  );
}

function Status({ summary, stale, now }: { summary: DashboardSummary; stale: boolean; now: Date }) {
  // Said in either state, so a failing fetch on worklog's side never reads as Largata's state —
  // neither as Largata going quiet (a growing "last event") nor as it still being silent.
  const staleNote = stale ? ` · couldn’t refresh since ${clockTimeOrDate(summary.asOf, now)}` : '';

  if (summary.silent) {
    return (
      <View style={styles.statusRow}>
        <View style={[styles.statusDot, styles.statusDotSilent]} />
        <Text style={styles.statusSilent} numberOfLines={1}>
          {summary.silentSince
            ? `Silent since ${clockTimeOrDate(summary.silentSince, now)}`
            : 'Silent — no Snapshot yet'}
          <Text style={styles.statusText}>{staleNote}</Text>
        </Text>
      </View>
    );
  }
  return (
    <View style={styles.statusRow}>
      <LiveDot />
      <Text style={styles.statusText} numberOfLines={1}>
        <Text style={styles.statusLive}>Live</Text>
        {summary.lastEventAt ? ` · last event ${relativeWhen(summary.lastEventAt, now)}` : ''}
        {staleNote}
      </Text>
    </View>
  );
}

/** The live dot breathes (1 → 0.35 and back over 2.4 s); it holds still under reduced motion. */
function LiveDot() {
  const reduced = useReducedMotion();
  const [opacity] = useState(() => new Animated.Value(1));
  useEffect(() => {
    if (reduced) {
      opacity.setValue(1);
      return;
    }
    const half = { duration: 1200, easing: Easing.inOut(Easing.ease), useNativeDriver: true };
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 0.35, ...half }),
        Animated.timing(opacity, { toValue: 1, ...half }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [reduced, opacity]);
  return <Animated.View style={[styles.statusDot, { opacity }]} />;
}

const styles = StyleSheet.create({
  header: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingHorizontal: space.lg,
    paddingBottom: space.md,
    // Translucent like a nav bar; blurred where the platform can.
    backgroundColor: colors.surfaceTranslucent,
    borderBottomWidth: 1,
    borderBottomColor: colors.hairline,
    ...((Platform.OS === 'web' ? { backdropFilter: 'blur(8px)' } : {}) as ViewStyle),
  },
  backBtn: {
    width: 36,
    height: 36,
    marginLeft: -8,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
    ...noTextSelect,
  },
  backBtnHover: { backgroundColor: colors.brandSoft },
  headerText: { flex: 1, minWidth: 0 },
  title: { fontFamily: fonts.bold, fontSize: 17, lineHeight: 22, color: colors.text },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  statusDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: colors.brand },
  statusDotSilent: { backgroundColor: colors.textFaint },
  statusText: { ...type.caption, fontSize: 11.5, lineHeight: 15, flexShrink: 1 },
  statusLive: { fontFamily: fonts.bold, color: colors.text },
  statusSilent: {
    fontFamily: fonts.bold,
    fontSize: 11.5,
    lineHeight: 15,
    color: colors.brandDeep,
    flexShrink: 1,
  },
});

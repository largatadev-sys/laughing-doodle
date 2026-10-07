import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ActivityIndicator, Animated, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { CenteredIcon } from '@/components/nav/CenteredIcon';
import { CENTER_DISC_ID, CENTER_DISC_SIZE } from '@/components/nav/TabBar';
import type { TabBarAction } from '@/components/nav/TabBarAction';
import { noTextSelect, type PressState } from '@/components/ui/press';
import { startToEnd } from '@/lib/animation';
import { useReducedMotion } from '@/lib/useReducedMotion';
import { colors, fonts, radius, shadow, space, TAB_BAR_HEIGHT, type } from '@/theme';

/**
 * The floating red pill. Normally it holds the tab navigation (`children`); when a screen
 * lends it an action (see TabBarAction) the same pill turns into that action and back — it
 * never moves or resizes, only its contents trade places, so it reads as one object changing
 * job rather than a second bar arriving. The tabs sink and shrink a touch as they fade; the
 * action rises into their place. A count change in the label gives the button a small bump,
 * so ticking a Report has feedback at the point of the next tap. Under reduced motion every
 * change is a short fade with no movement.
 *
 * When the action was opened from the centre disc (`origin: 'center'`), a white "bridge" shape
 * carries the eye across: it starts exactly over the disc and stretches into the action's white
 * button (and shrinks back on the way out), so the disc visibly *becomes* the button.
 */

// The action row's geometry, shared by its styles and by the bridge that morphs into it.
const ROW_PAD = space.sm;
const CANCEL_SIZE = 44;
const ROW_GAP = space.sm;
const PRIMARY_HEIGHT = 44;
const PRIMARY_LEFT = ROW_PAD + CANCEL_SIZE + ROW_GAP;
// Geometry moves on [0, BRIDGE_END]; the bridge then fades over [BRIDGE_END, 1] while the real
// button (identical shape, same place) comes up underneath, so the hand-over is invisible.
const BRIDGE_END = 0.85;
// The resting look of a disabled action button (e.g. "Hand off (0)"); the bridge dims to it.
const DISABLED_OPACITY = 0.55;
export function MorphingPill({
  action,
  discIcon,
  children,
}: {
  action: TabBarAction | null;
  /** The glyph the centre disc shows right now, so the bridge carries the same one. */
  discIcon: TabBarAction['icon'];
  children: React.ReactNode;
}) {
  const reduced = useReducedMotion();
  const active = action !== null;
  const [mode] = useState(() => new Animated.Value(active ? 1 : 0));
  // The last action stays rendered while the pill morphs back, so the exit has something to
  // fade out; it is dropped once the navigation is fully back.
  const [lingering, setLingering] = useState<TabBarAction | null>(action);
  // Kept in step during render (React's "adjust state when a prop changes" pattern) rather than
  // in an effect, so the exit always has the latest label to fade out.
  if (action && action !== lingering) setLingering(action);
  const shown = action ?? lingering;

  // The bridge animates layout (left/width/top/height), which the native driver can't, so it
  // runs on its own JS-driven value in step with `mode`, on the same spring.
  const [morph] = useState(() => new Animated.Value(active ? 1 : 0));
  const [pillWidth, setPillWidth] = useState(0);
  const bridged = !reduced && shown?.origin === 'center' && pillWidth > 0;
  // While bridged, the real button stays hidden until the bridge has reached it, then the two
  // crossfade; the bridge dims to the button's own resting look, so nothing snaps.
  const reveal = bridged
    ? morph.interpolate({ inputRange: [BRIDGE_END, 1], outputRange: [0, 1], extrapolate: 'clamp' })
    : null;

  // Keyboard focus follows the face: when the face holding focus goes inert the browser drops
  // focus to the page, so the newly shown face takes it (Layer reports the hand-over; its
  // effect runs before this one in the same commit).
  const pillRef = useRef<View>(null);
  const focusYielded = useRef(false);
  useEffect(() => {
    if (Platform.OS !== 'web' || !focusYielded.current) return;
    focusYielded.current = false;
    const pill = pillRef.current as unknown as HTMLElement | null;
    const disc = active ? null : pill?.querySelector<HTMLElement>('#' + CENTER_DISC_ID);
    const first = pill?.querySelector<HTMLElement>(':scope > :not([inert]) [tabindex="0"]');
    (disc ?? first)?.focus();
  }, [active]);

  useEffect(() => {
    const toValue = active ? 1 : 0;
    const spring = { toValue, speed: 16, bounciness: 5 };
    const anim = reduced
      ? Animated.timing(mode, { toValue, duration: 140, useNativeDriver: true })
      : Animated.parallel([
          Animated.spring(mode, { ...spring, useNativeDriver: true }),
          Animated.spring(morph, { ...spring, useNativeDriver: false }),
        ]);
    return startToEnd(
      anim,
      () => {
        mode.setValue(toValue);
        morph.setValue(toValue);
      },
      () => {
        if (!active) setLingering(null);
      },
    );
  }, [active, reduced, mode, morph]);

  const clamp = { extrapolate: 'clamp' as const };
  const navStyle = {
    opacity: mode.interpolate({ inputRange: [0, 0.6], outputRange: [1, 0], ...clamp }),
    // No sink while bridged: the disc must stay exactly under the bridge that replaces it, or
    // it peeks out below as the two shapes part.
    transform: reduced || bridged
      ? []
      : [
          { translateY: mode.interpolate({ inputRange: [0, 1], outputRange: [0, 10] }) },
          { scale: mode.interpolate({ inputRange: [0, 1], outputRange: [1, 0.94], ...clamp }) },
        ],
  };
  const actionStyle = {
    opacity: mode.interpolate({ inputRange: [0.4, 1], outputRange: [0, 1], ...clamp }),
    transform: reduced
      ? []
      : [
          { translateY: mode.interpolate({ inputRange: [0, 1], outputRange: [10, 0] }) },
          { scale: mode.interpolate({ inputRange: [0, 1], outputRange: [0.94, 1], ...clamp }) },
        ],
  };

  return (
    <View style={styles.stack} pointerEvents="box-none">
      {shown?.error && active ? <ErrorNote message={shown.error} /> : null}
      <View
        ref={pillRef}
        style={styles.pill}
        onLayout={(e) => setPillWidth(e.nativeEvent.layout.width)}>
        <Layer
          hidden={active}
          style={[styles.navLayer, navStyle]}
          onYieldFocus={() => (focusYielded.current = true)}>
          {children}
        </Layer>
        {shown && (
          <Layer
            hidden={!active}
            style={[styles.actionLayer, actionStyle]}
            onYieldFocus={() => (focusYielded.current = true)}>
            <ActionContent action={shown} reveal={reveal} />
          </Layer>
        )}
        {bridged && (
          <Bridge
            morph={morph}
            pillWidth={pillWidth}
            icon={discIcon}
            dim={shown.disabled ? DISABLED_OPACITY : 1}
          />
        )}
      </View>
    </View>
  );
}

// The white shape that turns the centre disc into the action's button: over the disc at 0,
// exactly over the button at BRIDGE_END, gone at both ends so it never doubles a real control.
// It carries the glyph the disc shows *now*: on the way back that is the one the disc will
// land on, even if it changed meanwhile (send -> plus when selection ends on another
// segment), so the hand-back never snaps. It dims toward the button's resting look (`dim`)
// as it stretches. Decorative: no input, no a11y.
function Bridge({
  morph,
  pillWidth,
  icon,
  dim,
}: {
  morph: Animated.Value;
  pillWidth: number;
  icon: TabBarAction['icon'];
  dim: number;
}) {
  const clamp = { extrapolate: 'clamp' as const };
  const geometry = (from: number, to: number) =>
    morph.interpolate({ inputRange: [0, BRIDGE_END], outputRange: [from, to], ...clamp });
  const primaryWidth = pillWidth - PRIMARY_LEFT - ROW_PAD;
  return (
    <Animated.View
      pointerEvents="none"
      aria-hidden
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[
        styles.bridge,
        {
          left: geometry((pillWidth - CENTER_DISC_SIZE) / 2, PRIMARY_LEFT),
          width: geometry(CENTER_DISC_SIZE, primaryWidth),
          top: geometry((TAB_BAR_HEIGHT - CENTER_DISC_SIZE) / 2, (TAB_BAR_HEIGHT - PRIMARY_HEIGHT) / 2),
          height: geometry(CENTER_DISC_SIZE, PRIMARY_HEIGHT),
          opacity: morph.interpolate({
            inputRange: [0, 0.02, BRIDGE_END, 1],
            outputRange: [0, 1, dim, 0],
            ...clamp,
          }),
        },
      ]}>
      <Animated.View
        style={{ opacity: morph.interpolate({ inputRange: [0, 0.35], outputRange: [1, 0], ...clamp }) }}>
        <CenteredIcon name={icon} size={icon === 'plus' ? 26 : 22} color={colors.brand} />
      </Animated.View>
    </Animated.View>
  );
}

// One of the pill's two faces. A hidden face must be out of reach for every input, not just
// invisible: pointerEvents stops taps, and on web the `inert` attribute also takes its buttons
// out of the Tab order and the accessibility tree (aria-hidden alone leaves them focusable).
// On native the descendants are hidden from the screen reader instead.
function Layer({
  hidden,
  style,
  onYieldFocus,
  children,
}: {
  hidden: boolean;
  style: React.ComponentProps<typeof Animated.View>['style'];
  /** Called when this face goes inert while holding keyboard focus. */
  onYieldFocus: () => void;
  children: React.ReactNode;
}) {
  const ref = useRef<View>(null);
  // Read through a ref so a fresh closure each render doesn't re-run the inert effect.
  const yieldFocus = useRef(onYieldFocus);
  useLayoutEffect(() => {
    yieldFocus.current = onYieldFocus;
  });
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    const node = ref.current as unknown as HTMLElement | null;
    if (!node) return;
    if (hidden) {
      if (node.contains(document.activeElement)) yieldFocus.current();
      node.setAttribute('inert', '');
    } else {
      node.removeAttribute('inert');
    }
  }, [hidden]);
  return (
    <Animated.View
      ref={ref}
      style={style}
      pointerEvents={hidden ? 'none' : 'box-none'}
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}>
      {children}
    </Animated.View>
  );
}

function ActionContent({
  action,
  reveal,
}: {
  action: TabBarAction;
  /** While the bridge is morphing into the button, the button's own opacity; null otherwise. */
  reveal: Animated.AnimatedInterpolation<number> | null;
}) {
  return (
    <View style={styles.actionRow}>
      <Pressable
        onPress={action.onCancel}
        accessibilityRole="button"
        accessibilityLabel={action.cancelLabel}
        hitSlop={6}
        style={({ pressed, hovered }: PressState) => [
          styles.cancel,
          hovered && styles.cancelHover,
          pressed && styles.pressed,
        ]}>
        <CenteredIcon name="x" size={22} color={colors.onBrand} />
      </Pressable>
      <PillPrimary
        label={action.label}
        icon={action.icon}
        onPress={action.onPress}
        disabled={action.disabled}
        loading={action.loading}
        style={[styles.primaryGrow, reveal && { opacity: reveal }]}
      />
    </View>
  );
}

/**
 * The white button that sits on the red pill — the pill's one action. A change of label
 * (a count ticking, "Copy" becoming "Copied") gives it a small bump, so the tap is answered
 * where the finger is. No bump under reduced motion.
 */
function PillPrimary({
  label,
  icon,
  onPress,
  disabled,
  loading,
  style,
}: {
  label: string;
  icon: keyof typeof Feather.glyphMap;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  style?: React.ComponentProps<typeof Animated.View>['style'];
}) {
  const reduced = useReducedMotion();
  const [bump] = useState(() => new Animated.Value(1));
  const previous = useRef(label);

  useEffect(() => {
    if (previous.current === label) return;
    previous.current = label;
    if (reduced) return;
    bump.setValue(1.06);
    const anim = Animated.spring(bump, { toValue: 1, useNativeDriver: true, speed: 20, bounciness: 12 });
    return startToEnd(anim, () => bump.setValue(1));
  }, [label, reduced, bump]);

  const inert = disabled || loading;
  return (
    <Animated.View style={[style, { transform: [{ scale: bump }] }]}>
      <Pressable
        onPress={onPress}
        disabled={inert}
        accessibilityRole="button"
        // Named explicitly: while loading, the label is swapped for a spinner.
        accessibilityLabel={label}
        accessibilityState={{ disabled: !!inert, busy: !!loading }}
        style={({ pressed }: PressState) => [
          styles.primary,
          disabled && styles.primaryDisabled,
          pressed && !inert && styles.pressed,
        ]}>
        {loading ? (
          <ActivityIndicator color={colors.brand} />
        ) : (
          <>
            <Feather name={icon} size={18} color={colors.brand} />
            <Text style={styles.primaryLabel} numberOfLines={1}>
              {label}
            </Text>
          </>
        )}
      </Pressable>
    </Animated.View>
  );
}

// A failed action explains itself right where the next tap goes, without pushing the list.
export function ErrorNote({ message }: { message: string }) {
  const reduced = useReducedMotion();
  const [progress] = useState(() => new Animated.Value(0));
  useEffect(() => {
    const anim = Animated.timing(progress, { toValue: 1, duration: 180, useNativeDriver: true });
    return startToEnd(anim, () => progress.setValue(1));
  }, [progress]);
  return (
    <Animated.View
      accessibilityRole="alert"
      style={[
        styles.error,
        {
          opacity: progress,
          transform: reduced
            ? []
            : [{ translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [6, 0] }) }],
        },
      ]}>
      <Text style={styles.errorText}>{message}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  stack: { alignItems: 'center', gap: space.sm },
  pill: {
    height: TAB_BAR_HEIGHT,
    borderRadius: radius.pill,
    backgroundColor: colors.brand,
    // No overflow clipping: on iOS it would clip the pill's own shadow, and the pill is on
    // every native screen. The layers' 10px drift happens while they're nearly transparent.
    ...shadow.floating,
  },
  navLayer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    height: TAB_BAR_HEIGHT,
    paddingHorizontal: space.sm,
  },
  actionLayer: { ...StyleSheet.absoluteFill, justifyContent: 'center' },
  actionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: ROW_GAP,
    paddingHorizontal: ROW_PAD,
  },
  cancel: {
    width: CANCEL_SIZE,
    height: CANCEL_SIZE,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.18)',
    cursor: 'pointer',
    ...noTextSelect,
  },
  cancelHover: { backgroundColor: 'rgba(255,255,255,0.28)' },
  primaryGrow: { flex: 1 },
  primary: {
    height: PRIMARY_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.sm,
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    cursor: 'pointer',
    ...noTextSelect,
  },
  primaryDisabled: { opacity: DISABLED_OPACITY },
  bridge: {
    position: 'absolute',
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryLabel: { fontFamily: fonts.extrabold, fontSize: 15, color: colors.brand },
  pressed: { transform: [{ scale: 0.95 }] },
  error: {
    maxWidth: 320,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    ...shadow.card,
  },
  errorText: { ...type.body, color: colors.brand, textAlign: 'center' },
});

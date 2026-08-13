import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  Platform,
  useWindowDimensions,
  View,
} from 'react-native';
import { Feather } from '@expo/vector-icons';

import { useAuthedImage } from '@/lib/useAuthedImage';
import { useReducedMotion } from '@/lib/useReducedMotion';
import { colors, fonts, radius, space, type } from '@/theme';

const THUMB_SIZE = 108;

interface ReportScreenshotsProps {
  reportId: string;
  ordinals: number[];
}

/** The evidence strip on a report's detail screen: thumbnails that open full-size. */
export function ReportScreenshots({ reportId, ordinals }: ReportScreenshotsProps) {
  // Position within `ordinals`, not the ordinal itself — the lightbox pages through the
  // report's screenshots in order, and ordinals are only incidentally 0,1,2.
  const [viewing, setViewing] = useState<number | null>(null);

  if (ordinals.length === 0) return null;

  return (
    <View style={styles.block}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.strip}>
        {ordinals.map((ordinal, i) => (
          <Thumb
            key={ordinal}
            reportId={reportId}
            ordinal={ordinal}
            position={i}
            total={ordinals.length}
            onPress={() => setViewing(i)}
          />
        ))}
      </ScrollView>

      <Lightbox
        reportId={reportId}
        ordinals={ordinals}
        index={viewing}
        onIndexChange={setViewing}
        onClose={() => setViewing(null)}
      />
    </View>
  );
}

function Thumb({
  reportId,
  ordinal,
  position,
  total,
  onPress,
}: {
  reportId: string;
  ordinal: number;
  position: number;
  total: number;
  onPress: () => void;
}) {
  const image = useAuthedImage(`/api/reports/${reportId}/screenshots/${ordinal}`);

  return (
    <Pressable
      onPress={onPress}
      disabled={image.status !== 'ready'}
      accessibilityRole="imagebutton"
      accessibilityLabel={`Screenshot ${position + 1} of ${total}, tap to view full size`}
      style={({ pressed }) => [styles.thumb, pressed && styles.thumbPressed]}>
      {image.status === 'ready' ? (
        <Image
          source={{ uri: image.uri, headers: image.headers }}
          style={styles.thumbImage}
          resizeMode="cover"
          accessibilityIgnoresInvertColors
        />
      ) : (
        <View style={styles.thumbPlaceholder}>
          {image.status === 'loading' ? (
            <ActivityIndicator size="small" color={colors.brand} />
          ) : (
            <Feather name="image" size={18} color={colors.textFaint} />
          )}
        </View>
      )}
    </Pressable>
  );
}

function Lightbox({
  reportId,
  ordinals,
  index,
  onIndexChange,
  onClose,
}: {
  reportId: string;
  ordinals: number[];
  index: number | null;
  onIndexChange: (index: number) => void;
  onClose: () => void;
}) {
  const reduced = useReducedMotion();
  const { width, height } = useWindowDimensions();
  // Leaves the pill above and the dots + close hint below outside the image area.
  const pageHeight = Math.max(200, height - 220);
  const scroller = useRef<ScrollView>(null);
  const open = index !== null;

  // Drive the pager from an index — used by the arrows, the arrow keys, and the mouse drag,
  // so every input route ends up in the same place as a touch swipe.
  const goTo = useCallback(
    (next: number) => {
      const clamped = Math.max(0, Math.min(ordinals.length - 1, next));
      scroller.current?.scrollTo({ x: clamped * width, animated: !reduced });
      onIndexChange(clamped);
    },
    [ordinals.length, width, reduced, onIndexChange],
  );

  // Mouse drag, because react-native-web only wires touch events into ScrollView scrolling —
  // a click-drag does nothing there, which left PC users unable to swipe between photos at
  // all. Tracked on the window so a drag that leaves the image still resolves.
  const drag = useRef<{ startX: number; moved: boolean } | null>(null);
  // Set for a moment after a drag so the scrim's click-to-close ignores the release.
  const draggedRecently = useRef(false);

  const onMouseDown = useCallback((e: { nativeEvent: { pageX?: number; sourceCapabilities?: unknown } }) => {
    // Touch devices emit synthetic mouse events after a tap. Ignoring them keeps the touch
    // path purely native — otherwise a swipe could page twice, once per event family.
    const native = e.nativeEvent as { pageX?: number; sourceCapabilities?: { firesTouchEvents?: boolean } };
    if (native.sourceCapabilities?.firesTouchEvents) return;
    if (typeof native.pageX === 'number') drag.current = { startX: native.pageX, moved: false };
  }, []);

  useEffect(() => {
    if (!open || Platform.OS !== 'web' || typeof window === 'undefined') return;

    // A quarter of the screen is a deliberate, unambiguous drag — short enough to feel light,
    // long enough that a sloppy click never pages.
    const threshold = width / 4;

    const onMove = (e: MouseEvent) => {
      if (drag.current && Math.abs(e.pageX - drag.current.startX) > 6) drag.current.moved = true;
    };
    const onUp = (e: MouseEvent) => {
      const d = drag.current;
      drag.current = null;
      if (!d || !d.moved) return;
      // Suppress the click that follows this release, so the scrim doesn't read the end of a
      // drag as "tap anywhere to close".
      draggedRecently.current = true;
      setTimeout(() => {
        draggedRecently.current = false;
      }, 100);
      const dx = e.pageX - d.startX;
      if (dx <= -threshold) goTo((index ?? 0) + 1);
      else if (dx >= threshold) goTo((index ?? 0) - 1);
    };

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, [open, width, index, goTo]);

  // ← / → page, Escape closes. Keyboard is the third input this viewer has to serve, after
  // touch and mouse, and it costs almost nothing to support.
  useEffect(() => {
    if (!open || Platform.OS !== 'web' || typeof window === 'undefined') return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') goTo((index ?? 0) + 1);
      else if (e.key === 'ArrowLeft') goTo((index ?? 0) - 1);
      else if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, index, goTo, onClose]);

  // Jump the pager to the tapped thumbnail when the lightbox opens. Without this the pager
  // would always start at the first screenshot regardless of which thumb was tapped.
  useEffect(() => {
    if (!open) return;
    const target = index * width;
    const id = setTimeout(() => scroller.current?.scrollTo({ x: target, animated: false }), 0);
    return () => clearTimeout(id);
    // Deliberately keyed on `open` only: re-running on every index change would fight the
    // user's own swipe, which is what moves the index the rest of the time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, width]);

  return (
    <Modal
      visible={open}
      transparent
      // Under reduced motion the overlay appears rather than fades.
      animationType={reduced ? 'none' : 'fade'}
      onRequestClose={onClose}>
      <View style={styles.backdrop}>
        {/* The whole scrim is the close target. It sits BEHIND the pager, so a tap that
            misses the image still closes — "tap anywhere" has to mean anywhere. */}
        <Pressable
          style={StyleSheet.absoluteFill}
          // A drag that ends here is a page turn, not a dismissal: without this, swiping with
          // a mouse would close the viewer the moment you released.
          onPress={() => {
            if (!draggedRecently.current) onClose();
          }}
          accessibilityLabel="Close screenshot"
        />
        <ScrollView
          ref={scroller}
          horizontal
          pagingEnabled
          // Pinch-to-zoom, iOS-native. On Android and web the contain-fit image plus the
          // system's own page zoom cover the same need; RN exposes no cross-platform pinch.
          maximumZoomScale={4}
          minimumZoomScale={1}
          showsHorizontalScrollIndicator={false}
          onScroll={(e) => {
            // `onScroll` rather than `onMomentumScrollEnd`: web scrolling often produces no
            // momentum phase, so the counter and dots would never advance there.
            const next = Math.round(e.nativeEvent.contentOffset.x / width);
            if (next !== index && next >= 0 && next < ordinals.length) onIndexChange(next);
          }}
          scrollEventThrottle={16}
          // @ts-expect-error — react-native-web forwards DOM mouse handlers; RN's types don't
          // model them, but this is the only way to catch a mouse drag on the pager. Touch
          // scrolling is untouched: the ScrollView keeps handling that natively.
          onMouseDown={onMouseDown}
          style={styles.pager}>
          {ordinals.map((ordinal, i) => (
            // Not pressable: the scrim beneath handles closing, and a Pressable here would
            // compete with the pager's horizontal swipe.
            //
            // Width AND height are explicit pixels. Inside a horizontal ScrollView children
            // size to their content, so neither a percentage nor flex:1 gives a page any
            // height — that is what rendered the images 0px tall.
            <View key={ordinal} style={[styles.page, { width, height: pageHeight }]}>
              <FullImage reportId={reportId} ordinal={ordinal} position={i} total={ordinals.length} />
            </View>
          ))}
        </ScrollView>

        {ordinals.length > 1 && (
          <View style={styles.pagerPill} pointerEvents="none">
            <Text style={styles.pagerText}>
              {(index ?? 0) + 1} of {ordinals.length}
            </Text>
          </View>
        )}

        {/* Arrows, because swipe is touch-only: dragging a ScrollView with a mouse does
            nothing on web, which left multi-screenshot reports with no way through. Shown
            only when there is more than one, and disabled at each end rather than wrapping —
            two or three screenshots is not a carousel worth looping. */}
        {ordinals.length > 1 && (
          <>
            <PagerArrow
              side="left"
              disabled={(index ?? 0) === 0}
              onPress={() => goTo((index ?? 0) - 1)}
            />
            <PagerArrow
              side="right"
              disabled={(index ?? 0) >= ordinals.length - 1}
              onPress={() => goTo((index ?? 0) + 1)}
            />
          </>
        )}

        {ordinals.length > 1 && (
          <View style={styles.dots} pointerEvents="none">
            {ordinals.map((ordinal, i) => (
              <View key={ordinal} style={[styles.dot, i === index && styles.dotActive]} />
            ))}
          </View>
        )}

        <View style={styles.closeHint} pointerEvents="none">
          <Feather name="x" size={18} color={colors.onBrand} />
          <Text style={styles.closeText}>Tap anywhere to close</Text>
        </View>
      </View>
    </Modal>
  );
}

function PagerArrow({
  side,
  disabled,
  onPress,
}: {
  side: 'left' | 'right';
  disabled: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={side === 'left' ? 'Previous screenshot' : 'Next screenshot'}
      accessibilityState={{ disabled }}
      style={({ pressed, hovered }) => [
        styles.arrow,
        side === 'left' ? styles.arrowLeft : styles.arrowRight,
        hovered && !disabled && styles.arrowHovered,
        pressed && !disabled && styles.arrowPressed,
        disabled && styles.arrowDisabled,
      ]}>
      <Feather
        name={side === 'left' ? 'chevron-left' : 'chevron-right'}
        size={26}
        color={colors.onBrand}
      />
    </Pressable>
  );
}

function FullImage({
  reportId,
  ordinal,
  position,
  total,
}: {
  reportId: string;
  ordinal: number;
  position: number;
  total: number;
}) {
  const image = useAuthedImage(`/api/reports/${reportId}/screenshots/${ordinal}`);

  if (image.status === 'loading') {
    return <ActivityIndicator color={colors.onBrand} />;
  }
  if (image.status === 'error') {
    return <Text style={styles.errorText}>That screenshot could not be loaded.</Text>;
  }
  return (
    <Image
      source={{ uri: image.uri, headers: image.headers }}
      style={styles.fullImage}
      resizeMode="contain"
      accessibilityIgnoresInvertColors
      accessibilityLabel={`Screenshot ${position + 1} of ${total}`}
    />
  );
}

const styles = StyleSheet.create({
  block: { gap: space.sm },
  strip: { flexDirection: 'row', gap: space.sm, paddingVertical: space.xs },
  thumb: {
    width: THUMB_SIZE,
    height: THUMB_SIZE,
    borderRadius: radius.md,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface,
    cursor: 'pointer',
  },
  thumbPressed: { opacity: 0.9, transform: [{ scale: 0.98 }] },
  thumbImage: { width: '100%', height: '100%' },
  thumbPlaceholder: { flex: 1, alignItems: 'center', justifyContent: 'center' },

  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(26,26,30,0.94)',
    justifyContent: 'center',
  },
  // The pager fills the scrim and each page fills the pager. Percentage heights were the bug:
  // `height: '82%'` resolved against a flexGrow:0 ScrollView that had no height of its own, so
  // the images loaded correctly and then rendered 0px tall.
  pager: { flexGrow: 0 },
  page: { alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.lg },
  fullImage: { width: '100%', height: '100%' },

  pagerPill: {
    position: 'absolute',
    top: space.xxl + space.lg,
    alignSelf: 'center',
    paddingHorizontal: space.md,
    paddingVertical: 5,
    borderRadius: radius.pill,
    backgroundColor: 'rgba(255,255,255,0.16)',
  },
  pagerText: { ...type.caption, color: colors.onBrand, fontFamily: fonts.bold },

  arrow: {
    position: 'absolute',
    top: '50%',
    marginTop: -26,
    width: 52,
    height: 52,
    borderRadius: radius.pill,
    backgroundColor: 'rgba(255,255,255,0.16)',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
  },
  arrowLeft: { left: space.sm },
  arrowRight: { right: space.sm },
  arrowHovered: { backgroundColor: 'rgba(255,255,255,0.28)' },
  arrowPressed: { backgroundColor: 'rgba(255,255,255,0.34)', transform: [{ scale: 0.94 }] },
  // Kept in place rather than hidden at the ends, so the controls don't jump around.
  arrowDisabled: { opacity: 0.25 },

  dots: {
    position: 'absolute',
    bottom: space.xxl + space.xl,
    alignSelf: 'center',
    flexDirection: 'row',
    gap: 6,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  dotActive: { backgroundColor: colors.onBrand },

  closeHint: {
    position: 'absolute',
    bottom: space.xxl,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  closeText: { ...type.caption, color: colors.onBrand },
  errorText: { ...type.body, color: colors.onBrand },
});

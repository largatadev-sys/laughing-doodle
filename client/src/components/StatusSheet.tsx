import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { Feather } from '@expo/vector-icons';

import { noTextSelect, type PressState } from '@/components/ui/press';
import { STATUS_EDGE, STATUS_LABELS, STATUS_ORDER } from '@/lib/reportStatus';
import { useReducedMotion } from '@/lib/useReducedMotion';
import type { ReportResponse, ReportStatus } from '@/lib/types';
import { colors, fonts, radius, space, type } from '@/theme';

interface StatusSheetProps {
  report: ReportResponse | null;
  saving: ReportStatus | null;
  /** A failed move. The sheet stays open on failure, so this has to be shown *here* — the
   *  screen's own error line is behind the modal and nobody would ever see it. */
  error: string | null;
  onSelect: (status: ReportStatus) => void;
  onClose: () => void;
}

/**
 * The full status list, reachable by long-pressing a row when the two swipe actions aren't the
 * move you want. Dismissed is separated from the forward path and labelled with its consequence
 * — "won't act" should never read as the next step after Done.
 */
export function StatusSheet({ report, saving, error, onSelect, onClose }: StatusSheetProps) {
  const reduced = useReducedMotion();
  const forward = STATUS_ORDER.filter((s) => s !== 'dismissed');

  // The pick is acknowledged locally, the instant it happens — the write still has to reach
  // the server, and a tap that shows nothing until it does feels broken. This never decides
  // what the report's status *is*; it only says "you chose this just now".
  //
  // Stamped with the report it belongs to and derived rather than reset in an effect, so a
  // pick simply stops applying the moment the sheet is showing a different report — or the
  // moment the move fails, since a tick left over a failed write would be a lie.
  const [pickedOn, setPicked] = useState<{ reportId: string; status: ReportStatus } | null>(null);
  const picked = pickedOn && pickedOn.reportId === report?.id && !error ? pickedOn.status : null;

  function pick(status: ReportStatus) {
    if (report) setPicked({ reportId: report.id, status });
    onSelect(status);
  }

  return (
    <Modal
      visible={report !== null}
      transparent
      animationType={reduced ? 'none' : 'slide'}
      onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="Close">
        {/* Stop taps inside the sheet from closing it. */}
        <Pressable style={styles.sheet} onPress={() => {}}>
          <View style={styles.grabber} />
          <Text style={styles.title}>Move this report</Text>
          <Text style={styles.subtitle}>
            Your name goes on the change — visible to the whole team.
          </Text>

          <View style={styles.options}>
            {forward.map((status) => (
              <Option
                key={status}
                status={status}
                current={report?.status === status}
                picked={picked === status}
                saving={saving === status}
                disabled={saving !== null}
                reduced={reduced}
                onPress={() => pick(status)}
              />
            ))}
          </View>

          <View style={styles.separator} />

          <Option
            status="dismissed"
            current={report?.status === 'dismissed'}
            picked={picked === 'dismissed'}
            saving={saving === 'dismissed'}
            disabled={saving !== null}
            reduced={reduced}
            onPress={() => pick('dismissed')}
            consequence="we won't act on this"
          />

          {error && <Text style={styles.error}>{error}</Text>}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

function Option({
  status,
  current,
  picked,
  saving,
  disabled,
  reduced,
  onPress,
  consequence,
}: {
  status: ReportStatus;
  current: boolean;
  /** Chosen just now, whether or not the write has landed — this is what animates. */
  picked: boolean;
  saving: boolean;
  disabled: boolean;
  reduced: boolean;
  onPress: () => void;
  consequence?: string;
}) {
  // Named for the animation, not the action — the sheet above has a `pick()` function.
  const [pickAnim] = useState(() => new Animated.Value(0));

  // The dot swells and settles, and a tick fades in beside it. Everything about the pick is
  // immediate; the spinner that follows means "still saving", not "did that register?".
  useEffect(() => {
    if (!picked) {
      pickAnim.setValue(0);
      return;
    }
    if (reduced) {
      pickAnim.setValue(1);
      return;
    }
    const anim = Animated.spring(pickAnim, {
      toValue: 1,
      useNativeDriver: true,
      speed: 20,
      bounciness: 14,
    });
    anim.start();
    return () => anim.stop();
  }, [picked, reduced, pickAnim]);

  // Overshoot on the way, settling a touch larger than it started — the dot reads as "taken".
  const dotScale = pickAnim.interpolate({ inputRange: [0, 0.6, 1], outputRange: [1, 1.5, 1.2] });
  const chosen = current || picked;

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ selected: chosen, disabled, busy: saving }}
      accessibilityLabel={`Move to ${STATUS_LABELS[status]}${consequence ? `, ${consequence}` : ''}`}
      style={({ pressed, hovered }: PressState) => [
        styles.option,
        // Highlights the moment you tap, not when the server agrees.
        chosen && styles.optionCurrent,
        hovered && !chosen && styles.optionHover,
        pressed && styles.optionPressed,
      ]}>
      <Animated.View
        style={[
          styles.dot,
          { backgroundColor: STATUS_EDGE[status], transform: [{ scale: dotScale }] },
        ]}
      />
      <Text style={[styles.optionText, chosen && styles.optionTextCurrent]}>
        {STATUS_LABELS[status]}
        {consequence ? <Text style={styles.consequence}> — {consequence}</Text> : null}
      </Text>
      {picked ? (
        <Animated.View style={{ opacity: pickAnim, transform: [{ scale: pickAnim }] }}>
          <Feather name="check" size={17} color={colors.brand} />
        </Animated.View>
      ) : null}
      {saving ? (
        <ActivityIndicator size="small" color={colors.brand} />
      ) : current && !picked ? (
        <Text style={styles.currentMark}>current</Text>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(26,26,30,0.45)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    paddingHorizontal: space.lg,
    paddingTop: space.sm,
    paddingBottom: space.xxl,
    gap: 2,
    // The sheet mounts under a pointer that is still held down from the long-press, so a
    // selection started on the row would run straight into this text. It is all control
    // labels anyway — nothing here is meant to be copied.
    ...noTextSelect,
  },
  grabber: {
    alignSelf: 'center',
    width: 38,
    height: 4,
    borderRadius: radius.pill,
    backgroundColor: colors.hairline,
    marginBottom: space.md,
  },
  title: { ...type.title },
  subtitle: { ...type.caption, marginBottom: space.sm },

  options: { marginTop: space.xs },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    minHeight: 48,
    paddingHorizontal: space.sm,
    borderRadius: radius.md,
    cursor: 'pointer',
  },
  optionCurrent: { backgroundColor: colors.brandSoft },
  optionHover: { backgroundColor: colors.bg },
  optionPressed: { opacity: 0.8 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  optionText: { ...type.bodyMedium, flex: 1 },
  optionTextCurrent: { fontFamily: fonts.bold },
  consequence: { ...type.caption, fontSize: 13 },
  currentMark: { ...type.caption, fontFamily: fonts.bold },

  separator: {
    height: 1,
    backgroundColor: colors.hairline,
    marginVertical: space.sm,
  },
  error: { ...type.caption, color: colors.brand, fontFamily: fonts.semibold, marginTop: space.sm },
});

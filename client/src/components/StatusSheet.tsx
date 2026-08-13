import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import type { PressState } from '@/components/ui/press';
import { STATUS_EDGE, STATUS_LABELS, STATUS_ORDER } from '@/lib/reportStatus';
import { useReducedMotion } from '@/lib/useReducedMotion';
import type { ReportResponse, ReportStatus } from '@/lib/types';
import { colors, fonts, radius, space, type } from '@/theme';

interface StatusSheetProps {
  report: ReportResponse | null;
  saving: ReportStatus | null;
  onSelect: (status: ReportStatus) => void;
  onClose: () => void;
}

/**
 * The full status list, reachable by long-pressing a row when the two swipe actions aren't the
 * move you want. Dismissed is separated from the forward path and labelled with its consequence
 * — "won't act" should never read as the next step after Done.
 */
export function StatusSheet({ report, saving, onSelect, onClose }: StatusSheetProps) {
  const reduced = useReducedMotion();
  const forward = STATUS_ORDER.filter((s) => s !== 'dismissed');

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
                saving={saving === status}
                disabled={saving !== null}
                onPress={() => onSelect(status)}
              />
            ))}
          </View>

          <View style={styles.separator} />

          <Option
            status="dismissed"
            current={report?.status === 'dismissed'}
            saving={saving === 'dismissed'}
            disabled={saving !== null}
            onPress={() => onSelect('dismissed')}
            consequence="we won't act on this"
          />
        </Pressable>
      </Pressable>
    </Modal>
  );
}

function Option({
  status,
  current,
  saving,
  disabled,
  onPress,
  consequence,
}: {
  status: ReportStatus;
  current: boolean;
  saving: boolean;
  disabled: boolean;
  onPress: () => void;
  consequence?: string;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ selected: current, disabled }}
      accessibilityLabel={`Move to ${STATUS_LABELS[status]}${consequence ? `, ${consequence}` : ''}`}
      style={({ pressed, hovered }: PressState) => [
        styles.option,
        current && styles.optionCurrent,
        hovered && !current && styles.optionHover,
        pressed && styles.optionPressed,
      ]}>
      <View style={[styles.dot, { backgroundColor: STATUS_EDGE[status] }]} />
      <Text style={[styles.optionText, current && styles.optionTextCurrent]}>
        {STATUS_LABELS[status]}
        {consequence ? <Text style={styles.consequence}> — {consequence}</Text> : null}
      </Text>
      {saving ? (
        <ActivityIndicator size="small" color={colors.brand} />
      ) : current ? (
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
});

import { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import {
  formatMediumDate,
  isSameDay,
  monthMatrix,
  monthName,
  parseISODate,
  toISODate,
} from '@/lib/datetime';
import { colors, fonts, radius, shadow, space, type } from '@/theme';
import type { PressState } from './press';

const WEEKDAY_LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

// A tappable field that opens a Largata calendar to pick a day — no free-text date typing,
// no locale ambiguity, and it reads the same as the Calendar tab. Cross-platform (the same
// grid on web and native).
export function DatePickerField({ value, onChange }: { value: string; onChange: (iso: string) => void }) {
  const [open, setOpen] = useState(false);
  const [viewMonth, setViewMonth] = useState(() => parseISODate(value || toISODate(new Date())));

  const selected = value ? parseISODate(value) : null;
  const today = new Date();

  function openPicker() {
    setViewMonth(selected ?? new Date());
    setOpen(true);
  }
  function pick(day: Date) {
    onChange(toISODate(day));
    setOpen(false);
  }
  function shiftMonth(dir: -1 | 1) {
    setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + dir, 1));
  }

  return (
    <>
      <Pressable
        onPress={openPicker}
        accessibilityRole="button"
        accessibilityLabel={`Change date, currently ${value ? formatMediumDate(value) : 'not set'}`}
        style={({ pressed, hovered }: PressState) => [
          styles.field,
          hovered && styles.fieldHover,
          pressed && styles.fieldPressed,
        ]}>
        <Feather name="calendar" size={18} color={colors.brand} />
        <Text style={styles.fieldText}>{value ? formatMediumDate(value) : 'Pick a date'}</Text>
        <Feather name="chevron-down" size={18} color={colors.textMuted} />
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable style={styles.overlay} onPress={() => setOpen(false)}>
          <Pressable style={styles.card} onPress={(e) => e.stopPropagation()}>
            <View style={styles.header}>
              <Pressable onPress={() => shiftMonth(-1)} hitSlop={10} style={styles.navArrow}>
                <Feather name="chevron-left" size={22} color={colors.brand} />
              </Pressable>
              <Text style={styles.monthLabel}>
                {monthName(viewMonth)} {viewMonth.getFullYear()}
              </Text>
              <Pressable onPress={() => shiftMonth(1)} hitSlop={10} style={styles.navArrow}>
                <Feather name="chevron-right" size={22} color={colors.brand} />
              </Pressable>
            </View>

            <View style={styles.weekdayRow}>
              {WEEKDAY_LETTERS.map((l, i) => (
                <Text key={i} style={styles.weekdayCell}>
                  {l}
                </Text>
              ))}
            </View>

            {monthMatrix(viewMonth).map((week, wi) => (
              <View key={wi} style={styles.weekRow}>
                {week.map((day) => {
                  const inMonth = day.getMonth() === viewMonth.getMonth();
                  const isSel = selected != null && isSameDay(day, selected);
                  const isToday = isSameDay(day, today);
                  return (
                    <Pressable
                      key={toISODate(day)}
                      onPress={() => pick(day)}
                      accessibilityRole="button"
                      style={({ hovered }: PressState) => [styles.dayCell, hovered && styles.dayHover]}>
                      <View style={[styles.dayInner, isSel && styles.daySelected]}>
                        <Text
                          style={[
                            styles.dayText,
                            !inMonth && styles.dayFaint,
                            isToday && !isSel && styles.dayToday,
                            isSel && styles.daySelectedText,
                          ]}>
                          {day.getDate()}
                        </Text>
                      </View>
                    </Pressable>
                  );
                })}
              </View>
            ))}

            <Pressable
              onPress={() => pick(new Date())}
              style={({ hovered }: PressState) => [styles.todayBtn, hovered && styles.todayBtnHover]}>
              <Text style={styles.todayBtnText}>Jump to today</Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    borderWidth: 1.5,
    borderColor: colors.hairline,
    borderRadius: radius.md,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    backgroundColor: colors.surface,
    cursor: 'pointer',
  },
  fieldHover: { borderColor: colors.brand },
  fieldPressed: { backgroundColor: colors.brandSoft },
  fieldText: { ...type.bodyMedium, flex: 1 },

  overlay: {
    flex: 1,
    backgroundColor: 'rgba(26,26,30,0.45)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: space.xl,
  },
  card: {
    width: '100%',
    maxWidth: 340,
    backgroundColor: colors.surface,
    borderRadius: radius.xl,
    padding: space.lg,
    ...shadow.card,
  },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: space.md },
  navArrow: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: colors.brandSoft,
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
  },
  monthLabel: { ...type.title, fontSize: 18 },

  weekdayRow: { flexDirection: 'row', marginBottom: space.xs },
  weekdayCell: { flex: 1, textAlign: 'center', fontFamily: fonts.bold, fontSize: 11, color: colors.textMuted },

  weekRow: { flexDirection: 'row' },
  dayCell: { flex: 1, alignItems: 'center', paddingVertical: 3, cursor: 'pointer' },
  dayHover: { opacity: 0.7 },
  dayInner: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  daySelected: { backgroundColor: colors.brand },
  dayText: { fontFamily: fonts.semibold, fontSize: 14, color: colors.text },
  dayFaint: { color: colors.textFaint },
  dayToday: { color: colors.brand, fontFamily: fonts.extrabold },
  daySelectedText: { color: colors.onBrand, fontFamily: fonts.bold },

  todayBtn: {
    marginTop: space.md,
    paddingVertical: space.sm,
    borderRadius: radius.pill,
    alignItems: 'center',
    cursor: 'pointer',
  },
  todayBtnHover: { backgroundColor: colors.brandSoft },
  todayBtnText: { fontFamily: fonts.bold, fontSize: 14, color: colors.brand },
});

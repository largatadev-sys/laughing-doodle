import { useCallback, useMemo, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { AppHeader } from '@/components/AppHeader';
import { Eyebrow, FadeInView, Scroll, StackedTallyBar, TallyBar } from '@/components/ui';
import type { PressState } from '@/components/ui/press';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import {
  addDays,
  endOfMonth,
  formatDuration,
  formatHours,
  isSameDay,
  monthMatrix,
  monthName,
  startOfMonth,
  startOfWeek,
  toISODate,
  weekdayShort,
} from '@/lib/datetime';
import type { EntryResponse } from '@/lib/types';
import { colorForName, colors, fonts, radius, space, TAB_BAR_CLEARANCE, type } from '@/theme';

type CalMode = 'week' | 'month';
const WEEKDAY_LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

interface DayLoad {
  total: number;
  people: { name: string; color: string; minutes: number }[];
}

function aggregateByDay(entries: EntryResponse[]): Map<string, DayLoad> {
  const map = new Map<string, DayLoad>();
  for (const e of entries) {
    let day = map.get(e.entryDate);
    if (!day) {
      day = { total: 0, people: [] };
      map.set(e.entryDate, day);
    }
    day.total += e.durationMin;
    const person = day.people.find((p) => p.name === e.authorName);
    if (person) person.minutes += e.durationMin;
    else day.people.push({ name: e.authorName, color: colorForName(e.authorName), minutes: e.durationMin });
  }
  for (const day of map.values()) day.people.sort((a, b) => b.minutes - a.minutes);
  return map;
}

export default function Calendar() {
  const { session, logout } = useAuth();
  const [view, setView] = useState<CalMode>('week');
  const [anchor, setAnchor] = useState(() => new Date());
  const [entries, setEntries] = useState<EntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const range = useMemo(() => {
    if (view === 'week') {
      const start = startOfWeek(anchor);
      return { start, end: addDays(start, 6) };
    }
    return { start: startOfMonth(anchor), end: endOfMonth(anchor) };
  }, [view, anchor]);

  useFocusEffect(
    useCallback(() => {
      if (!session) return;
      let cancelled = false;
      setError(null);
      apiClient
        .listEntries({ from: toISODate(range.start), to: toISODate(range.end) }, session.token)
        .then((result) => {
          if (!cancelled) setEntries(result);
        })
        .catch((e: unknown) => {
          if (cancelled) return;
          if (e instanceof UnauthorizedError) return logout();
          setError(e instanceof Error ? e.message : 'Could not load the calendar.');
        });
      return () => {
        cancelled = true;
      };
    }, [session, logout, range.start, range.end]),
  );

  const byDay = useMemo(() => aggregateByDay(entries ?? []), [entries]);
  const periodTotal = useMemo(
    () => (entries ?? []).reduce((s, e) => s + e.durationMin, 0),
    [entries],
  );
  const busiestDay = useMemo(() => {
    let m = 0;
    for (const d of byDay.values()) m = Math.max(m, d.total);
    return m;
  }, [byDay]);

  function shift(dir: -1 | 1) {
    setAnchor((prev) =>
      view === 'week'
        ? addDays(prev, dir * 7)
        : new Date(prev.getFullYear(), prev.getMonth() + dir, 1),
    );
  }

  function openDay(date: Date) {
    router.push({ pathname: '/day/[date]', params: { date: toISODate(date) } });
  }

  const weekDays = useMemo(() => {
    const start = startOfWeek(anchor);
    return Array.from({ length: 7 }, (_, i) => addDays(start, i));
  }, [anchor]);

  const periodLabel =
    view === 'week'
      ? `${monthName(range.start).slice(0, 3)} ${range.start.getDate()} – ${range.end.getDate()}`
      : `${monthName(anchor)} ${anchor.getFullYear()}`;

  return (
    <View style={styles.screen}>
      <AppHeader />

      <Scroll
        contentContainerStyle={[styles.content, { paddingBottom: TAB_BAR_CLEARANCE }]}
        showsVerticalScrollIndicator={false}>
        {/* View switch */}
        <View style={styles.segment}>
          {(['week', 'month'] as CalMode[]).map((v) => (
            <Pressable
              key={v}
              onPress={() => setView(v)}
              accessibilityRole="button"
              accessibilityState={{ selected: view === v }}
              style={({ hovered }: PressState) => [
                styles.segmentItem,
                view === v && styles.segmentItemActive,
                hovered && view !== v && styles.segmentItemHover,
              ]}>
              <Text style={[styles.segmentText, view === v && styles.segmentTextActive]}>
                {v === 'week' ? 'Week' : 'Month'}
              </Text>
            </Pressable>
          ))}
        </View>

        {/* Period nav */}
        <View style={styles.periodNav}>
          <Pressable
            onPress={() => shift(-1)}
            hitSlop={10}
            style={({ hovered }: PressState) => [styles.navArrow, hovered && styles.navArrowHover]}>
            <Feather name="chevron-left" size={22} color={colors.brand} />
          </Pressable>
          <Pressable onPress={() => setAnchor(new Date())} hitSlop={8} style={styles.periodLabelBtn}>
            <Text style={styles.periodLabel}>{periodLabel}</Text>
          </Pressable>
          <Pressable
            onPress={() => shift(1)}
            hitSlop={10}
            style={({ hovered }: PressState) => [styles.navArrow, hovered && styles.navArrowHover]}>
            <Feather name="chevron-right" size={22} color={colors.brand} />
          </Pressable>
        </View>

        <View style={styles.totalRow}>
          <Eyebrow>{view === 'week' ? 'This week' : 'This month'}</Eyebrow>
          <Text style={styles.totalValue}>{formatDuration(periodTotal)} logged</Text>
        </View>

        {error && <Text style={styles.error}>{error}</Text>}
        {!error && entries === null && (
          <ActivityIndicator color={colors.brand} style={{ marginTop: space.xxl }} />
        )}

        {!error && entries !== null && view === 'week' && (
          <WeekView days={weekDays} byDay={byDay} busiest={busiestDay} onOpenDay={openDay} />
        )}

        {!error && entries !== null && view === 'month' && (
          <FadeInView>
            <MonthView anchor={anchor} byDay={byDay} busiest={busiestDay} onOpenDay={openDay} />
          </FadeInView>
        )}
      </Scroll>
    </View>
  );
}

// ── Week: 7 stacked day-loads, honest duration-as-load ───────────────────────────────────
function WeekView({
  days,
  byDay,
  busiest,
  onOpenDay,
}: {
  days: Date[];
  byDay: Map<string, DayLoad>;
  busiest: number;
  onOpenDay: (d: Date) => void;
}) {
  const today = new Date();
  return (
    <View style={{ gap: space.sm }}>
      {days.map((day, i) => {
        const load = byDay.get(toISODate(day));
        const isToday = isSameDay(day, today);
        return (
          <FadeInView key={toISODate(day)} delay={i * 45} distance={14}>
            <Pressable
              onPress={() => onOpenDay(day)}
              style={({ hovered }: PressState) => [
                styles.weekRow,
                isToday && styles.weekRowToday,
                hovered && styles.weekRowHover,
              ]}>
              <View style={styles.weekDayCol}>
              <Text style={[styles.weekWeekday, isToday && styles.todayText]}>
                {weekdayShort(day)}
              </Text>
              <Text style={[styles.weekDate, isToday && styles.todayText]}>{day.getDate()}</Text>
            </View>
            <View style={styles.weekLoadCol}>
              {load ? (
                <StackedTallyBar
                  segments={load.people.map((p) => ({ minutes: p.minutes, color: p.color }))}
                  max={Math.max(busiest, 1)}
                  height={12}
                />
              ) : (
                <View style={styles.weekEmptyBar} />
              )}
            </View>
              <Text style={[styles.weekTotal, !load && styles.weekTotalEmpty]}>
                {load ? formatHours(load.total) : '—'}
              </Text>
            </Pressable>
          </FadeInView>
        );
      })}
    </View>
  );
}

// ── Month: date grid with per-day red load bars ──────────────────────────────────────────
function MonthView({
  anchor,
  byDay,
  busiest,
  onOpenDay,
}: {
  anchor: Date;
  byDay: Map<string, DayLoad>;
  busiest: number;
  onOpenDay: (d: Date) => void;
}) {
  const today = new Date();
  const weeks = monthMatrix(anchor);
  return (
    <View style={styles.month}>
      <View style={styles.monthHeaderRow}>
        {WEEKDAY_LETTERS.map((l, i) => (
          <Text key={i} style={styles.monthHeaderCell}>
            {l}
          </Text>
        ))}
      </View>
      {weeks.map((week, wi) => (
        <View key={wi} style={styles.monthWeekRow}>
          {week.map((day) => {
            const inMonth = day.getMonth() === anchor.getMonth();
            const load = byDay.get(toISODate(day));
            const isToday = isSameDay(day, today);
            return (
              <Pressable
                key={toISODate(day)}
                onPress={() => onOpenDay(day)}
                style={styles.monthCell}>
                <View style={[styles.monthDateWrap, isToday && styles.monthDateToday]}>
                  <Text
                    style={[
                      styles.monthDate,
                      !inMonth && styles.monthDateFaint,
                      isToday && styles.monthDateTodayText,
                    ]}>
                    {day.getDate()}
                  </Text>
                </View>
                {load && inMonth ? (
                  <>
                    <TallyBar
                      minutes={load.total}
                      max={Math.max(busiest, 1)}
                      color={colors.brand}
                      height={4}
                      track={false}
                      style={styles.monthBar}
                    />
                    <Text style={styles.monthHours}>{formatHours(load.total)}</Text>
                  </>
                ) : (
                  <View style={styles.monthBarPlaceholder} />
                )}
              </Pressable>
            );
          })}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { paddingHorizontal: space.lg, paddingTop: space.lg },

  segment: {
    flexDirection: 'row',
    backgroundColor: colors.hairline,
    borderRadius: radius.pill,
    padding: 3,
  },
  segmentItem: { flex: 1, paddingVertical: 9, borderRadius: radius.pill, alignItems: 'center', cursor: 'pointer' },
  segmentItemActive: { backgroundColor: colors.brand },
  segmentItemHover: { backgroundColor: colors.brandSoft },
  segmentText: { fontFamily: fonts.bold, fontSize: 14, color: colors.textMuted },
  segmentTextActive: { color: colors.onBrand },

  periodNav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: space.lg,
  },
  navArrow: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: colors.brandSoft,
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
  },
  navArrowHover: { backgroundColor: colors.salmon },
  periodLabelBtn: { cursor: 'pointer' },
  periodLabel: { ...type.title, textAlign: 'center' },

  totalRow: { marginTop: space.lg, marginBottom: space.md, gap: 2 },
  totalValue: { ...type.body, fontFamily: fonts.semibold },

  error: { ...type.body, color: colors.brand, marginTop: space.lg },

  // Week rows
  weekRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.hairline,
    paddingVertical: space.md,
    paddingHorizontal: space.md,
    cursor: 'pointer',
  },
  weekRowToday: { borderColor: colors.brand, borderWidth: 1.5 },
  weekRowHover: { backgroundColor: colors.brandSoft },
  weekDayCol: { width: 40, alignItems: 'center' },
  weekWeekday: { fontFamily: fonts.semibold, fontSize: 11, color: colors.textMuted, textTransform: 'uppercase' },
  weekDate: { fontFamily: fonts.extrabold, fontSize: 18, color: colors.text },
  todayText: { color: colors.brand },
  weekLoadCol: { flex: 1, justifyContent: 'center' },
  weekEmptyBar: { height: 12, borderRadius: 6, backgroundColor: colors.hairline, opacity: 0.5 },
  weekTotal: { ...type.number, width: 52, textAlign: 'right' },
  weekTotalEmpty: { color: colors.textFaint },

  // Month grid
  month: { marginTop: space.xs },
  monthHeaderRow: { flexDirection: 'row', marginBottom: space.sm },
  monthHeaderCell: {
    flex: 1,
    textAlign: 'center',
    fontFamily: fonts.bold,
    fontSize: 11,
    color: colors.textMuted,
  },
  monthWeekRow: { flexDirection: 'row' },
  monthCell: { flex: 1, alignItems: 'center', paddingVertical: 6, gap: 3, minHeight: 58, cursor: 'pointer' },
  monthDateWrap: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  monthDateToday: { backgroundColor: colors.brand },
  monthDate: { fontFamily: fonts.semibold, fontSize: 14, color: colors.text },
  monthDateFaint: { color: colors.textFaint },
  monthDateTodayText: { color: colors.onBrand, fontFamily: fonts.bold },
  monthBar: { width: '62%' },
  monthHours: { fontFamily: fonts.semibold, fontSize: 9.5, color: colors.textMuted },
  monthBarPlaceholder: { height: 4 },
});

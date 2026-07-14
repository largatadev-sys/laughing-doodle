import { useCallback, useMemo, useState } from 'react';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { EntryCard } from '@/components/EntryCard';
import { Avatar, Card, Eyebrow, Scroll, TallyBar } from '@/components/ui';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { confirmDelete } from '@/lib/confirm';
import { formatDuration, parseISODate, weekdayLong, monthName } from '@/lib/datetime';
import type { EntryResponse } from '@/lib/types';
import { colorForName, colors, fonts, space, tabularNums, type } from '@/theme';

export default function DayDetail() {
  const { date } = useLocalSearchParams<{ date: string }>();
  const { session, logout } = useAuth();
  const insets = useSafeAreaInsets();
  const [entries, setEntries] = useState<EntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!session || !date) return;
      let cancelled = false;
      setError(null);
      apiClient
        .listEntries({ from: date, to: date }, session.token)
        .then((result) => {
          if (!cancelled) setEntries(result);
        })
        .catch((e: unknown) => {
          if (cancelled) return;
          if (e instanceof UnauthorizedError) return logout();
          setError(e instanceof Error ? e.message : 'Could not load this day.');
        });
      return () => {
        cancelled = true;
      };
    }, [session, logout, date]),
  );

  function goToEdit(item: EntryResponse) {
    router.push({
      pathname: '/[id]/edit',
      params: {
        id: String(item.id),
        entryDate: item.entryDate,
        durationMin: String(item.durationMin),
        description: item.description,
      },
    });
  }

  async function handleDelete(item: EntryResponse) {
    if (!(await confirmDelete()) || !session) return;
    try {
      await apiClient.deleteEntry(item.id, session.token);
      setEntries((prev) => (prev ? prev.filter((e) => e.id !== item.id) : prev));
    } catch (e) {
      if (e instanceof UnauthorizedError) return logout();
      setError(e instanceof Error ? e.message : 'Could not delete that entry.');
    }
  }

  const day = date ? parseISODate(date) : new Date();
  const total = useMemo(() => (entries ?? []).reduce((s, e) => s + e.durationMin, 0), [entries]);

  const byPerson = useMemo(() => {
    const map = new Map<string, number>();
    for (const e of entries ?? []) map.set(e.authorName, (map.get(e.authorName) ?? 0) + e.durationMin);
    return [...map.entries()].map(([name, minutes]) => ({ name, minutes })).sort((a, b) => b.minutes - a.minutes);
  }, [entries]);

  // Order by createdAt (newest first) — creation order, NOT updatedAt: an edit changes the card's
  // "edited · …" label but must not reorder rows out from under the reader.
  const sorted = useMemo(
    () => [...(entries ?? [])].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1)),
    [entries],
  );

  return (
    <View style={styles.screen}>
      <View style={[styles.topBar, { paddingTop: insets.top + space.sm }]}>
        <Pressable onPress={() => router.back()} hitSlop={10} style={styles.backBtn}>
          <Feather name="chevron-left" size={24} color={colors.brand} />
        </Pressable>
        <Text style={styles.topTitle}>
          {monthName(day).slice(0, 3)} {day.getDate()}
        </Text>
        <View style={styles.backBtn} />
      </View>

      <Scroll contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Eyebrow>{weekdayLong(day)}</Eyebrow>
        <Text style={styles.title}>
          {monthName(day)} {day.getDate()}
        </Text>

        {error && <Text style={styles.error}>{error}</Text>}
        {!error && entries === null && (
          <ActivityIndicator color={colors.brand} style={{ marginTop: space.xxl }} />
        )}

        {!error && entries !== null && (
          <>
            {byPerson.length > 0 && (
              <Card style={styles.summary}>
                <View style={styles.summaryTop}>
                  <Eyebrow>Team total</Eyebrow>
                  <Text style={styles.summaryTotal}>{formatDuration(total)}</Text>
                </View>
                <View style={styles.people}>
                  {byPerson.map((p) => (
                    <View key={p.name} style={styles.personRow}>
                      <Avatar name={p.name} size={28} />
                      <Text style={styles.personName} numberOfLines={1}>
                        {p.name}
                      </Text>
                      <TallyBar
                        minutes={p.minutes}
                        max={Math.max(total, 1)}
                        color={colorForName(p.name)}
                        height={8}
                        style={styles.personBar}
                      />
                      <Text style={styles.personMin}>{formatDuration(p.minutes)}</Text>
                    </View>
                  ))}
                </View>
              </Card>
            )}

            {entries.length === 0 ? (
              <View style={styles.empty}>
                <Feather name="calendar" size={28} color={colors.textFaint} />
                <Text style={styles.emptyText}>No time logged on this day.</Text>
              </View>
            ) : (
              <View style={styles.list}>
                {sorted.map((item) => (
                  <EntryCard
                    key={item.id}
                    entry={item}
                    isOwn={item.userId === session?.user.id}
                    onEdit={goToEdit}
                    onDelete={handleDelete}
                  />
                ))}
              </View>
            )}
          </>
        )}
      </Scroll>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    paddingBottom: space.sm,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.hairline,
  },
  backBtn: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  topTitle: { ...type.heading },
  content: { padding: space.lg, gap: space.sm },
  title: { ...type.display, marginBottom: space.sm },
  error: { ...type.body, color: colors.brand, marginTop: space.lg },

  summary: { gap: space.md, marginBottom: space.sm },
  summaryTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  summaryTotal: { fontFamily: fonts.extrabold, fontSize: 20, color: colors.text, fontVariant: tabularNums },
  people: { gap: space.md },
  personRow: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  personName: { ...type.bodyMedium, width: 92 },
  personBar: { flex: 1 },
  personMin: { ...type.caption, fontFamily: fonts.bold, color: colors.text, width: 56, textAlign: 'right' },

  list: { gap: space.md, marginTop: space.sm },
  empty: { alignItems: 'center', gap: space.sm, paddingVertical: space.xxl },
  emptyText: { ...type.body, color: colors.textMuted },
});

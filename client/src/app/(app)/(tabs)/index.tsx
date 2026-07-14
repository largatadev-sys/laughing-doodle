import { useCallback, useMemo, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { AppHeader } from '@/components/AppHeader';
import { EntryCard } from '@/components/EntryCard';
import { Avatar, Card, Eyebrow, FadeInView, Scroll, TallyBar } from '@/components/ui';
import type { PressState } from '@/components/ui/press';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { confirmDelete } from '@/lib/confirm';
import {
  addDays,
  dayLabel,
  formatDuration,
  parseISODate,
  startOfWeek,
  toISODate,
} from '@/lib/datetime';
import type { EntryResponse } from '@/lib/types';
import { colors, radius, space, TAB_BAR_CLEARANCE, type } from '@/theme';

const FEED_DAYS = 14;
const WEEK_TARGET_MIN = 40 * 60;

interface DayGroup {
  date: string;
  items: EntryResponse[];
}

// Newest first, then grouped into days (each day's own entries newest-first too).
function groupByDay(entries: EntryResponse[]): DayGroup[] {
  const sorted = [...entries].sort((a, b) => {
    if (a.entryDate !== b.entryDate) return a.entryDate < b.entryDate ? 1 : -1;
    return a.createdAt < b.createdAt ? 1 : -1;
  });
  const groups: DayGroup[] = [];
  for (const entry of sorted) {
    const last = groups[groups.length - 1];
    if (last && last.date === entry.entryDate) last.items.push(entry);
    else groups.push({ date: entry.entryDate, items: [entry] });
  }
  return groups;
}

export default function HomeFeed() {
  const { session, logout } = useAuth();
  const [entries, setEntries] = useState<EntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!session) return;
      let cancelled = false;
      setError(null);
      const to = toISODate(new Date());
      const from = toISODate(addDays(new Date(), -(FEED_DAYS - 1)));
      apiClient
        .listEntries({ from, to }, session.token)
        .then((result) => {
          if (!cancelled) setEntries(result);
        })
        .catch((e: unknown) => {
          if (cancelled) return;
          if (e instanceof UnauthorizedError) return logout();
          setError(e instanceof Error ? e.message : 'Could not load the team feed.');
        });
      return () => {
        cancelled = true;
      };
    }, [session, logout]),
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

  const groups = useMemo(() => (entries ? groupByDay(entries) : []), [entries]);

  const myWeekMin = useMemo(() => {
    if (!entries || !session) return 0;
    const weekStart = startOfWeek(new Date());
    return entries
      .filter((e) => e.userId === session.user.id && parseISODate(e.entryDate) >= weekStart)
      .reduce((sum, e) => sum + e.durationMin, 0);
  }, [entries, session]);

  const firstName = session?.user.name.split(' ')[0] ?? 'there';

  return (
    <View style={styles.screen}>
      <AppHeader />

      <Scroll
        contentContainerStyle={[styles.content, { paddingBottom: TAB_BAR_CLEARANCE }]}
        showsVerticalScrollIndicator={false}>
        <FadeInView style={styles.greetBlock}>
          <Text style={styles.greeting}>Hi, {firstName}</Text>
          <Text style={styles.subGreeting}>Here’s what the team has been logging.</Text>
        </FadeInView>

        {/* Your week — the personal meter atop the social feed */}
        <FadeInView delay={70}>
          <Card style={styles.weekCard}>
            <View style={styles.weekTop}>
              <Eyebrow>This week</Eyebrow>
              <Text style={type.caption}>of {formatDuration(WEEK_TARGET_MIN)}</Text>
            </View>
            <Text style={styles.weekTotal}>{formatDuration(myWeekMin)}</Text>
            <TallyBar
              minutes={myWeekMin}
              max={WEEK_TARGET_MIN}
              color={colors.brand}
              height={10}
              delay={220}
              style={{ marginTop: space.sm }}
            />
          </Card>
        </FadeInView>

        {/* Compose prompt — the social "what did you do" box */}
        <FadeInView delay={140}>
          <Pressable
            onPress={() => router.push('/new')}
            accessibilityRole="button"
            style={({ pressed, hovered }: PressState) => [
              styles.composePressable,
              hovered && styles.composeHover,
              pressed && styles.composePressed,
            ]}>
            <Card style={styles.composeCard}>
              {session && <Avatar name={session.user.name} size={36} />}
              <Text style={styles.composePlaceholder}>What did you work on?</Text>
              <View style={styles.composePlus}>
                <Feather name="plus" size={20} color={colors.onBrand} />
              </View>
            </Card>
          </Pressable>
        </FadeInView>

        <View style={styles.feedHeader}>
          <Eyebrow>Team activity</Eyebrow>
        </View>

        {error && <Text style={styles.error}>{error}</Text>}

        {!error && entries === null && (
          <ActivityIndicator color={colors.brand} style={{ marginTop: space.xxl }} />
        )}

        {!error && entries !== null && groups.length === 0 && (
          <View style={styles.empty}>
            <Feather name="coffee" size={30} color={colors.textFaint} />
            <Text style={styles.emptyTitle}>Quiet fortnight</Text>
            <Text style={styles.emptyBody}>
              No time logged in the last two weeks. Tap + to log the first entry.
            </Text>
          </View>
        )}

        {groups.map((group, gi) => (
          <FadeInView key={group.date} delay={Math.min(200 + gi * 70, 520)}>
            <View style={styles.dayHeader}>
              <Text style={styles.dayLabel}>{dayLabel(group.date)}</Text>
              <Text style={styles.dayTotal}>
                {formatDuration(group.items.reduce((s, e) => s + e.durationMin, 0))}
              </Text>
            </View>
            <View style={{ gap: space.md }}>
              {group.items.map((item) => (
                <EntryCard
                  key={item.id}
                  entry={item}
                  isOwn={item.userId === session?.user.id}
                  onEdit={goToEdit}
                  onDelete={handleDelete}
                />
              ))}
            </View>
          </FadeInView>
        ))}
      </Scroll>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { paddingHorizontal: space.lg, paddingTop: space.lg, gap: space.md },
  greetBlock: { gap: 2 },
  greeting: { ...type.display },
  subGreeting: { ...type.body, color: colors.textMuted },

  weekCard: { gap: 2, marginTop: space.xs },
  weekTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  weekTotal: { fontFamily: type.numberLarge.fontFamily, fontSize: 30, color: colors.text },

  composePressable: { borderRadius: radius.lg, cursor: 'pointer' },
  composeHover: { opacity: 0.94 },
  composePressed: { transform: [{ scale: 0.99 }] },
  composeCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingVertical: space.md,
  },
  composePlaceholder: { ...type.body, color: colors.textMuted, flex: 1 },
  composePlus: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.brand,
    alignItems: 'center',
    justifyContent: 'center',
  },

  feedHeader: { marginTop: space.sm },
  dayHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginTop: space.sm,
    marginBottom: space.md,
  },
  dayLabel: { ...type.title },
  dayTotal: { ...type.number, color: colors.textMuted },

  error: { ...type.body, color: colors.brand, marginTop: space.lg },
  empty: { alignItems: 'center', gap: space.sm, paddingVertical: space.xxl },
  emptyTitle: { ...type.heading },
  emptyBody: { ...type.body, color: colors.textMuted, textAlign: 'center', paddingHorizontal: space.xl },
});

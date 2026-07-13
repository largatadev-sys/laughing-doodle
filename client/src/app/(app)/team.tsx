import { useCallback, useState } from 'react';
import { router, Stack, useFocusEffect } from 'expo-router';
import { ActivityIndicator, Pressable, SectionList, StyleSheet, Text, View } from 'react-native';

import { EntryRow } from '@/components/EntryRow';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { confirmDelete } from '@/lib/confirm';
import type { EntryResponse } from '@/lib/types';

function toISODate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function startOfWeek(date: Date): Date {
  const day = date.getDay(); // 0 = Sunday .. 6 = Saturday
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(date);
  monday.setDate(date.getDate() + diffToMonday);
  monday.setHours(0, 0, 0, 0);
  return monday;
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function formatRangeLabel(weekStart: Date): string {
  const weekEnd = addDays(weekStart, 6);
  const format = (d: Date) => d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  return `${format(weekStart)} – ${format(weekEnd)}`;
}

interface Section {
  title: string;
  data: EntryResponse[];
}

function groupByDate(entries: EntryResponse[]): Section[] {
  const sections: Section[] = [];
  for (const entry of entries) {
    const last = sections[sections.length - 1];
    if (last && last.title === entry.entryDate) {
      last.data.push(entry);
    } else {
      sections.push({ title: entry.entryDate, data: [entry] });
    }
  }
  return sections;
}

export default function TeamFeed() {
  const { session, logout } = useAuth();
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()));
  const [entries, setEntries] = useState<EntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!session) {
        return;
      }
      let cancelled = false;
      setError(null);
      const from = toISODate(weekStart);
      const to = toISODate(addDays(weekStart, 6));
      apiClient
        .listEntries({ from, to }, session.token)
        .then((result) => {
          if (!cancelled) {
            setEntries(result);
          }
        })
        .catch((e: unknown) => {
          if (cancelled) {
            return;
          }
          if (e instanceof UnauthorizedError) {
            logout();
            return;
          }
          setError(e instanceof Error ? e.message : 'Failed to load the team feed.');
        });
      return () => {
        cancelled = true;
      };
    }, [session, logout, weekStart]),
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
    const confirmed = await confirmDelete();
    if (!confirmed || !session) {
      return;
    }
    try {
      await apiClient.deleteEntry(item.id, session.token);
      setEntries((prev) => (prev ? prev.filter((e) => e.id !== item.id) : prev));
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        logout();
        return;
      }
      setError(e instanceof Error ? e.message : 'Failed to delete entry.');
    }
  }

  const sections = entries ? groupByDate(entries) : [];

  return (
    <>
      <Stack.Screen options={{ title: 'Team Feed' }} />
      <View style={styles.weekNav}>
        <Pressable onPress={() => setWeekStart((prev) => addDays(prev, -7))} hitSlop={8}>
          <Text style={styles.weekNavArrow}>{'‹'}</Text>
        </Pressable>
        <Text style={styles.weekLabel}>{formatRangeLabel(weekStart)}</Text>
        <Pressable onPress={() => setWeekStart((prev) => addDays(prev, 7))} hitSlop={8}>
          <Text style={styles.weekNavArrow}>{'›'}</Text>
        </Pressable>
      </View>

      {error && (
        <View style={styles.center}>
          <Text style={styles.error}>{error}</Text>
        </View>
      )}

      {!error && entries === null && (
        <View style={styles.center}>
          <ActivityIndicator />
        </View>
      )}

      {!error && entries !== null && (
        <SectionList
          contentContainerStyle={styles.list}
          sections={sections}
          keyExtractor={(item) => String(item.id)}
          stickySectionHeadersEnabled={false}
          ListEmptyComponent={
            <View style={styles.center}>
              <Text>No entries this week.</Text>
            </View>
          }
          renderSectionHeader={({ section }) => (
            <Text style={styles.sectionHeader}>{section.title}</Text>
          )}
          ItemSeparatorComponent={() => <View style={styles.itemSeparator} />}
          renderItem={({ item }) => (
            <EntryRow
              entry={item}
              currentUserId={session?.user.id}
              showAuthor
              showDate={false}
              onEdit={goToEdit}
              onDelete={handleDelete}
            />
          )}
        />
      )}
    </>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  error: {
    color: '#b91c1c',
  },
  weekNav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 24,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e7eb',
  },
  weekNavArrow: {
    fontSize: 20,
    fontWeight: '600',
    color: '#1d4ed8',
    paddingHorizontal: 12,
  },
  weekLabel: {
    fontWeight: '600',
    minWidth: 120,
    textAlign: 'center',
  },
  list: {
    padding: 16,
    gap: 12,
  },
  sectionHeader: {
    fontWeight: '700',
    fontSize: 16,
    marginTop: 12,
    marginBottom: 4,
  },
  itemSeparator: {
    height: 8,
  },
});

import { useCallback, useMemo, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { AppHeader } from '@/components/AppHeader';
import { EntryCard } from '@/components/EntryCard';
import { Avatar, Card, Eyebrow, FadeInView, Scroll, StatusPill } from '@/components/ui';
import type { PressState } from '@/components/ui/press';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { confirmDelete } from '@/lib/confirm';
import { formatDuration, parseISODate, startOfWeek } from '@/lib/datetime';
import type { EntryResponse } from '@/lib/types';
import { colors, fonts, radius, space, tabularNums, TAB_BAR_CLEARANCE, type } from '@/theme';

export default function Profile() {
  const { session, logout } = useAuth();
  const [entries, setEntries] = useState<EntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!session) return;
      let cancelled = false;
      setError(null);
      apiClient
        .listEntries({ userId: session.user.id }, session.token)
        .then((result) => {
          if (!cancelled) setEntries(result);
        })
        .catch((e: unknown) => {
          if (cancelled) return;
          if (e instanceof UnauthorizedError) return logout();
          setError(e instanceof Error ? e.message : 'Could not load your entries.');
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

  const sorted = useMemo(
    () =>
      [...(entries ?? [])].sort((a, b) => {
        if (a.entryDate !== b.entryDate) return a.entryDate < b.entryDate ? 1 : -1;
        return a.createdAt < b.createdAt ? 1 : -1;
      }),
    [entries],
  );
  const weekMin = useMemo(() => {
    const weekStart = startOfWeek(new Date());
    return (entries ?? [])
      .filter((e) => parseISODate(e.entryDate) >= weekStart)
      .reduce((s, e) => s + e.durationMin, 0);
  }, [entries]);
  const totalMin = useMemo(() => (entries ?? []).reduce((s, e) => s + e.durationMin, 0), [entries]);

  if (!session) return null;

  return (
    <View style={styles.screen}>
      <AppHeader />

      <Scroll
        contentContainerStyle={[styles.content, { paddingBottom: TAB_BAR_CLEARANCE }]}
        showsVerticalScrollIndicator={false}>
        <FadeInView style={styles.identity}>
          <Avatar name={session.user.name} size={64} />
          <View style={styles.identityText}>
            <Text style={styles.name}>{session.user.name}</Text>
            <Text style={styles.username}>@{session.user.username}</Text>
          </View>
          <StatusPill label={session.user.role === 'ADMIN' ? 'admin' : 'member'} tone="neutral" />
        </FadeInView>

        <FadeInView delay={70} style={styles.stats}>
          <Card style={styles.stat}>
            <Eyebrow>This week</Eyebrow>
            <Text style={styles.statValue}>{formatDuration(weekMin)}</Text>
          </Card>
          <Card style={styles.stat}>
            <Eyebrow>All time</Eyebrow>
            <Text style={styles.statValue}>{formatDuration(totalMin)}</Text>
          </Card>
        </FadeInView>

        <FadeInView delay={110} style={styles.account}>
          <Pressable
            onPress={() => router.push('/change-name')}
            accessibilityRole="button"
            style={({ hovered }: PressState) => [styles.accountRow, hovered && styles.accountRowHover]}>
            <View style={styles.accountLeft}>
              <View style={styles.accountIcon}>
                <Feather name="user" size={16} color={colors.brand} />
              </View>
              <Text style={styles.accountLabel}>Change name</Text>
            </View>
            <Feather name="chevron-right" size={20} color={colors.textMuted} />
          </Pressable>
          <Pressable
            onPress={() => router.push('/change-password')}
            accessibilityRole="button"
            style={({ hovered }: PressState) => [styles.accountRow, hovered && styles.accountRowHover]}>
            <View style={styles.accountLeft}>
              <View style={styles.accountIcon}>
                <Feather name="lock" size={16} color={colors.brand} />
              </View>
              <Text style={styles.accountLabel}>Change password</Text>
            </View>
            <Feather name="chevron-right" size={20} color={colors.textMuted} />
          </Pressable>
        </FadeInView>

        <View style={styles.sectionHeader}>
          <Eyebrow>Your entries</Eyebrow>
          <Text style={type.caption}>{entries?.length ?? 0} total</Text>
        </View>

        {error && <Text style={styles.error}>{error}</Text>}
        {!error && entries === null && (
          <ActivityIndicator color={colors.brand} style={{ marginTop: space.xl }} />
        )}

        {!error && entries !== null && sorted.length === 0 && (
          <View style={styles.empty}>
            <Feather name="clock" size={28} color={colors.textFaint} />
            <Text style={styles.emptyText}>You haven’t logged any time yet.</Text>
          </View>
        )}

        <View style={styles.list}>
          {sorted.map((item, i) => (
            <FadeInView key={item.id} delay={Math.min(i * 40, 400)} distance={14}>
              <EntryCard entry={item} isOwn onEdit={goToEdit} onDelete={handleDelete} />
            </FadeInView>
          ))}
        </View>
      </Scroll>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { paddingHorizontal: space.lg, paddingTop: space.lg, gap: space.lg },

  identity: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  identityText: { flex: 1, gap: 2 },
  name: { ...type.title },
  username: { ...type.body, color: colors.textMuted },

  stats: { flexDirection: 'row', gap: space.md },
  stat: { flex: 1, gap: space.xs },
  statValue: { fontFamily: fonts.extrabold, fontSize: 22, color: colors.text, fontVariant: tabularNums },

  account: { gap: space.sm },
  accountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    paddingVertical: space.md,
    paddingHorizontal: space.lg,
    cursor: 'pointer',
  },
  accountRowHover: { backgroundColor: colors.brandSoft },
  accountLeft: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  accountIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.brandSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  accountLabel: { ...type.bodyMedium, fontFamily: fonts.bold },

  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  error: { ...type.body, color: colors.brand },
  empty: { alignItems: 'center', gap: space.sm, paddingVertical: space.xl },
  emptyText: { ...type.body, color: colors.textMuted },
  list: { gap: space.md },
});

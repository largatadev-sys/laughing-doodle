import { useCallback, useState } from 'react';
import { router, Stack, useFocusEffect } from 'expo-router';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { EntryRow } from '@/components/EntryRow';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { confirmDelete } from '@/lib/confirm';
import type { EntryResponse } from '@/lib/types';

export default function MyEntries() {
  const { session, logout } = useAuth();
  const [entries, setEntries] = useState<EntryResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!session) {
        return;
      }
      let cancelled = false;
      setError(null);
      apiClient
        .listEntries({ userId: session.user.id }, session.token)
        .then((result) => {
          if (!cancelled) {
            setEntries(result);
          }
        })
        .catch((e: unknown) => {
          if (cancelled) {
            return;
          }
          // An expired/invalid token: end the session and let the root layout's
          // Stack.Protected guard redirect to /login, instead of showing an error
          // the user has no way to act on.
          if (e instanceof UnauthorizedError) {
            logout();
            return;
          }
          setError(e instanceof Error ? e.message : 'Failed to load entries.');
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

  const screenOptions = (
    <Stack.Screen
      options={{
        headerLeft: () => (
          <Pressable onPress={() => router.push('/new')} hitSlop={8}>
            <Text style={styles.newLink}>+ New</Text>
          </Pressable>
        ),
        headerRight: () => (
          <View style={styles.headerRight}>
            <Pressable onPress={() => router.push('/team')} hitSlop={8}>
              <Text style={styles.newLink}>Team</Text>
            </Pressable>
            <Pressable onPress={() => logout()} hitSlop={8}>
              <Text style={styles.newLink}>Log out</Text>
            </Pressable>
          </View>
        ),
      }}
    />
  );

  if (error) {
    return (
      <View style={styles.center}>
        {screenOptions}
        <Text style={styles.error}>{error}</Text>
      </View>
    );
  }

  if (entries === null) {
    return (
      <View style={styles.center}>
        {screenOptions}
        <ActivityIndicator />
      </View>
    );
  }

  return (
    <>
      {screenOptions}
      <FlatList
        contentContainerStyle={styles.list}
        data={entries}
        keyExtractor={(item) => String(item.id)}
        ListEmptyComponent={
          <View style={styles.center}>
            <Text>No entries yet.</Text>
          </View>
        }
        renderItem={({ item }) => (
          <EntryRow
            entry={item}
            currentUserId={session?.user.id}
            onEdit={goToEdit}
            onDelete={handleDelete}
          />
        )}
      />
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
  list: {
    padding: 16,
    gap: 12,
  },
  newLink: {
    color: '#1d4ed8',
    fontWeight: '600',
  },
  headerRight: {
    flexDirection: 'row',
    gap: 16,
  },
});

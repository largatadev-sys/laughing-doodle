import { useCallback, useState } from 'react';
import { router, Stack, useFocusEffect } from 'expo-router';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import type { EntryResponse } from '@/lib/types';

function confirmDelete(): Promise<boolean> {
  const message = 'Delete this entry? This cannot be undone.';
  if (Platform.OS === 'web') {
    return Promise.resolve(window.confirm(message));
  }
  return new Promise((resolve) => {
    Alert.alert('Delete entry', message, [
      { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
      { text: 'Delete', style: 'destructive', onPress: () => resolve(true) },
    ]);
  });
}

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
          <View style={styles.row}>
            <Text style={styles.date}>{item.entryDate}</Text>
            <Text style={styles.duration}>{item.durationMin} min</Text>
            <Text style={styles.description}>{item.description}</Text>
            {item.userId === session?.user.id && (
              <View style={styles.actions}>
                <Pressable onPress={() => goToEdit(item)} hitSlop={8}>
                  <Text style={styles.actionLink}>Edit</Text>
                </Pressable>
                <Pressable onPress={() => handleDelete(item)} hitSlop={8}>
                  <Text style={styles.actionLinkDestructive}>Delete</Text>
                </Pressable>
              </View>
            )}
          </View>
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
  row: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    padding: 12,
    gap: 4,
  },
  date: {
    fontWeight: '600',
  },
  duration: {
    color: '#374151',
  },
  description: {
    color: '#111827',
  },
  actions: {
    flexDirection: 'row',
    gap: 16,
    marginTop: 8,
  },
  actionLink: {
    color: '#1d4ed8',
    fontWeight: '600',
  },
  actionLinkDestructive: {
    color: '#b91c1c',
    fontWeight: '600',
  },
  newLink: {
    color: '#1d4ed8',
    fontWeight: '600',
  },
});

import { useCallback, useState } from 'react';
import { useFocusEffect } from 'expo-router';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';

import { apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import type { EntryResponse } from '@/lib/types';

export default function MyEntries() {
  const { session } = useAuth();
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
          if (!cancelled) {
            setError(e instanceof Error ? e.message : 'Failed to load entries.');
          }
        });
      return () => {
        cancelled = true;
      };
    }, [session]),
  );

  if (error) {
    return (
      <View style={styles.center}>
        <Text style={styles.error}>{error}</Text>
      </View>
    );
  }

  if (entries === null) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  return (
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
        </View>
      )}
    />
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
});

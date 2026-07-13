import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { EntryResponse } from '@/lib/types';

interface EntryRowProps {
  entry: EntryResponse;
  currentUserId?: number;
  showAuthor?: boolean;
  showDate?: boolean;
  onEdit?: (entry: EntryResponse) => void;
  onDelete?: (entry: EntryResponse) => void;
}

export function EntryRow({
  entry,
  currentUserId,
  showAuthor = false,
  showDate = true,
  onEdit,
  onDelete,
}: EntryRowProps) {
  const isOwn = entry.userId === currentUserId;
  return (
    <View style={styles.row}>
      {showAuthor && <Text style={styles.author}>{entry.authorName}</Text>}
      {showDate && <Text style={styles.date}>{entry.entryDate}</Text>}
      <Text style={styles.duration}>{entry.durationMin} min</Text>
      <Text style={styles.description}>{entry.description}</Text>
      {isOwn && (onEdit || onDelete) && (
        <View style={styles.actions}>
          {onEdit && (
            <Pressable onPress={() => onEdit(entry)} hitSlop={8}>
              <Text style={styles.actionLink}>Edit</Text>
            </Pressable>
          )}
          {onDelete && (
            <Pressable onPress={() => onDelete(entry)} hitSlop={8}>
              <Text style={styles.actionLinkDestructive}>Delete</Text>
            </Pressable>
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    padding: 12,
    gap: 4,
  },
  author: {
    fontWeight: '600',
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
});

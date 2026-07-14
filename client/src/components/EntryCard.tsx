import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { Avatar, Card, StatusPill, TallyBar } from '@/components/ui';
import { formatDuration, relativeTime, WORKDAY_MIN } from '@/lib/datetime';
import type { EntryResponse } from '@/lib/types';
import { colorForName, colors, fonts, space, tabularNums, type } from '@/theme';

interface EntryCardProps {
  entry: EntryResponse;
  isOwn: boolean;
  onEdit?: (entry: EntryResponse) => void;
  onDelete?: (entry: EntryResponse) => void;
}

// One logged chunk of work, framed as a social-feed post: who, how long (as a number *and*
// a tally bar in their colour), how recently, and what they did. Own entries get the red
// accent outline + edit/delete; others are read-only (INV-2, expressed in the UI).
export function EntryCard({ entry, isOwn, onEdit, onDelete }: EntryCardProps) {
  const hue = colorForName(entry.authorName);
  return (
    <Card accent={isOwn}>
      <View style={styles.header}>
        <Avatar name={entry.authorName} size={42} />
        <View style={styles.headerText}>
          <View style={styles.nameRow}>
            <Text style={type.heading} numberOfLines={1}>
              {entry.authorName}
            </Text>
            {isOwn && <StatusPill label="you" />}
          </View>
          <Text style={type.caption}>logged · {relativeTime(entry.createdAt)}</Text>
        </View>
        <Text style={styles.duration}>{formatDuration(entry.durationMin)}</Text>
      </View>

      <TallyBar
        minutes={entry.durationMin}
        max={WORKDAY_MIN}
        color={hue}
        height={8}
        style={{ marginTop: space.md }}
      />

      <Text style={[type.body, styles.description]}>{entry.description}</Text>

      {isOwn && (onEdit || onDelete) && (
        <View style={styles.actions}>
          {onEdit && (
            <Pressable onPress={() => onEdit(entry)} hitSlop={8} style={styles.actionBtn}>
              <Feather name="edit-2" size={14} color={colors.brand} />
              <Text style={styles.actionText}>Edit</Text>
            </Pressable>
          )}
          {onDelete && (
            <Pressable onPress={() => onDelete(entry)} hitSlop={8} style={styles.actionBtn}>
              <Feather name="trash-2" size={14} color={colors.brand} />
              <Text style={styles.actionText}>Delete</Text>
            </Pressable>
          )}
        </View>
      )}
    </Card>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', gap: space.md },
  headerText: { flex: 1, gap: 1 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  duration: {
    fontFamily: fonts.extrabold,
    fontSize: 18,
    color: colors.text,
    fontVariant: tabularNums,
  },
  description: { marginTop: space.md },
  actions: {
    flexDirection: 'row',
    gap: space.xl,
    marginTop: space.md,
    paddingTop: space.md,
    borderTopWidth: 1,
    borderTopColor: colors.hairline,
  },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  actionText: { fontFamily: fonts.bold, fontSize: 13, color: colors.brand },
});

import { StyleSheet, Text, View, type ViewStyle } from 'react-native';

import { initials } from '@/lib/datetime';
import { colorForName, colors, fonts, readableTextOn } from '@/theme';

interface AvatarProps {
  name: string;
  size?: number;
  ring?: boolean; // white ring, for overlapping stacks
  style?: ViewStyle;
}

// A person's identity chip: initials on their deterministic hue. The same name is always
// the same colour, in the feed and in the calendar — the app's only source of colour.
export function Avatar({ name, size = 40, ring = false, style }: AvatarProps) {
  const hue = colorForName(name);
  return (
    <View
      style={[
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: hue,
          alignItems: 'center',
          justifyContent: 'center',
        },
        ring && { borderWidth: 2, borderColor: colors.surface },
        style,
      ]}>
      <Text style={{ color: readableTextOn(hue), fontFamily: fonts.bold, fontSize: size * 0.38 }}>
        {initials(name)}
      </Text>
    </View>
  );
}

// Overlapping member row (like Largata's trip-member circles). Shows up to `max`, then +N.
export function AvatarStack({ names, size = 28, max = 4 }: { names: string[]; size?: number; max?: number }) {
  const shown = names.slice(0, max);
  const overflow = names.length - shown.length;
  return (
    <View style={styles.stackRow}>
      {shown.map((name, i) => (
        <View key={`${name}-${i}`} style={{ marginLeft: i === 0 ? 0 : -size * 0.32 }}>
          <Avatar name={name} size={size} ring />
        </View>
      ))}
      {overflow > 0 && (
        <View
          style={[
            styles.overflow,
            { width: size, height: size, borderRadius: size / 2, marginLeft: -size * 0.32 },
          ]}>
          <Text style={{ color: colors.textMuted, fontFamily: fonts.bold, fontSize: size * 0.34 }}>
            +{overflow}
          </Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  stackRow: { flexDirection: 'row', alignItems: 'center' },
  overflow: {
    backgroundColor: colors.bg,
    borderWidth: 2,
    borderColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

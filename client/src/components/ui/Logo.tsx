import { Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { colors, fonts } from '@/theme';

// The Largata wordmark: paper-plane mark + extrabold red wordmark. Feather's "send" glyph
// stands in for the brand's paper-plane until a real logo asset is dropped in.
export function Logo({ size = 22, tagline }: { size?: number; tagline?: string }) {
  return (
    <View style={{ alignItems: 'center', gap: 2 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 7 }}>
        <Feather name="send" size={size} color={colors.brand} style={{ marginTop: -2 }} />
        <Text
          style={{
            fontFamily: fonts.extrabold,
            fontSize: size * 1.05,
            color: colors.brand,
            letterSpacing: -0.6,
          }}>
          Largata
        </Text>
      </View>
      {tagline && (
        <Text
          style={{
            fontFamily: fonts.semibold,
            fontSize: 11,
            letterSpacing: 2,
            textTransform: 'uppercase',
            color: colors.textMuted,
          }}>
          {tagline}
        </Text>
      )}
    </View>
  );
}

import { Platform, StyleSheet, View } from 'react-native';

import { colors } from '@/theme';

// This is a mobile-native app being run as a web app for now. On a wide browser, a full-bleed
// phone layout looks broken — so on web we frame the app as a centered phone-width column on a
// muted backdrop. On native this is a no-op passthrough.
export function WebFrame({ children }: { children: React.ReactNode }) {
  if (Platform.OS !== 'web') return <>{children}</>;
  return (
    <View style={styles.backdrop}>
      <View style={styles.column}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', backgroundColor: '#E4E4E7' },
  column: {
    flex: 1,
    width: '100%',
    maxWidth: 460,
    backgroundColor: colors.bg,
    // Reads as a device against the backdrop (react-native-web maps this to box-shadow).
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: 0 },
  },
});

import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Logo } from '@/components/ui';
import type { PressState } from '@/components/ui/press';
import { useAuth } from '@/lib/auth';
import { colors, fonts, radius, space } from '@/theme';

// Persistent top bar on the tab screens: Largata mark on the left, log out on the right.
// Opening your profile is the bottom Profile tab's job, so the header's action is log out.
// `right` can override the default control.
export function AppHeader({ right }: { right?: React.ReactNode }) {
  const insets = useSafeAreaInsets();
  const { session, logout } = useAuth();

  return (
    <View style={[styles.bar, { paddingTop: insets.top + space.sm }]}>
      <Logo size={20} />
      {right ??
        (session ? (
          <Pressable
            onPress={() => logout()}
            accessibilityRole="button"
            accessibilityLabel="Log out"
            style={({ hovered }: PressState) => [styles.logout, hovered && styles.logoutHover]}>
            <Feather name="log-out" size={16} color={colors.brand} />
            <Text style={styles.logoutText}>Log out</Text>
          </Pressable>
        ) : (
          <View />
        ))}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.lg,
    paddingBottom: space.sm,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.hairline,
  },
  logout: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: colors.brand,
    cursor: 'pointer',
  },
  logoutHover: { backgroundColor: colors.brandSoft },
  logoutText: { fontFamily: fonts.bold, fontSize: 13, color: colors.brand },
});

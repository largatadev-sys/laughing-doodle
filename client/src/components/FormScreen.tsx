import { KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Scroll } from '@/components/ui';
import { colors, radius, space, type } from '@/theme';

// Shell for the compose/edit modals: drag grabber, a Largata header with a close control,
// and a keyboard-aware scroll body. Keeps both form screens visually identical.
export function FormScreen({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  const insets = useSafeAreaInsets();
  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.screen}>
      <View style={[styles.grabberWrap, { paddingTop: Math.max(insets.top - 4, 8) }]}>
        <View style={styles.grabber} />
      </View>
      <View style={styles.header}>
        <View style={styles.headerText}>
          <Text style={styles.title}>{title}</Text>
          {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
        </View>
        <Pressable
          onPress={() => router.back()}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel="Close"
          style={styles.close}>
          <Feather name="x" size={20} color={colors.text} />
        </Pressable>
      </View>
      <Scroll
        contentContainerStyle={[styles.body, { paddingBottom: insets.bottom + space.xxl }]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}>
        {children}
      </Scroll>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.surface },
  grabberWrap: { alignItems: 'center', paddingBottom: space.sm },
  grabber: { width: 40, height: 4, borderRadius: 2, backgroundColor: colors.hairline },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    paddingHorizontal: space.lg,
    paddingBottom: space.lg,
  },
  headerText: { flex: 1, gap: 2 },
  title: { ...type.display, fontSize: 24 },
  subtitle: { ...type.body, color: colors.textMuted },
  close: {
    width: 36,
    height: 36,
    borderRadius: radius.pill,
    backgroundColor: colors.bg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: { paddingHorizontal: space.lg },
});

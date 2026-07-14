import { Stack } from 'expo-router';

import { colors } from '@/theme';

// The authenticated stack: the tab shell, plus screens that present ABOVE the tabs — the
// compose/edit forms as modals, and the day-detail agenda as a pushed screen.
export default function AppLayout() {
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: colors.bg },
      }}>
      <Stack.Screen name="(tabs)" />
      <Stack.Screen name="new" options={{ presentation: 'modal' }} />
      <Stack.Screen name="[id]/edit" options={{ presentation: 'modal' }} />
      <Stack.Screen name="change-name" options={{ presentation: 'modal' }} />
      <Stack.Screen name="change-password" options={{ presentation: 'modal' }} />
      <Stack.Screen name="day/[date]" />
    </Stack>
  );
}

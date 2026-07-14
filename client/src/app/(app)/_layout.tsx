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
        // Forms slide up from the bottom (native modal feel), consistently timed so every
        // sheet in the app rises at the same pace.
        animationDuration: 300,
      }}>
      <Stack.Screen name="(tabs)" />
      <Stack.Screen name="new" options={{ presentation: 'modal' }} />
      <Stack.Screen name="[id]/edit" options={{ presentation: 'modal' }} />
      <Stack.Screen name="change-name" options={{ presentation: 'modal' }} />
      <Stack.Screen name="change-password" options={{ presentation: 'modal' }} />
      {/* Tapping a day drills *deeper* into that date's agenda — a slide-from-right carries
          that "one level down" relationship (and back-swipes right to return). */}
      <Stack.Screen name="day/[date]" options={{ animation: 'ios_from_right' }} />
    </Stack>
  );
}

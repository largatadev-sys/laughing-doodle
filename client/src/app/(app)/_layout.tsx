import { Stack } from 'expo-router';

import { DashboardProvider } from '@/lib/dashboard';
import { ReportsProvider } from '@/lib/reports';
import { colors } from '@/theme';

// The authenticated stack: the tab shell, plus screens that present ABOVE the tabs — the
// compose/edit forms as modals, and the day-detail agenda as a pushed screen.
export default function AppLayout() {
  return (
    // The inbox fetch lives above the tabs: the Reports badge has to be live on every
    // authenticated screen, not only once the Inbox tab has been opened.
    <ReportsProvider>
      {/* One Largata-usage fetch for the Reports-tab strip and the Dashboard screen. It never
          polls on its own — whichever of the two is focused drives it. */}
      <DashboardProvider>
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
        {/* A report opens one level down from the inbox — the same drill-in as a day. */}
        <Stack.Screen name="report/[id]" options={{ animation: 'ios_from_right' }} />
        {/* The Dashboard is one level down from the Reports tab's strip — the same drill-in. */}
        <Stack.Screen name="dashboard" options={{ animation: 'ios_from_right' }} />
        {/* A Handoff opens one level down from the inbox it was made in — the same drill-in. */}
        <Stack.Screen name="handoff/[id]" options={{ animation: 'ios_from_right' }} />
        {/* The Handoffs list is one level down from the Reports tab — the same drill-in. */}
        <Stack.Screen name="handoffs" options={{ animation: 'ios_from_right' }} />
      </Stack>
      </DashboardProvider>
    </ReportsProvider>
  );
}

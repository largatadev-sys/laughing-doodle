import { router } from 'expo-router';
import { Tabs, TabList, TabTrigger } from 'expo-router/ui';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppHeader } from '@/components/AppHeader';
import { MorphingPill } from '@/components/nav/MorphingPill';
import { CenterButton, TabBarButton } from '@/components/nav/TabBar';
import {
  TabBarActionProvider,
  useCurrentTabBarAction,
  useCurrentTabBarCenter,
} from '@/components/nav/TabBarAction';
import { AnimatedTabSlot } from '@/components/nav/TabTransition';
import { useReports } from '@/lib/reports';

// Headless tabs (expo-router/ui) so the bar can be a fully custom floating red pill with a
// non-tab centre disc in the middle. The hidden <TabList> registers the routes; the
// visible triggers reference them by name and forward `isFocused` for the active halo.
export default function TabsLayout() {
  return (
    <TabBarActionProvider>
      <TabsShell />
    </TabBarActionProvider>
  );
}

// The pill can be lent to the focused screen for one action (MorphingPill / TabBarAction);
// the triggers stay mounted underneath so navigation comes back exactly as it was.
function TabsShell() {
  const insets = useSafeAreaInsets();
  const { newCount } = useReports();
  const action = useCurrentTabBarAction();
  const center = useCurrentTabBarCenter();
  return (
    <Tabs>
      {/* One header for every tab, outside the transition: it is the same on all four, so
          switching tabs must not fade or drift it. Only the content beneath animates. */}
      <AppHeader />
      <AnimatedTabSlot />

      <View
        style={[styles.barWrap, { bottom: Math.max(insets.bottom, 10) }]}
        pointerEvents="box-none">
        <MorphingPill action={action} discIcon={center?.icon ?? 'plus'}>
          <TabTrigger name="home" asChild>
            <TabBarButton icon="home" a11y="Home" />
          </TabTrigger>
          <TabTrigger name="calendar" asChild>
            <TabBarButton icon="calendar" a11y="Calendar" />
          </TabTrigger>
          {/* Log time by default; the focused tab can lend it its own action (Reports: hand off). */}
          <CenterButton override={center} onCompose={() => router.push('/new')} />
          <TabTrigger name="reports" asChild>
            {/* The badge counts reports nobody has triaged yet, so incoming Largata
                feedback is visible from any screen — see ReportsProvider. */}
            <TabBarButton icon="alert-circle" a11y="Reports" badge={newCount} />
          </TabTrigger>
          <TabTrigger name="profile" asChild>
            <TabBarButton icon="user" a11y="Profile" />
          </TabTrigger>
        </MorphingPill>
      </View>

      <TabList style={styles.hiddenList}>
        <TabTrigger name="home" href="/" />
        <TabTrigger name="calendar" href="/calendar" />
        <TabTrigger name="reports" href="/reports" />
        <TabTrigger name="profile" href="/profile" />
      </TabList>
    </Tabs>
  );
}

const styles = StyleSheet.create({
  barWrap: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  hiddenList: { display: 'none' },
});

import { router } from 'expo-router';
import { Tabs, TabList, TabSlot, TabTrigger } from 'expo-router/ui';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ComposeButton, TabBarButton } from '@/components/nav/TabBar';
import { colors, radius, shadow, space, TAB_BAR_HEIGHT } from '@/theme';

// Headless tabs (expo-router/ui) so the bar can be a fully custom floating red pill with a
// non-tab compose button in the middle. The hidden <TabList> registers the routes; the
// visible triggers reference them by name and forward `isFocused` for the active halo.
export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs>
      <TabSlot />

      <View
        style={[styles.barWrap, { bottom: Math.max(insets.bottom, 10) }]}
        pointerEvents="box-none">
        <View style={styles.pill}>
          <TabTrigger name="home" asChild>
            <TabBarButton icon="home" a11y="Home" />
          </TabTrigger>
          <TabTrigger name="calendar" asChild>
            <TabBarButton icon="calendar" a11y="Calendar" />
          </TabTrigger>
          <ComposeButton onPress={() => router.push('/new')} />
          <TabTrigger name="profile" asChild>
            <TabBarButton icon="user" a11y="Profile" />
          </TabTrigger>
        </View>
      </View>

      <TabList style={styles.hiddenList}>
        <TabTrigger name="home" href="/" />
        <TabTrigger name="calendar" href="/calendar" />
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
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    height: TAB_BAR_HEIGHT,
    paddingHorizontal: space.sm,
    borderRadius: radius.pill,
    backgroundColor: colors.brand,
    ...shadow.floating,
  },
  hiddenList: { display: 'none' },
});

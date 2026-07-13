import { Stack } from 'expo-router';
import { Pressable, Text } from 'react-native';

import { useAuth } from '@/lib/auth';

export default function AppLayout() {
  const { logout } = useAuth();

  return (
    <Stack
      screenOptions={{
        title: 'My Entries',
        headerRight: () => (
          <Pressable onPress={() => logout()} hitSlop={8}>
            <Text style={{ color: '#1d4ed8', fontWeight: '600' }}>Log out</Text>
          </Pressable>
        ),
      }}
    />
  );
}

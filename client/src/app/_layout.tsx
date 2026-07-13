import { Stack } from 'expo-router';

import { AuthProvider, useAuth } from '@/lib/auth';

export default function RootLayout() {
  return (
    <AuthProvider>
      <RootNavigator />
    </AuthProvider>
  );
}

function RootNavigator() {
  const { session, isLoading } = useAuth();

  if (isLoading) {
    return null;
  }

  return (
    <Stack>
      <Stack.Protected guard={!!session}>
        <Stack.Screen name="(app)" options={{ headerShown: false }} />
      </Stack.Protected>
      <Stack.Protected guard={!session}>
        <Stack.Screen name="login" options={{ title: 'Log in' }} />
      </Stack.Protected>
    </Stack>
  );
}

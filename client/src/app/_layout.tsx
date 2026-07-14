import { useEffect } from 'react';
import { Stack } from 'expo-router';
import { useFonts } from 'expo-font';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { Platform } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { WebFrame } from '@/components/WebFrame';
import { AuthProvider, useAuth } from '@/lib/auth';
import { colors, fontAssets } from '@/theme';

// Keep the splash up until the brand font is ready, so the first paint is already Largata —
// no flash of a system-font fallback.
SplashScreen.preventAutoHideAsync();

// Mobile-native app running on web for now. Rather than hide the scrollbars (native feel) or
// keep the chunky browser default, give the web build a thin, subtle, on-brand scrollbar — a
// real scroll affordance that still reads as Largata (muted thumb, brand-red on hover).
function useWebScrollbarStyle() {
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof document === 'undefined') return;
    const el = document.createElement('style');
    el.textContent = [
      '*::-webkit-scrollbar{width:8px;height:8px}',
      '*::-webkit-scrollbar-track{background:transparent}',
      '*::-webkit-scrollbar-thumb{background:rgba(26,26,30,0.2);border-radius:8px}',
      '*::-webkit-scrollbar-thumb:hover{background:rgba(245,51,63,0.55)}',
      '*{scrollbar-width:thin;scrollbar-color:rgba(26,26,30,0.22) transparent}',
    ].join('');
    document.head.appendChild(el);
    return () => el.remove();
  }, []);
}

export default function RootLayout() {
  const [loaded, error] = useFonts(fontAssets());
  useWebScrollbarStyle();

  useEffect(() => {
    if (loaded || error) {
      SplashScreen.hideAsync();
    }
  }, [loaded, error]);

  if (!loaded && !error) {
    return null;
  }

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <AuthProvider>
          <StatusBar style="dark" />
          <WebFrame>
            <RootNavigator />
          </WebFrame>
        </AuthProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

function RootNavigator() {
  const { session, isLoading } = useAuth();

  if (isLoading) {
    return null;
  }

  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: colors.bg },
        // The auth boundary is a change of *state*, not a step deeper — so cross it with a
        // fade rather than a spatial slide. Signing in/out swaps the whole world calmly.
        animation: 'fade',
        animationDuration: 260,
      }}>
      <Stack.Protected guard={!!session}>
        <Stack.Screen name="(app)" />
      </Stack.Protected>
      <Stack.Protected guard={!session}>
        <Stack.Screen name="login" />
      </Stack.Protected>
    </Stack>
  );
}

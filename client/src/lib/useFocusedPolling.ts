import { useCallback } from 'react';
import { useFocusEffect } from 'expo-router';
import { AppState } from 'react-native';

/**
 * A screen that keeps itself current while someone is looking at it — and only then.
 *
 * `load` is called once, loudly, when the screen gains focus, and then quietly every
 * `intervalMs` while the screen stays focused AND the app is in the foreground. "Quietly" is
 * the caller's promise: a quiet load that fails must leave what is on screen exactly as it
 * was, with no banner, because a network blip must not interrupt someone mid-task. The
 * `quiet` flag is how this hook asks for that.
 *
 * The timer exists only while focused and foregrounded: no polling from a backgrounded app,
 * and none at all from any other screen (the Story 20 fixes, kept in this one place). By
 * default, coming back to the foreground only restarts the clock — the Inbox's provider
 * already refetches on foreground; `refetchOnForeground` adds a quiet load for callers whose
 * provider does not.
 *
 * `load` must be stable (wrap it in useCallback): a new identity restarts the focus effect.
 */
export function useFocusedPolling(
  load: (options: { quiet: boolean }) => void,
  intervalMs: number,
  { refetchOnForeground = false }: { refetchOnForeground?: boolean } = {},
): void {
  useFocusEffect(
    useCallback(() => {
      load({ quiet: false });

      let timer: ReturnType<typeof setInterval> | null = null;
      const start = () => {
        if (timer === null) timer = setInterval(() => load({ quiet: true }), intervalMs);
      };
      const stop = () => {
        if (timer !== null) {
          clearInterval(timer);
          timer = null;
        }
      };

      if (AppState.currentState === 'active') start();
      const sub = AppState.addEventListener('change', (state) => {
        if (state === 'active') {
          if (refetchOnForeground && timer === null) load({ quiet: true });
          start();
        } else {
          stop();
        }
      });

      return () => {
        stop();
        sub.remove();
      };
    }, [load, intervalMs, refetchOnForeground]),
  );
}

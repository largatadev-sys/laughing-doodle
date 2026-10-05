import { useEffect, useState } from 'react';

/**
 * The current time, re-read every `intervalMs` and whenever `resetKey` changes. For relative
 * labels ("updated 3m ago") that must keep ageing even when nothing else re-renders — which
 * is exactly when a refresh has been failing and the age is the thing the reader needs to
 * see. Pass the data's own timestamp as `resetKey` so fresh data is never labelled against a
 * clock read before it arrived.
 */
export function useNow(intervalMs: number, resetKey?: unknown): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    // Re-read on the next tick rather than synchronously in the effect body, which would
    // render twice for every new value of resetKey.
    const reread = setTimeout(() => setNow(new Date()), 0);
    const timer = setInterval(() => setNow(new Date()), intervalMs);
    return () => {
      clearTimeout(reread);
      clearInterval(timer);
    };
  }, [intervalMs, resetKey]);
  return now;
}

import { useEffect, useState } from 'react';
import { Platform } from 'react-native';

import { useAuth } from './auth';

// Same rule as apiClient: empty base = same origin (the prod single-origin image), absolute in dev.
const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? '';

type State =
  | { status: 'loading'; uri: null }
  | { status: 'ready'; uri: string }
  | { status: 'error'; uri: null };

/**
 * An image URI usable by `<Image source>` for a screenshot that sits behind bearer auth.
 *
 * A plain `src` cannot carry an Authorization header, so on web the bytes are fetched and
 * handed over as an object URL (revoked on unmount so blobs don't accumulate). On native,
 * `Image` accepts request headers directly, so the URL is used as-is and RN does the fetch —
 * no need to pull megabytes of image through JS.
 */
export function useAuthedImage(path: string): State & { headers?: Record<string, string> } {
  const { session } = useAuth();
  const token = session?.token ?? null;
  const url = `${BASE_URL}${path}`;

  const [state, setState] = useState<State>({ status: 'loading', uri: null });

  useEffect(() => {
    if (Platform.OS !== 'web' || !token) return;

    let objectUrl: string | null = null;
    let cancelled = false;

    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then((response) => {
        if (!response.ok) throw new Error(`status ${response.status}`);
        return response.blob();
      })
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setState({ status: 'ready', uri: objectUrl });
      })
      .catch(() => {
        if (!cancelled) setState({ status: 'error', uri: null });
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url, token]);

  if (Platform.OS !== 'web') {
    return token
      ? { status: 'ready', uri: url, headers: { Authorization: `Bearer ${token}` } }
      : { status: 'error', uri: null };
  }

  return state;
}

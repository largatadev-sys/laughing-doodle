import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

import type { UserSummary } from './types';

const TOKEN_KEY = 'timesheet.session.token';
const USER_KEY = 'timesheet.session.user';

async function getItem(key: string): Promise<string | null> {
  if (Platform.OS === 'web') {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }
  return SecureStore.getItemAsync(key);
}

async function setItem(key: string, value: string): Promise<void> {
  if (Platform.OS === 'web') {
    localStorage.setItem(key, value);
    return;
  }
  await SecureStore.setItemAsync(key, value);
}

async function deleteItem(key: string): Promise<void> {
  if (Platform.OS === 'web') {
    localStorage.removeItem(key);
    return;
  }
  await SecureStore.deleteItemAsync(key);
}

export interface StoredSession {
  token: string;
  user: UserSummary;
}

// Token and user are stored as separate keys, not one JSON blob, to stay well
// under SecureStore's ~2048-byte practical per-key ceiling on iOS.
export async function loadSession(): Promise<StoredSession | null> {
  const [token, userJson] = await Promise.all([getItem(TOKEN_KEY), getItem(USER_KEY)]);
  if (!token || !userJson) {
    return null;
  }
  try {
    return { token, user: JSON.parse(userJson) as UserSummary };
  } catch {
    return null;
  }
}

export async function saveSession(session: StoredSession): Promise<void> {
  await Promise.all([
    setItem(TOKEN_KEY, session.token),
    setItem(USER_KEY, JSON.stringify(session.user)),
  ]);
}

export async function clearSession(): Promise<void> {
  await Promise.all([deleteItem(TOKEN_KEY), deleteItem(USER_KEY)]);
}

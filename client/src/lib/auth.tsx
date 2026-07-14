import { createContext, use, useEffect, useState, type PropsWithChildren } from 'react';

import { apiClient } from './apiClient';
import { clearSession, loadSession, saveSession, type StoredSession } from './tokenStorage';
import type { UserSummary } from './types';

interface AuthContextValue {
  session: StoredSession | null;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  updateUser: (user: UserSummary) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = use(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return value;
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadSession().then((stored) => {
      setSession(stored);
      setIsLoading(false);
    });
  }, []);

  async function login(username: string, password: string) {
    const response = await apiClient.login(username, password);
    const newSession: StoredSession = { token: response.token, user: response.user };
    await saveSession(newSession);
    setSession(newSession);
  }

  async function logout() {
    await clearSession();
    setSession(null);
  }

  // Refresh the stored profile in place (same token) — e.g. after a display-name change.
  async function updateUser(user: UserSummary) {
    setSession((prev) => {
      if (!prev) return prev;
      const next: StoredSession = { token: prev.token, user };
      void saveSession(next);
      return next;
    });
  }

  return (
    <AuthContext value={{ session, isLoading, login, logout, updateUser }}>{children}</AuthContext>
  );
}

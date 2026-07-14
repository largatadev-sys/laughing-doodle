import type {
  CreateEntryRequest,
  EntryResponse,
  LoginResponse,
  UpdateEntryRequest,
  UserSummary,
} from './types';

// Empty/unset → a relative base, so `${BASE_URL}/api/...` calls the same origin that served
// the app. That's the prod path: the Spring image serves this web bundle and the API together
// (ADR-008), so no CORS and no baked absolute URL. In dev, client/.env sets an absolute URL
// (http://localhost:8080 for the web dev server, a LAN IP for Expo Go). Never `undefined`,
// which would produce "undefined/api/...".
const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? '';

export class ApiError extends Error {
  status: number;
  code: string;
  details?: Record<string, unknown>;

  constructor(status: number, code: string, message: string, details?: Record<string, unknown>) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export class UnauthorizedError extends ApiError {}

interface RequestOptions {
  method?: string;
  body?: unknown;
  token?: string | null;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const code = data?.error?.code ?? 'UNKNOWN';
    const message = data?.error?.message ?? 'Something went wrong. Please try again.';
    const details = data?.error?.details;
    if (response.status === 401) {
      throw new UnauthorizedError(response.status, code, message, details);
    }
    throw new ApiError(response.status, code, message, details);
  }

  return data as T;
}

export const apiClient = {
  login(username: string, password: string): Promise<LoginResponse> {
    return request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: { username, password },
    });
  },

  changePassword(currentPassword: string, newPassword: string, token: string): Promise<void> {
    return request<void>('/api/auth/password', {
      method: 'PUT',
      body: { currentPassword, newPassword },
      token,
    });
  },

  updateName(name: string, token: string): Promise<UserSummary> {
    return request<UserSummary>('/api/auth/name', { method: 'PUT', body: { name }, token });
  },

  listEntries(
    params: { userId?: number; from?: string; to?: string },
    token: string,
  ): Promise<EntryResponse[]> {
    const query = new URLSearchParams();
    if (params.userId !== undefined) query.set('userId', String(params.userId));
    if (params.from) query.set('from', params.from);
    if (params.to) query.set('to', params.to);
    const qs = query.toString();
    return request<EntryResponse[]>(`/api/entries${qs ? `?${qs}` : ''}`, { token });
  },

  createEntry(request_: CreateEntryRequest, token: string): Promise<EntryResponse> {
    return request<EntryResponse>('/api/entries', { method: 'POST', body: request_, token });
  },

  updateEntry(id: number, request_: UpdateEntryRequest, token: string): Promise<EntryResponse> {
    return request<EntryResponse>(`/api/entries/${id}`, {
      method: 'PUT',
      body: request_,
      token,
    });
  },

  deleteEntry(id: number, token: string): Promise<void> {
    return request<void>(`/api/entries/${id}`, { method: 'DELETE', token });
  },
};

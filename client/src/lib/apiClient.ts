import type { EntryResponse, LoginResponse } from './types';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL;

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
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
    if (response.status === 401) {
      throw new UnauthorizedError(response.status, code, message);
    }
    throw new ApiError(response.status, code, message);
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
};

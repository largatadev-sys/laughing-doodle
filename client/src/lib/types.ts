export type Role = 'MEMBER' | 'ADMIN';

export interface UserSummary {
  id: number;
  name: string;
  username: string;
  role: Role;
}

export interface LoginResponse {
  token: string;
  user: UserSummary;
}

export interface EntryResponse {
  id: number;
  userId: number;
  authorName: string;
  entryDate: string;
  durationMin: number;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEntryRequest {
  entryDate: string;
  durationMin: number;
  description: string;
}

export interface UpdateEntryRequest {
  entryDate: string;
  durationMin: number;
  description: string;
}

export interface ErrorEnvelope {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
}

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

// --- Reports inbox (Epic 3) -------------------------------------------------------------
// Feedback from users of the sibling product Largata, relayed server-to-server into worklog
// (ADR-010). A reporter is NOT a worklog user — name/uid are opaque strings that travel on
// the report itself.

export type ReportType = 'problem' | 'idea';
export type ReportPlatform = 'android' | 'ios' | 'web';
export type ReportStatus = 'new' | 'discuss' | 'in_progress' | 'done' | 'dismissed';

export interface ReportResponse {
  id: string;
  type: ReportType;
  description: string;
  reporterName: string;
  reporterUid: string;
  platform: ReportPlatform;
  appVersion: string;
  /** When the reporter hit send. Display and sort key — a retried relay must not reorder. */
  submittedAt: string;
  /** When worklog received it; later than submittedAt whenever delivery was retried. */
  receivedAt: string;
  status: ReportStatus;
  statusChangedBy: number | null;
  statusChangedByName: string | null;
  statusChangedAt: string | null;
  screenshotOrdinals: number[];
}

export interface ErrorEnvelope {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
}

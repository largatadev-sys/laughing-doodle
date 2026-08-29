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

/**
 * A team-authored note on a report (Story 20, ADR-012 as revised). The log is append-only —
 * there is no delete anywhere — and a note's text can be rewritten only by its author, which
 * is why `editedBy` always equals `authorId` in practice. The `editedAt` null-ness is what the
 * UI reads to decide whether to show the "Edited" stamp; the editor's *name* is carried but
 * not rendered, since it would only repeat the author line above it.
 */
export interface ReportNote {
  id: string;
  body: string;
  authorId: number;
  authorName: string;
  createdAt: string;
  editedBy: number | null;
  editedByName: string | null;
  editedAt: string | null;
}

export interface ReportResponse {
  id: string;
  type: ReportType;
  description: string;
  /** Null when the report was filed from a signed-out Largata screen (contract v1.1). */
  reporterName: string | null;
  reporterUid: string | null;
  platform: ReportPlatform;
  appVersion: string;
  /** Where the reporter was when they opened the report flow — an opaque Largata-minted
   *  string; null on reports from builds that don't send it (contract v1.1). */
  screen: string | null;
  /** When the reporter hit send. Display and sort key — a retried relay must not reorder. */
  submittedAt: string;
  /** When worklog received it; later than submittedAt whenever delivery was retried. */
  receivedAt: string;
  status: ReportStatus;
  statusChangedBy: number | null;
  statusChangedByName: string | null;
  statusChangedAt: string | null;
  screenshotOrdinals: number[];
  /** The team's own writing, oldest-first. Never sent to the relay, never seen by a reporter. */
  notes: ReportNote[];
}

export interface ErrorEnvelope {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
}

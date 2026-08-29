import { createContext, use, useCallback, useEffect, useState, type PropsWithChildren } from 'react';
import { AppState } from 'react-native';

import { apiClient, UnauthorizedError } from './apiClient';
import { useAuth } from './auth';
import type { ReportNote, ReportResponse, ReportStatus } from './types';

interface ReportsContextValue {
  /** All reports, newest-first — null until the first fetch resolves. */
  reports: ReportResponse[] | null;
  error: string | null;
  /** Count of untouched reports; what the tab badge shows. */
  newCount: number;
  refresh: () => void;
  /** The same fetch, but a failure is silent-stale: the list keeps what it has and no error
   *  banner appears. For background polling, where a blip must not interrupt triage. */
  refreshQuietly: () => void;
  /** Swap one report in place after a status change, so the list, chips, and badge all
   *  follow from a single write without a round-trip. */
  replaceReport: (report: ReportResponse) => void;
  /** Move a report and write the result back into the shared list. Throws on failure so the
   *  caller can surface it; a 401 logs out here, as everywhere. */
  changeStatus: (reportId: string, status: ReportStatus) => Promise<void>;
  /** Append a note and merge it into the cached report, so the writer sees it land without a
   *  refetch. Throws on failure so the composer can surface it. */
  addNote: (reportId: string, body: string) => Promise<void>;
  /** Rewrite a note's text — the server refuses anyone but its author, and stamps the edit. */
  editNote: (reportId: string, noteId: string, body: string) => Promise<void>;
}

const ReportsContext = createContext<ReportsContextValue | null>(null);

export function useReports(): ReportsContextValue {
  const value = use(ReportsContext);
  if (!value) {
    throw new Error('useReports must be used within a ReportsProvider');
  }
  return value;
}

/**
 * One shared fetch of the inbox, held above the tab bar so the badge and the Inbox screen
 * read the same list. The badge counts `new` from that list — there is no counts endpoint
 * (the volume is trivial), and crucially the count is a property of the DATA, not of whether
 * anyone opened the tab: it clears only when reports are actually triaged out of `new`.
 */
export function ReportsProvider({ children }: PropsWithChildren) {
  const { session, logout } = useAuth();
  const [reports, setReports] = useState<ReportResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const token = session?.token ?? null;

  const fetchReports = useCallback(
    (quiet: boolean) => {
      if (!token) return;
      apiClient
        .listReports({}, token)
        .then((result) => {
          setReports(result);
          setError(null);
        })
        .catch((e: unknown) => {
          if (e instanceof UnauthorizedError) return void logout();
          // A background tick that fails changes nothing on screen: the reports already there
          // are still the truth, and a banner nobody asked for would interrupt triage.
          if (quiet) return;
          setError(e instanceof Error ? e.message : 'Could not load the inbox.');
        });
    },
    [token, logout],
  );

  const refresh = useCallback(() => fetchReports(false), [fetchReports]);
  const refreshQuietly = useCallback(() => fetchReports(true), [fetchReports]);

  // Fetch on sign-in — the badge must be live from any screen, so this does not wait for
  // the Inbox tab to be opened. Sign-out needs no cleanup here: `visibleReports` below is
  // gated on the token, so a stale list can never outlive the session it came from.
  useEffect(() => {
    refresh();
  }, [refresh]);

  // Refetch when the app comes back to the foreground: feedback arrives while it's closed.
  useEffect(() => {
    if (!token) return;
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') refresh();
    });
    return () => sub.remove();
  }, [token, refresh]);

  const replaceReport = useCallback((updated: ReportResponse) => {
    setReports((prev) => (prev ? prev.map((r) => (r.id === updated.id ? updated : r)) : prev));
  }, []);

  const changeStatus = useCallback(
    async (reportId: string, status: ReportStatus) => {
      if (!token) return;
      try {
        replaceReport(await apiClient.updateReportStatus(reportId, status, token));
      } catch (e) {
        if (e instanceof UnauthorizedError) {
          void logout();
          return;
        }
        throw e;
      }
    },
    [token, logout, replaceReport],
  );

  /** Put a note into its report's cached log — appended when it's new, replaced in place when
   *  it's an edit. The log only ever grows: nothing here can remove an entry. */
  const mergeNote = useCallback((reportId: string, note: ReportNote) => {
    setReports((prev) =>
      prev
        ? prev.map((report) => {
            if (report.id !== reportId) return report;
            const existing = report.notes.findIndex((n) => n.id === note.id);
            return {
              ...report,
              notes:
                existing === -1
                  ? [...report.notes, note]
                  : report.notes.map((n) => (n.id === note.id ? note : n)),
            };
          })
        : prev,
    );
  }, []);

  const addNote = useCallback(
    async (reportId: string, body: string) => {
      if (!token) return;
      try {
        mergeNote(reportId, await apiClient.addReportNote(reportId, body, token));
      } catch (e) {
        if (e instanceof UnauthorizedError) {
          void logout();
          return;
        }
        throw e;
      }
    },
    [token, logout, mergeNote],
  );

  const editNote = useCallback(
    async (reportId: string, noteId: string, body: string) => {
      if (!token) return;
      try {
        mergeNote(reportId, await apiClient.editReportNote(reportId, noteId, body, token));
      } catch (e) {
        if (e instanceof UnauthorizedError) {
          void logout();
          return;
        }
        throw e;
      }
    },
    [token, logout, mergeNote],
  );

  // Signed out, there is no inbox — not an empty one. Deriving this (rather than clearing
  // state on logout) means a previous session's reports can never flash on screen.
  const visibleReports = token ? reports : null;
  const newCount = (visibleReports ?? []).filter((r) => r.status === NEW_STATUS).length;

  return (
    <ReportsContext
      value={{
        reports: visibleReports,
        error: token ? error : null,
        newCount,
        refresh,
        refreshQuietly,
        replaceReport,
        changeStatus,
        addNote,
        editNote,
      }}>
      {children}
    </ReportsContext>
  );
}

const NEW_STATUS: ReportStatus = 'new';

// The Handoff text (Story 28). A pure function of (selected Reports, creating Member's name,
// now) to a string — no React, no React Native, no theme, nothing with side effects — so a
// test runner could cover it later without a refactor (spec, "Who writes the text"). Apart from
// the pure lib/plural.ts, only type imports below: they are erased at compile time.
//
// Shape (spec, "Text format"): a header, an index grouped by screen, then one numbered,
// self-contained section per Report — its description quoted verbatim, its facts, and the
// team's Notes. Never included: the reporter's name, screenshots or their count, worklog
// links, any prompt or instruction.
import { reportNoun } from './plural';
import type { ReportNote, ReportResponse, ReportStatus } from './types';

export interface HandoffDraft {
  /** The text exactly as it will be stored and copied. */
  text: string;
  /** The Report ids in the order the text numbers them — what the create call sends. */
  reportIds: string[];
}

// The UI's status labels, verbatim. Duplicated from lib/reportStatus.ts on purpose: that
// module imports the icon set and the theme, which would make this function impure. Keep the
// two in step if a label ever changes.
const STATUS_LABELS: Record<ReportStatus, string> = {
  new: 'New',
  discuss: 'For discussion',
  in_progress: 'In progress',
  done: 'Done',
  dismissed: 'Dismissed',
};

const NO_SCREEN = '(no screen)';
const SUMMARY_MAX = 80;

export function buildHandoffText(
  reports: readonly ReportResponse[],
  creatorName: string,
  now: Date,
): HandoffDraft {
  const groups = groupByScreen(reports);
  const ordered = groups.flatMap((g) => g.reports);
  const count = ordered.length;

  const lines: string[] = [
    `# Handoff · ${utcMinute(now)} · ${count} ${reportNoun(count)}`,
    `By ${creatorName}`,
    '',
    '## Index',
  ];

  let n = 0;
  for (const group of groups) {
    lines.push(group.screen ?? NO_SCREEN);
    for (const report of group.reports) {
      n += 1;
      lines.push(`  ${n}. ${shortId(report.id)} · ${report.type} · ${summary(report.description)}`);
    }
  }

  lines.push('', '---');

  ordered.forEach((report, i) => {
    lines.push('', ...section(report, i + 1));
  });

  return { text: `${lines.join('\n')}\n`, reportIds: ordered.map((r) => r.id) };
}

/** The first 8 characters of the Report id — enough to find it, short enough to say aloud. */
export function shortId(id: string): string {
  return id.slice(0, 8);
}

interface ScreenGroup {
  /** Null for the final "(no screen)" group. */
  screen: string | null;
  reports: ReportResponse[];
}

/**
 * Screens alphabetically (plain code-unit order, so the result never depends on the
 * browser's locale), then "(no screen)" last; oldest submission first within a group, with
 * the id as a tie-break so the same selection always yields the same text.
 */
function groupByScreen(reports: readonly ReportResponse[]): ScreenGroup[] {
  const byScreen = new Map<string | null, ReportResponse[]>();
  for (const report of reports) {
    const key = present(report.screen) ?? null;
    const bucket = byScreen.get(key);
    if (bucket) bucket.push(report);
    else byScreen.set(key, [report]);
  }

  const keys = [...byScreen.keys()].sort((a, b) => {
    if (a === b) return 0;
    if (a === null) return 1;
    if (b === null) return -1;
    return a < b ? -1 : 1;
  });

  return keys.map((screen) => ({
    screen,
    reports: [...(byScreen.get(screen) ?? [])].sort(
      (a, b) =>
        Date.parse(a.submittedAt) - Date.parse(b.submittedAt) ||
        (a.id < b.id ? -1 : a.id > b.id ? 1 : 0),
    ),
  }));
}

/** One Report's section: everything a fix needs, with no reference to any other section. */
function section(report: ReportResponse, n: number): string[] {
  const lines = [`## ${n}. ${shortId(report.id)} · ${report.type}`, ''];
  lines.push(...quote(report.description), '');

  lines.push(`- Status: ${STATUS_LABELS[report.status] ?? report.status}`);
  const screen = present(report.screen);
  if (screen) lines.push(`- Screen: ${screen}`);
  lines.push(`- Submitted: ${utcMinute(new Date(report.submittedAt))}`);
  lines.push(`- Traveler uid: ${present(report.reporterUid) ?? 'none (signed out)'}`);
  const device = deviceLine(report);
  if (device) lines.push(`- Device: ${device}`);

  lines.push('', ...notes(report.notes));
  return lines;
}

/** Whichever of platform, app version, OS, browser and device model are present. */
function deviceLine(report: ReportResponse): string | undefined {
  const appVersion = present(report.appVersion);
  const parts = [
    present(report.platform),
    appVersion && `app ${appVersion}`,
    present(report.os),
    present(report.browser),
    present(report.deviceModel),
  ].filter((p): p is string => !!p);
  return parts.length > 0 ? parts.join(' · ') : undefined;
}

/**
 * Notes oldest first as "- date · author: body". A multi-line body continues on lines
 * indented under the bullet (a blank line indented too, so it stays inside the list item); "(edited date)" closes an edited Note.
 */
function notes(all: readonly ReportNote[] | undefined): string[] {
  if (!all || all.length === 0) return ['Notes: none'];
  const sorted = [...all].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));

  const lines = ['Notes'];
  for (const note of sorted) {
    const body = splitLines(note.body);
    if (note.editedAt) {
      body[body.length - 1] += ` (edited ${utcDate(new Date(note.editedAt))})`;
    }
    const [first, ...rest] = body;
    lines.push(`- ${utcDate(new Date(note.createdAt))} · ${note.authorName}: ${first}`);
    for (const line of rest) lines.push(`  ${line}`);
  }
  return lines;
}

/**
 * The index summary: the description's first line, trimmed, cut at 80 characters with "…".
 * Counted in code points so an emoji is never split in half. A description that opens with
 * blank lines is trimmed first, so the summary is its first line with any words on it.
 */
function summary(description: string): string {
  const first = splitLines(description.trim())[0].trim();
  const chars = Array.from(first);
  return chars.length > SUMMARY_MAX ? `${chars.slice(0, SUMMARY_MAX).join('').trimEnd()}…` : first;
}

/**
 * The reporter's words as a Markdown blockquote, line by line: every line prefixed "> ", a
 * blank line kept as ">". Nothing else is escaped or altered, so a description full of
 * Markdown can't break the text's structure and still reaches the reader verbatim.
 */
function quote(description: string): string[] {
  return splitLines(description).map((line) => (line === '' ? '>' : `> ${line}`));
}

function splitLines(s: string): string[] {
  return s.split(/\r\n|\r|\n/);
}

/** The value when it has any non-whitespace content; undefined for null, "" and blanks. */
function present(value: string | null | undefined): string | undefined {
  return value != null && value.trim() !== '' ? value : undefined;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** "2026-10-07". */
function utcDate(date: Date): string {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

/** "2026-10-07 14:02 UTC". */
function utcMinute(date: Date): string {
  return `${utcDate(date)} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())} UTC`;
}

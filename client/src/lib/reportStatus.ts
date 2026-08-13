import { Feather } from '@expo/vector-icons';

import { colors } from '@/theme';
import type { ReportPlatform, ReportStatus, ReportType } from './types';

// The five triage states in the order a report normally travels, so chips and the detail
// control both read left-to-right as a lifecycle. `discuss` is deliberately labelled "For
// discussion" — it means "parked for a founders' decision", not "being discussed".
export const STATUS_ORDER: ReportStatus[] = ['new', 'discuss', 'in_progress', 'done', 'dismissed'];

export const STATUS_LABELS: Record<ReportStatus, string> = {
  new: 'New',
  discuss: 'For discussion',
  in_progress: 'In progress',
  done: 'Done',
  dismissed: 'Dismissed',
};

// The default inbox view: everything still open. Done and dismissed are history — reachable
// through the chips, but not what you see on arrival.
export const OPEN_STATUSES: ReportStatus[] = ['new', 'discuss', 'in_progress'];

type StatusTone = { bg: string; fg: string };

// New is the only status that shouts (solid brand); the rest are quiet, because a report
// being in progress is not news. Dismissed reads greyest of all.
export const STATUS_TONES: Record<ReportStatus, StatusTone> = {
  new: { bg: colors.brand, fg: colors.onBrand },
  discuss: { bg: colors.brandSoft, fg: colors.brandDeep },
  in_progress: { bg: colors.brandSoft, fg: colors.brandDeep },
  done: { bg: colors.hairline, fg: colors.textMuted },
  dismissed: { bg: colors.hairline, fg: colors.textFaint },
};

// The status's own colour, used for the row's left edge and the sheet's dots — the pill
// tones above are backgrounds, which are too pale to read as a 3px edge.
export const STATUS_EDGE: Record<ReportStatus, string> = {
  new: colors.brand,
  discuss: colors.accent,
  in_progress: colors.salmon,
  done: colors.textFaint,
  dismissed: colors.hairline,
};

export const TYPE_LABELS: Record<ReportType, string> = {
  problem: 'Problem',
  idea: 'Idea',
};

// A bug glyph for problems, a lightbulb for ideas — the same two-way split the reporter saw.
export const TYPE_ICONS: Record<ReportType, keyof typeof Feather.glyphMap> = {
  problem: 'alert-circle',
  idea: 'zap',
};

export const PLATFORM_LABELS: Record<ReportPlatform, string> = {
  android: 'Android',
  ios: 'iOS',
  web: 'Web',
};

/** "Android · 1.4.2" — the context line that stops "which build was this?" from ever
 *  needing a follow-up question (reporters are unreachable by design). */
export function platformLabel(platform: ReportPlatform, appVersion: string): string {
  return `${PLATFORM_LABELS[platform] ?? platform} · ${appVersion}`;
}

import { monthShort } from './datetime';
import type { SeriesBucket } from './types';

// Display helpers for the Largata usage Dashboard — words only, no state (the Reports twin is
// lib/reportStatus.ts).

/**
 * A counter's display name. Counters are Largata's words, opaque here, so this only makes
 * them readable: separators become spaces and the plural follows the number ("1 trip",
 * "19 diaries"). Never a lookup table — a new counter must render without a worklog change.
 */
export function counterLabel(counter: string, n: number): string {
  const words = counter.replace(/[._-]+/g, ' ').trim();
  if (n === 1) return words;
  if (/[^aeiou]y$/i.test(words)) return `${words.slice(0, -1)}ies`;
  if (/(s|x|z|ch|sh)$/i.test(words)) return `${words}es`;
  return `${words}s`;
}

/** "Sep 24" · "Sep 2026" · "2026" — a bucket's name in the reader's words. */
export function bucketLabel(bucket: SeriesBucket, start: string): string {
  const [y, m, d] = start.split('-').map(Number);
  if (bucket === 'year') return String(y);
  const month = monthShort(new Date(y, (m ?? 1) - 1, 1));
  return bucket === 'month' ? `${month} ${y}` : `${month} ${d}`;
}

/** The latest bucket, in words: the chart's last bucket is always the current one. */
export function currentPeriod(bucket: SeriesBucket): string {
  return bucket === 'day' ? 'today' : bucket === 'month' ? 'this month' : 'this year';
}

/** "14 days" · "12 months" · "1 year". */
export function periodSpan(bucket: SeriesBucket, n: number): string {
  return `${n} ${bucket}${n === 1 ? '' : 's'}`;
}

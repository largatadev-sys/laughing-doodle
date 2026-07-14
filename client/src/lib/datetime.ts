// Shared date + duration formatting. Entries are date-only (`YYYY-MM-DD`) + a whole-minute
// duration (INV-4) — there is no time-of-day, so nothing here invents clock times.

// Reference "full day" for scaling a single entry's tally bar — a 3h entry then reads as
// ~⅜ of a workday. Not a cap or a rule, just the yardstick the bars are drawn against.
export const WORKDAY_MIN = 8 * 60;

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const WEEKDAYS_LONG = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_LONG = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

export function toISODate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

// Parse a `YYYY-MM-DD` as LOCAL midnight — `new Date('2026-07-14')` is UTC and shifts a day
// in western timezones, which would misfile entries into the wrong calendar cell.
export function parseISODate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

export function addDays(date: Date, days: number): Date {
  const r = new Date(date);
  r.setDate(r.getDate() + days);
  r.setHours(0, 0, 0, 0);
  return r;
}

// Monday-based week start (matches the old team feed's convention).
export function startOfWeek(date: Date): Date {
  const day = date.getDay(); // 0=Sun..6=Sat
  const diffToMonday = day === 0 ? -6 : 1 - day;
  return addDays(date, diffToMonday);
}

export function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

export function endOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0);
}

// Six-week grid from the Monday on/before the 1st; trailing all-next-month weeks trimmed.
// Shared by the calendar screen and the date picker so they read identically.
export function monthMatrix(anchor: Date): Date[][] {
  let cur = startOfWeek(startOfMonth(anchor));
  const weeks: Date[][] = [];
  for (let w = 0; w < 6; w++) {
    const row: Date[] = [];
    for (let d = 0; d < 7; d++) {
      row.push(cur);
      cur = addDays(cur, 1);
    }
    weeks.push(row);
  }
  return weeks.filter((row) => row.some((d) => d.getMonth() === anchor.getMonth()));
}

// "Mon, Jul 14, 2026" — the medium date shown in the picker field.
export function formatMediumDate(iso: string): string {
  const d = parseISODate(iso);
  return `${WEEKDAYS[d.getDay()]}, ${MONTHS[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

// "3h 15m" · "45m" · "2h" — the friendly duration used everywhere.
export function formatDuration(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

// Compact hours for tight calendar cells: "3h" · "3.5h".
export function formatHours(min: number): string {
  const h = min / 60;
  const rounded = Math.round(h * 10) / 10;
  return Number.isInteger(rounded) ? `${rounded}h` : `${rounded}h`;
}

// "logged 2h ago" — relative time from a timestamp, degrading to a date past a week.
export function relativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const secs = Math.floor((now.getTime() - then.getTime()) / 1000);
  if (secs < 45) return 'just now';
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d ago`;
  return `${MONTHS[then.getMonth()]} ${then.getDate()}`;
}

// Day divider label: "Today" · "Yesterday" · "Mon, Jul 14".
export function dayLabel(iso: string, now: Date = new Date()): string {
  const d = parseISODate(iso);
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diff = Math.round((today.getTime() - d.getTime()) / 86400000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  return `${WEEKDAYS[d.getDay()]}, ${MONTHS[d.getMonth()]} ${d.getDate()}`;
}

export function weekdayShort(date: Date): string {
  return WEEKDAYS[date.getDay()];
}

export function weekdayLong(date: Date): string {
  return WEEKDAYS_LONG[date.getDay()];
}

export function monthName(date: Date): string {
  return MONTHS_LONG[date.getMonth()];
}

export function monthShort(date: Date): string {
  return MONTHS[date.getMonth()];
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

// Largata brand palette — extracted from the product screenshots.
// One hot red owns all chrome/CTAs; the only non-red colour in the UI enters through
// people (member avatars + per-person calendar load), exactly as the brand does it.
// Hex values are estimates from the mockups — tweak here and the whole app follows.

export const palette = {
  red: '#F5333F', // Largata Red — logo, CTAs, active tab, today, eyebrows, card accents
  redDeep: '#D62330', // pressed states / logo depth
  redSoft: '#FCE3E4', // pale wash — soft fills, selected day
  salmon: '#F7A9AC', // status pills, muted banners
  violet: '#7C4DFF', // the one sparing "featured/selected" highlight

  ink: '#1A1A1E', // primary text + bold labels
  inkMuted: '#8A8A8F', // secondary text, captions
  inkFaint: '#C4C4C8', // placeholders, disabled

  paper: '#FFFFFF', // cards / surfaces
  page: '#F7F7F8', // app background
  hairline: '#ECECEE', // neutral dividers
  cardBorder: '#F6CBCE', // soft red-tinted card outline (Largata's outlined cards)
} as const;

// Semantic aliases — screens reference these, not raw palette keys, so a re-theme
// is a one-file change.
export const colors = {
  brand: palette.red,
  brandDeep: palette.redDeep,
  brandSoft: palette.redSoft,
  salmon: palette.salmon,
  accent: palette.violet,

  text: palette.ink,
  textMuted: palette.inkMuted,
  textFaint: palette.inkFaint,
  onBrand: '#FFFFFF',

  bg: palette.page,
  surface: palette.paper,
  hairline: palette.hairline,
  cardBorder: palette.cardBorder,

  danger: palette.red, // destructive == brand red here (delete)
} as const;

// People are the only colourful thing in the UI. Bright, friendly, paper-legible —
// the same spirit as Largata's multi-colour member circles.
export const personHues = [
  '#F06AA0', // pink
  '#8B5CF6', // violet
  '#4C8DFF', // blue
  '#22B8A6', // teal
  '#F5A623', // amber
  '#FF7A59', // coral
  '#5B6EF5', // indigo
  '#3FB950', // green
] as const;

// Deterministic person colour: the same name always maps to the same hue, so a
// teammate is recognisable at a glance across the feed and the calendar.
export function colorForName(name: string): string {
  let h = 0;
  for (let i = 0; i < name.length; i++) {
    h = (h * 31 + name.charCodeAt(i)) >>> 0;
  }
  return personHues[h % personHues.length];
}

// Pick ink or white for text sitting on a coloured chip, by perceived luminance — so
// initials stay legible on the light hues (amber, coral) as well as the dark ones.
export function readableTextOn(hex: string): string {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.62 ? palette.ink : '#FFFFFF';
}

// A translucent tint of any hex (for avatar/chip backgrounds). alpha in [0,1].
export function tint(hex: string, alpha = 0.14): string {
  const a = Math.round(Math.max(0, Math.min(1, alpha)) * 255)
    .toString(16)
    .padStart(2, '0');
  return `${hex}${a}`;
}

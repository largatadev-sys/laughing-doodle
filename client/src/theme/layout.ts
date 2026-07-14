// Spacing, radii, and the floating tab-bar geometry. One 4px rhythm everywhere.

export const space = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

// Largata is rounded-everything. `pill` = fully round; cards use `lg`.
export const radius = {
  sm: 8,
  md: 12,
  lg: 18,
  xl: 24,
  pill: 999,
} as const;

// The floating red pill nav sits above the home indicator; screens pad their scroll
// content by TAB_BAR_CLEARANCE so nothing hides behind it.
export const TAB_BAR_HEIGHT = 60;
export const TAB_BAR_MARGIN = 16;
export const TAB_BAR_CLEARANCE = TAB_BAR_HEIGHT + TAB_BAR_MARGIN + 24;

// One soft shadow, reused, so elevation reads as a single system (not per-component guesses).
export const shadow = {
  card: {
    shadowColor: '#1A1A1E',
    shadowOpacity: 0.06,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 2,
  },
  floating: {
    shadowColor: '#D62330',
    shadowOpacity: 0.28,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 8,
  },
} as const;

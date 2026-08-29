import type { ViewStyle } from 'react-native';

// react-native-web passes `hovered` in the Pressable style-callback state at runtime; React
// Native's own types only declare `pressed`. Screens annotate their style callbacks with this
// so hover styling type-checks on web without fighting the RN types.
export type PressState = { pressed: boolean; hovered?: boolean };

/**
 * A control's label is not prose — don't let the browser select it.
 *
 * On web a press is a mouse-down that the browser also reads as the start of a text drag, so
 * holding a report row to open the status sheet left a blue selection behind, and the selection
 * then extended into the sheet as it mounted under the still-held pointer. Long-press surfaces
 * feel broken without this; on touch it also suppresses the hold-to-select callout.
 *
 * Spread it wherever a style already says `cursor: 'pointer'` — the two travel together, since
 * both are saying "this is a control". Never on prose the reader might want to copy: the
 * reporter's testimony and the note bodies stay selectable by deliberately omitting it.
 */
export const noTextSelect: ViewStyle = { userSelect: 'none' };

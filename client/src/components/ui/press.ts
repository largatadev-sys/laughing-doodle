// react-native-web passes `hovered` in the Pressable style-callback state at runtime; React
// Native's own types only declare `pressed`. Screens annotate their style callbacks with this
// so hover styling type-checks on web without fighting the RN types.
export type PressState = { pressed: boolean; hovered?: boolean };

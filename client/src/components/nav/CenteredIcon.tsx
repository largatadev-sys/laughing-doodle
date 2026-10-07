import { StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';

// An icon that is centred in its own square, for icon-only round buttons.
//
// Vector icons render as text with only a font size, so on web the line box is taller than the
// glyph and it sits low in a circle. Pinning the line box to the icon's size fixes that. Some
// glyphs are also drawn off-centre in their own 24-unit box: Feather's paper plane ("send")
// points up-right, its mass centred at (13, 11) — one unit right and up — so it gets the matching
// optical nudge back. Add an entry here if another off-centre glyph ends up in a round button.
const OPTICAL_NUDGE: Partial<Record<keyof typeof Feather.glyphMap, { x: number; y: number }>> = {
  send: { x: -1, y: 1 },
};

export function CenteredIcon({
  name,
  size,
  color,
}: {
  name: keyof typeof Feather.glyphMap;
  size: number;
  color: string;
}) {
  const nudge = OPTICAL_NUDGE[name];
  // The nudge is in the glyph's 24-unit design grid; scale it to the rendered size.
  const unit = size / 24;
  return (
    <Feather
      name={name}
      size={size}
      color={color}
      style={[
        styles.box,
        { width: size, height: size, lineHeight: size },
        nudge && {
          transform: [{ translateX: nudge.x * unit }, { translateY: nudge.y * unit }],
        },
      ]}
    />
  );
}

const styles = StyleSheet.create({
  box: { textAlign: 'center' },
});

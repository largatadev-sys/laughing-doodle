import { Platform, ScrollView, StyleSheet, View, type ScrollViewProps } from 'react-native';

// Cross-platform scroll region. On NATIVE it's a real ScrollView. On WEB, react-native-web's
// ScrollView mis-sizes inside nested flex layouts — a flex item with tall in-flow content keeps
// a min-content height equal to that content, so it grows instead of bounding, and the page
// can't scroll.
//
// The fix: put the content behind an absolutely-positioned child. The `bounds` wrapper then has
// zero in-flow content, so `flex: 1` correctly shrinks it to the available height; the absolute
// `scroller` fills that height and scrolls. The scrollbar itself is hidden globally in the root
// layout, keeping the native-app feel.
export function Scroll({ children, contentContainerStyle, ...props }: ScrollViewProps) {
  if (Platform.OS === 'web') {
    return (
      <View style={styles.bounds}>
        <View style={styles.scroller}>
          <View style={contentContainerStyle}>{children}</View>
        </View>
      </View>
    );
  }
  return (
    <ScrollView style={styles.nativeScroll} contentContainerStyle={contentContainerStyle} {...props}>
      {children}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  bounds: { flex: 1, position: 'relative' },
  scroller: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, overflow: 'scroll' },
  nativeScroll: { flex: 1 },
});

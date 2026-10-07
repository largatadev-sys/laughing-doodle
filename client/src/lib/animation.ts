import type { Animated } from 'react-native';

/**
 * Start an animation from an effect, guaranteeing its values end up where it was going.
 *
 * An Animated value stops its animation when it is detached from the screen — and covering a
 * screen with another (pushing a stack screen over the tabs, on web) detaches it. An animation
 * caught mid-flight that way would freeze at an in-between frame, and nothing restarts it when
 * the screen is uncovered: the effect that started it has already run. Story 28 hit exactly
 * this: "Hand off" opened the Handoff screen while the tab pill was morphing back, and the pill
 * came back frozen at 95% — a ghost of the action, the navigation invisible.
 *
 * So: if the animation is stopped by anything other than this effect's own cleanup, `land`
 * puts the values on their end state (a value can be set while detached; the screen reads it
 * when it is shown again). `onEnd` runs once the values are at rest, either way.
 *
 * Returns the effect's cleanup — `return startToEnd(anim, land)`.
 */
export function startToEnd(
  anim: Animated.CompositeAnimation,
  land: () => void,
  onEnd?: () => void,
): () => void {
  let cleanedUp = false;
  anim.start(({ finished }) => {
    if (cleanedUp) return; // our own effect moved on; whatever replaced it owns the values now
    if (!finished) land();
    onEnd?.();
  });
  return () => {
    cleanedUp = true;
    anim.stop();
  };
}

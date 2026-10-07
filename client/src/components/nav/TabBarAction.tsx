import { createContext, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useIsFocused } from 'expo-router';
import type { Feather } from '@expo/vector-icons';

// Lets a tab screen borrow the floating red pill for one primary action — the pill morphs from
// navigation into that action and back. Story 28 uses it for "Hand off (N)": while a Member is
// ticking Reports, leaving the tab would only lose the selection, so the navigation's place is
// better spent on the one thing that finishes the task. One action at a time, owned by the
// focused screen.

export type TabBarAction = {
  label: string;
  icon: keyof typeof Feather.glyphMap;
  onPress: () => void;
  /** Restores the navigation. Always shown: the pill must never strand you. */
  onCancel: () => void;
  cancelLabel: string;
  disabled?: boolean;
  loading?: boolean;
  /** Shown just above the pill, e.g. a failed request; the action stays so a retry is one tap. */
  error?: string | null;
  /** 'center': the action was opened from the centre disc, so the disc stretches into the
   *  action's button (and back) instead of the two simply crossfading. */
  origin?: 'center';
};

/**
 * What the white centre disc does on the focused tab, instead of its default (log time). The
 * Reports inbox uses it for "Hand off": the action that belongs to that screen sits where the
 * thumb already goes, and the screen's header stays free of a second call to action.
 */
export type TabBarCenter = {
  icon: keyof typeof Feather.glyphMap;
  /** Accessible name; the disc shows only the icon. */
  label: string;
  onPress: () => void;
};

type Channel = {
  action: TabBarAction | null;
  setAction: (action: TabBarAction | null) => void;
  center: TabBarCenter | null;
  setCenter: (center: TabBarCenter | null) => void;
};

const TabBarActionContext = createContext<Channel>({
  action: null,
  setAction: () => {},
  center: null,
  setCenter: () => {},
});

export function TabBarActionProvider({ children }: { children: React.ReactNode }) {
  const [action, setAction] = useState<TabBarAction | null>(null);
  const [center, setCenter] = useState<TabBarCenter | null>(null);
  const value = useMemo(() => ({ action, setAction, center, setCenter }), [action, center]);
  return <TabBarActionContext.Provider value={value}>{children}</TabBarActionContext.Provider>;
}

/** For the tab layout: the action the pill should show, or null for the navigation. */
export function useCurrentTabBarAction(): TabBarAction | null {
  return useContext(TabBarActionContext).action;
}

/** For the tab layout: the centre disc's override, or null for its default. */
export function useCurrentTabBarCenter(): TabBarCenter | null {
  return useContext(TabBarActionContext).center;
}

/**
 * For a tab screen: put `center` on the centre disc while it is non-null and the screen is
 * focused; hand the disc back on null, blur or unmount (same focus rule as useTabBarAction).
 */
export function useTabBarCenter(center: TabBarCenter | null): void {
  const { setCenter } = useContext(TabBarActionContext);
  const latest = useRef(center);
  useLayoutEffect(() => {
    latest.current = center;
  });

  const focused = useIsFocused();
  const active = center !== null && focused;
  const icon = center?.icon;
  const label = center?.label;

  useEffect(() => {
    if (!active || icon === undefined || label === undefined) {
      setCenter(null);
      return;
    }
    setCenter({ icon, label, onPress: () => latest.current?.onPress() });
  }, [active, icon, label, setCenter]);

  useEffect(() => () => setCenter(null), [setCenter]);
}

/**
 * For a tab screen: show `action` in the pill while it is non-null AND the screen is focused,
 * and hand the pill back on null, blur or unmount. Focus matters because the tab slot keeps a
 * visited tab mounted (hidden) after you leave it — browser Back from a mid-selection Reports
 * must not leave Home wearing "Hand off (N)". Coming back to the screen re-publishes it.
 * Handlers are read through a ref, so passing fresh closures every render doesn't re-publish
 * the action (or restart the pill's motion); only what's visible does.
 */
export function useTabBarAction(action: TabBarAction | null): void {
  const { setAction } = useContext(TabBarActionContext);
  const latest = useRef(action);
  useLayoutEffect(() => {
    latest.current = action;
  });

  const focused = useIsFocused();
  const active = action !== null && focused;
  const label = action?.label;
  const icon = action?.icon;
  const cancelLabel = action?.cancelLabel;
  const disabled = action?.disabled;
  const loading = action?.loading;
  const error = action?.error;
  const origin = action?.origin;

  useEffect(() => {
    if (!active || label === undefined || icon === undefined || cancelLabel === undefined) {
      setAction(null);
      return;
    }
    setAction({
      label,
      icon,
      cancelLabel,
      disabled,
      loading,
      error,
      origin,
      onPress: () => latest.current?.onPress(),
      onCancel: () => latest.current?.onCancel(),
    });
  }, [active, label, icon, cancelLabel, disabled, loading, error, origin, setAction]);

  useEffect(() => () => setAction(null), [setAction]);
}

import { useCallback, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import {
  ReportListEmpty,
  ReportListError,
  ReportListSkeleton,
} from '@/components/ReportListStates';
import { Card, Scroll } from '@/components/ui';
import { noTextSelect, type PressState } from '@/components/ui/press';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { clockTimeOrDate } from '@/lib/datetime';
import { reportNoun } from '@/lib/plural';
import type { HandoffSummary } from '@/lib/types';
import { colors, fonts, space, type } from '@/theme';

/**
 * Every Handoff, newest first (Story 28): when, who and how many. A row reopens that
 * Handoff's frozen text with Copy. Refetched on every focus, so a Handoff made a moment ago
 * is already here when you come back to the list.
 */
export default function HandoffsScreen() {
  const { session, logout } = useAuth();
  const insets = useSafeAreaInsets();
  const [handoffs, setHandoffs] = useState<HandoffSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Returns its own cancel, so focus can drop a fetch that lands after the screen blurs; Try
  // again calls it directly.
  const load = useCallback(() => {
    if (!session) return;
    let cancelled = false;
    setError(null);
    apiClient
      .listHandoffs(session.token)
      .then((result) => {
        if (!cancelled) setHandoffs(result);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        if (e instanceof UnauthorizedError) return logout();
        setError(e instanceof Error ? e.message : 'Could not load the handoffs.');
      });
    return () => {
      cancelled = true;
    };
  }, [session, logout]);

  useFocusEffect(load);

  return (
    <View style={styles.screen}>
      <View style={[styles.topBar, { paddingTop: insets.top + space.sm }]}>
        <Pressable onPress={() => router.back()} hitSlop={10} style={styles.backBtn}>
          <Feather name="chevron-left" size={24} color={colors.brand} />
        </Pressable>
        <Text style={styles.topTitle}>Handoffs</Text>
        <View style={styles.backBtn} />
      </View>

      <Scroll contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* An error never blanks a list we already have — it sits above it, as in the inbox. */}
        {error && <ReportListError message={error} onRetry={() => void load()} />}

        {handoffs === null && !error && <ReportListSkeleton />}

        {handoffs !== null && handoffs.length === 0 && (
          <ReportListEmpty
            title="No handoffs yet"
            body="Hand off open reports from the inbox and they'll be kept here."
          />
        )}

        {handoffs !== null && handoffs.length > 0 && (
          <Card flush>
            {handoffs.map((h, i) => (
              <Pressable
                key={h.id}
                onPress={() => router.push({ pathname: '/handoff/[id]', params: { id: h.id } })}
                accessibilityRole="button"
                accessibilityLabel={`Handoff by ${h.createdByName}, ${h.reportCount} ${reportNoun(h.reportCount)}, ${clockTimeOrDate(h.createdAt)}`}
                style={({ pressed, hovered }: PressState) => [
                  styles.row,
                  i > 0 && styles.divider,
                  hovered && styles.rowHover,
                  pressed && styles.rowPressed,
                ]}>
                <Feather name="send" size={16} color={colors.textMuted} />
                <View style={styles.rowBody}>
                  <Text style={styles.rowTitle}>{clockTimeOrDate(h.createdAt)}</Text>
                  <Text style={styles.rowMeta}>
                    {h.createdByName} · {h.reportCount} {reportNoun(h.reportCount)}
                  </Text>
                </View>
                <Feather name="chevron-right" size={18} color={colors.textFaint} />
              </Pressable>
            ))}
          </Card>
        )}
      </Scroll>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    paddingBottom: space.sm,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.hairline,
  },
  backBtn: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center' },
  topTitle: { ...type.heading },

  content: { padding: space.lg, gap: space.md, paddingBottom: space.xxl },

  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    minHeight: 62,
    paddingHorizontal: space.md,
    paddingVertical: space.sm + 2,
    cursor: 'pointer',
    ...noTextSelect,
  },
  divider: { borderTopWidth: 1, borderTopColor: colors.hairline },
  rowHover: { backgroundColor: colors.brandSoft },
  rowPressed: { opacity: 0.85 },
  rowBody: { flex: 1, gap: 2 },
  rowTitle: { ...type.bodyMedium, fontFamily: fonts.bold },
  rowMeta: { ...type.caption },
});

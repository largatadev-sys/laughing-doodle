import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorNote } from '@/components/nav/MorphingPill';
import { ReportListError } from '@/components/ReportListStates';
import { Card, PillButton, Scroll } from '@/components/ui';
import { apiClient, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { copyText } from '@/lib/clipboard';
import { clockTimeOrDate } from '@/lib/datetime';
import { reportNoun } from '@/lib/plural';
import type { Handoff } from '@/lib/types';
import { colors, fonts, radius, shadow, space, TAB_BAR_CLEARANCE, type } from '@/theme';

// No mono face is bundled; the platform's own is what a pasted Markdown block looks like.
const MONO = Platform.select({
  web: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
  ios: 'Menlo',
  default: 'monospace',
});

type CopyState = 'idle' | 'copied' | 'failed';

/**
 * One Handoff: who, when, how many, and the exact text that was handed over — raw, so what
 * you see is what you paste. Fetched by id (not passed in), so a fresh create, a reopen from
 * the list and a browser reload all show the stored text rather than a local copy.
 */
export default function HandoffScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { session, logout } = useAuth();
  const insets = useSafeAreaInsets();
  const token = session?.token ?? null;
  const [handoff, setHandoff] = useState<Handoff | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [copy, setCopy] = useState<CopyState>('idle');

  useEffect(() => {
    if (!token || !id) return;
    let live = true;
    apiClient
      .getHandoff(id, token)
      .then((h) => {
        if (live) setHandoff(h);
      })
      .catch((e: unknown) => {
        if (e instanceof UnauthorizedError) return void logout();
        if (live) setError(e instanceof Error ? e.message : 'Could not load this handoff.');
      });
    return () => {
      live = false;
    };
  }, [id, token, logout, attempt]);

  async function copyHandoff() {
    if (!handoff) return;
    // Web only for now (spec). The Handoff is already recorded either way; a failed copy only
    // means the text above has to be selected by hand.
    setCopy((await copyText(handoff.text)) ? 'copied' : 'failed');
  }

  const count = handoff?.reportCount ?? 0;

  return (
    <View style={styles.screen}>
      <View style={[styles.topBar, { paddingTop: insets.top + space.sm }]}>
        <Pressable onPress={() => router.back()} hitSlop={10} style={styles.backBtn}>
          <Feather name="chevron-left" size={24} color={colors.brand} />
        </Pressable>
        <Text style={styles.topTitle}>Handoff</Text>
        <View style={styles.backBtn} />
      </View>

      {!handoff ? (
        <View style={styles.missing}>
          {error ? (
            <ReportListError message={error} onRetry={() => {
                setError(null);
                setAttempt((n) => n + 1);
              }}
            />
          ) : (
            <ActivityIndicator color={colors.brand} />
          )}
        </View>
      ) : (
        <Scroll contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <Text style={styles.headerLine}>
            {clockTimeOrDate(handoff.createdAt)} · {handoff.createdByName} · {count}{' '}
            {reportNoun(count)}
          </Text>

          <Card style={styles.textCard}>
            {/* Raw, unrendered Markdown in a monospace face; selectable so a failed copy never
                blocks anyone. RN-web keeps newlines and leading spaces (pre-wrap). */}
            <Text selectable style={styles.text}>
              {handoff.text}
            </Text>
          </Card>
        </Scroll>
      )}

      {handoff && (
        // Copy floats where the tab pill sits on the tab screens, so this screen's one action is
        // where the thumb already goes. A failure floats just above it.
        <View
          style={[styles.floating, { bottom: Math.max(insets.bottom, 10) }]}
          pointerEvents="box-none">
          {copy === 'failed' && (
            <ErrorNote message="Couldn't copy — select the text above by hand." />
          )}
          <PillButton
            label={copy === 'copied' ? 'Copied' : 'Copy'}
            icon={copy === 'copied' ? 'check' : 'copy'}
            onPress={copyHandoff}
            style={styles.copyButton}
          />
        </View>
      )}
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

  // Clears the floating pill (and a failure note above it) so the last line can scroll free.
  content: { padding: space.lg, gap: space.md, paddingBottom: TAB_BAR_CLEARANCE + 64 },
  headerLine: { ...type.bodyMedium, fontFamily: fonts.bold },
  floating: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    gap: space.sm,
  },
  copyButton: { minWidth: 200, ...shadow.floating },

  textCard: { borderRadius: radius.md },
  text: { fontFamily: MONO, fontSize: 13, lineHeight: 19, color: colors.text },

  missing: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: space.lg },
});

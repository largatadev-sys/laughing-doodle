import { useCallback, useMemo, useState } from 'react';
import { router, useFocusEffect } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { AppHeader } from '@/components/AppHeader';
import {
  ReportListEmpty,
  ReportListError,
  ReportListSkeleton,
} from '@/components/ReportListStates';
import { ReportRow } from '@/components/ReportRow';
import { StatusSheet } from '@/components/StatusSheet';
import { Card, Eyebrow, FadeInView, Scroll } from '@/components/ui';
import type { PressState } from '@/components/ui/press';
import { useReports } from '@/lib/reports';
import { OPEN_STATUSES, STATUS_LABELS } from '@/lib/reportStatus';
import type { ReportResponse, ReportStatus } from '@/lib/types';
import { colors, fonts, radius, space, TAB_BAR_CLEARANCE, type } from '@/theme';

// The top-level split: what's still live, versus the two archives. Everything open shares one
// segment because the point of the inbox is "what needs us", not "which of five buckets".
type Segment = 'open' | 'done' | 'dismissed';

const SEGMENTS: { key: Segment; label: string }[] = [
  { key: 'open', label: 'Open' },
  { key: 'done', label: 'Done' },
  { key: 'dismissed', label: 'Dismissed' },
];

export default function ReportsInbox() {
  const { reports, error, refresh, changeStatus } = useReports();
  const [segment, setSegment] = useState<Segment>('open');
  // Within Open, an optional narrowing to one status. Cleared whenever the segment changes.
  const [narrow, setNarrow] = useState<ReportStatus | null>(null);
  const [sheetFor, setSheetFor] = useState<ReportResponse | null>(null);
  const [saving, setSaving] = useState<ReportStatus | null>(null);
  const [writeError, setWriteError] = useState<string | null>(null);

  // Feedback lands while you are elsewhere in the app, so the inbox refetches on focus.
  // This does NOT clear the badge: the count follows the data, not the visit.
  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh]),
  );

  const openReports = useMemo(
    () => (reports ?? []).filter((r) => OPEN_STATUSES.includes(r.status)),
    [reports],
  );

  const visible = useMemo(() => {
    if (!reports) return [];
    if (segment === 'open') {
      return narrow ? openReports.filter((r) => r.status === narrow) : openReports;
    }
    return reports.filter((r) => r.status === segment);
  }, [reports, openReports, segment, narrow]);

  function selectSegment(next: Segment) {
    setSegment(next);
    setNarrow(null);
  }

  async function move(report: ReportResponse, status: ReportStatus) {
    if (status === report.status) {
      setSheetFor(null);
      return; // Selecting the current status is a no-op — no request (spec §4).
    }
    setSaving(status);
    setWriteError(null);
    try {
      await changeStatus(report.id, status);
      setSheetFor(null);
    } catch (e) {
      setWriteError(e instanceof Error ? e.message : 'Could not update that report.');
    } finally {
      setSaving(null);
    }
  }

  const showSkeleton = reports === null && !error;
  // An error never blanks a list we already have — it sits above the stale data (spec §3).
  const showError = error !== null;

  return (
    <View style={styles.screen}>
      <AppHeader />

      <Scroll
        contentContainerStyle={[styles.content, { paddingBottom: TAB_BAR_CLEARANCE }]}
        showsVerticalScrollIndicator={false}>
        <FadeInView>
          {/* 1b's header is compact — the eyebrow carries §6's "From Largata" provenance so
              the source of this feedback stays stated, while the count replaces the subtitle. */}
          <Eyebrow>From Largata</Eyebrow>
          <View style={styles.titleRow}>
            <Text style={styles.title}>Inbox</Text>
            <Text style={styles.openCount}>{openReports.length} open</Text>
          </View>
        </FadeInView>

        <FadeInView delay={50}>
          <View style={styles.segmented}>
            {SEGMENTS.map((s) => (
              <Pressable
                key={s.key}
                onPress={() => selectSegment(s.key)}
                accessibilityRole="button"
                accessibilityState={{ selected: segment === s.key }}
                style={({ pressed }: PressState) => [
                  styles.segment,
                  segment === s.key && styles.segmentActive,
                  pressed && styles.segmentPressed,
                ]}>
                <Text style={[styles.segmentText, segment === s.key && styles.segmentTextActive]}>
                  {s.label}
                </Text>
              </Pressable>
            ))}
          </View>
        </FadeInView>

        {/* Sub-chips narrow *within* Open — the three archives need no further slicing. */}
        {segment === 'open' && (
          <FadeInView delay={90}>
            <View style={styles.subChips}>
              {OPEN_STATUSES.map((status) => {
                const count = openReports.filter((r) => r.status === status).length;
                const active = narrow === status;
                return (
                  <Pressable
                    key={status}
                    onPress={() => setNarrow(active ? null : status)}
                    accessibilityRole="button"
                    accessibilityState={{ selected: active }}
                    accessibilityLabel={`${STATUS_LABELS[status]}, ${count}`}
                    style={({ pressed, hovered }: PressState) => [
                      styles.subChip,
                      active && styles.subChipActive,
                      hovered && !active && styles.subChipHover,
                      pressed && styles.subChipPressed,
                    ]}>
                    <Text style={[styles.subChipText, active && styles.subChipTextActive]}>
                      {STATUS_LABELS[status]}
                    </Text>
                    {count > 0 && (
                      <Text style={[styles.subChipCount, active && styles.subChipTextActive]}>
                        {count}
                      </Text>
                    )}
                  </Pressable>
                );
              })}
            </View>
          </FadeInView>
        )}

        {showError && <ReportListError onRetry={refresh} />}
        {writeError && <Text style={styles.writeError}>{writeError}</Text>}

        {showSkeleton && <ReportListSkeleton />}

        {reports !== null && visible.length === 0 && !showError && (
          <ReportListEmpty
            title={emptyTitle(segment, narrow)}
            body={
              segment === 'open' && !narrow
                ? 'No reports are waiting. New feedback from Largata lands here.'
                : 'Try another filter to see the rest of the inbox.'
            }
          />
        )}

        {visible.length > 0 && (
          // One flush card holding every row, so the list reads as a single surface with
          // hairline dividers rather than a stack of floating cards.
          <Card flush style={styles.list}>
            {visible.map((report, i) => (
              <FadeInView key={report.id} delay={Math.min(140 + i * 60, 480)}>
                <View style={i > 0 ? styles.divider : undefined}>
                  <ReportRow
                    report={report}
                    onPress={(r) => router.push({ pathname: '/report/[id]', params: { id: r.id } })}
                    onTriage={setSheetFor}
                  />
                </View>
              </FadeInView>
            ))}
          </Card>
        )}

        {visible.length > 0 && (
          /* The long-press is the only way to triage from this list and nothing on screen
             advertises it, so this hint is load-bearing rather than decorative. */
          <Text style={styles.hint}>Tap to read · press and hold to move</Text>
        )}
      </Scroll>

      <StatusSheet
        report={sheetFor}
        saving={saving}
        onSelect={(status) => sheetFor && move(sheetFor, status)}
        onClose={() => setSheetFor(null)}
      />
    </View>
  );
}

function emptyTitle(segment: Segment, narrow: ReportStatus | null): string {
  if (segment === 'open') {
    return narrow ? `No ${STATUS_LABELS[narrow].toLowerCase()} reports` : 'Nothing open';
  }
  return `No ${STATUS_LABELS[segment].toLowerCase()} reports`;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { paddingHorizontal: space.lg, paddingTop: space.lg, gap: space.md },

  titleRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  title: { ...type.display },
  openCount: { ...type.caption, fontFamily: fonts.bold },

  segmented: {
    flexDirection: 'row',
    backgroundColor: colors.hairline,
    borderRadius: radius.pill,
    padding: 3,
  },
  segment: {
    flex: 1,
    minHeight: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.pill,
    cursor: 'pointer',
  },
  segmentActive: { backgroundColor: colors.surface },
  segmentPressed: { opacity: 0.85 },
  segmentText: { fontFamily: fonts.bold, fontSize: 13, color: colors.textMuted },
  segmentTextActive: { color: colors.text },

  subChips: { flexDirection: 'row', gap: space.sm, flexWrap: 'wrap' },
  subChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    minHeight: 44,
    paddingHorizontal: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface,
    cursor: 'pointer',
  },
  subChipActive: { backgroundColor: colors.brand, borderColor: colors.brand },
  subChipHover: { backgroundColor: colors.brandSoft },
  subChipPressed: { transform: [{ scale: 0.97 }] },
  subChipText: { fontFamily: fonts.bold, fontSize: 12.5, color: colors.text },
  subChipTextActive: { color: colors.onBrand },
  subChipCount: { fontFamily: fonts.bold, fontSize: 11.5, color: colors.textMuted },

  list: { marginTop: space.xs },
  divider: { borderTopWidth: 1, borderTopColor: colors.hairline },
  hint: { ...type.caption, textAlign: 'center', marginTop: space.xs },
  writeError: { ...type.body, color: colors.brand },
});

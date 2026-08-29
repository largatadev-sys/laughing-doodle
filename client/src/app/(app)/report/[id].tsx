import { useMemo, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ReportNotes } from '@/components/ReportNotes';
import { ReportScreenshots } from '@/components/ReportScreenshots';
import { ReportStatusPill } from '@/components/ReportStatusPill';
import { Card, Eyebrow, Scroll } from '@/components/ui';
import { noTextSelect, type PressState } from '@/components/ui/press';
import { activityLabel } from '@/lib/datetime';
import { useReports } from '@/lib/reports';
import {
  platformLabel,
  STATUS_EDGE,
  STATUS_LABELS,
  STATUS_ORDER,
  TYPE_ICONS,
  TYPE_LABELS,
} from '@/lib/reportStatus';
import type { ReportStatus } from '@/lib/types';
import { colors, fonts, radius, space, type } from '@/theme';

// The rail carries the forward path only; Dismissed is deliberately off it.
const FORWARD_STATUSES = STATUS_ORDER.filter((s) => s !== 'dismissed');

// Past this length the testimony steps down a size, so a 2000-character report reads as a
// document rather than a poster. Scale contrast is what does the emphasis on this screen —
// there is no quote rule and no background wash.
const LONG_DESCRIPTION = 280;

// Shorter labels for the rail, where four nodes share one row. "For discussion" becomes
// "Discussion" here only — the full label is what the pill and the sheet show.
const RAIL_LABELS: Record<ReportStatus, string> = {
  ...STATUS_LABELS,
  discuss: 'Discussion',
};

export default function ReportDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { reports, changeStatus: moveReport, addNote, editNote } = useReports();
  const insets = useSafeAreaInsets();
  const [saving, setSaving] = useState<ReportStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  // The list is already loaded above the tabs, so the detail screen reads from it rather
  // than refetching one report — there is no single-report endpoint and no need for one.
  const report = useMemo(() => (reports ?? []).find((r) => r.id === id), [reports, id]);

  // browser · OS · device model, null parts dropped: the three facts are read together, so
  // they share one row instead of stacking three more labels under the testimony. Empty —
  // and the row below omitted — on pre-v1.2 reports, which send none of them.
  const device = report
    ? [report.browser, report.os, report.deviceModel].filter(Boolean).join(' · ')
    : '';

  async function changeStatus(status: ReportStatus) {
    // Selecting the current status sends no request (spec §4).
    if (!report || status === report.status) return;
    setSaving(status);
    setError(null);
    try {
      // The provider writes the result back into the shared list, which is what updates the
      // inbox, the filter chips, and the tab badge at once — no manual refresh anywhere.
      await moveReport(report.id, status);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update the status.');
    } finally {
      setSaving(null);
    }
  }

  return (
    <View style={styles.screen}>
      <View style={[styles.topBar, { paddingTop: insets.top + space.sm }]}>
        <Pressable onPress={() => router.back()} hitSlop={10} style={styles.backBtn}>
          <Feather name="chevron-left" size={24} color={colors.brand} />
        </Pressable>
        <Text style={styles.topTitle}>Report</Text>
        <View style={styles.backBtn} />
      </View>

      {!report ? (
        <View style={styles.missing}>
          {reports === null ? (
            <ActivityIndicator color={colors.brand} />
          ) : (
            <>
              <Feather name="inbox" size={28} color={colors.textFaint} />
              <Text style={styles.missingText}>That report is no longer in the inbox.</Text>
            </>
          )}
        </View>
      ) : (
        <Scroll contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <View style={styles.typeRow}>
            <View style={styles.glyph}>
              <Feather name={TYPE_ICONS[report.type]} size={17} color={colors.brand} />
            </View>
            <Eyebrow>{TYPE_LABELS[report.type]}</Eyebrow>
            <View style={styles.typeRowSpacer} />
            {/* Where this report stands, visible on arrival rather than four blocks down. */}
            <ReportStatusPill status={report.status} />
          </View>

          {/* The reporter's own words open the screen, unboxed and never editable here: on a
              page carrying two voices, the foreign testimony outranks the team's apparatus. */}
          <Text
            style={[
              styles.testimony,
              report.description.length > LONG_DESCRIPTION && styles.testimonyLong,
            ]}>
            {report.description}
          </Text>

          <ReportScreenshots reportId={report.id} ordinals={report.screenshotOrdinals} />

          <Card style={styles.metaCard}>
            <MetaRow label="Reporter" value={report.reporterName ?? 'Signed out'} />
            <MetaRow label="Platform" value={platformLabel(report.platform, report.appVersion)} />
            {/* Where they were when they opened the report flow — Largata's wording,
                rendered verbatim; absent on reports from builds that don't send it. */}
            {report.screen && <MetaRow label="Screen" value={report.screen} />}
            {/* What they were running when they filed — Largata's wording verbatim. */}
            {device !== '' && <MetaRow label="Device" value={device} />}
            <MetaRow label="Submitted" value={activityLabel(report.submittedAt, report.submittedAt).when} />
            {/* Received differs from submitted whenever Largata had to retry delivery. */}
            <MetaRow label="Received" value={activityLabel(report.receivedAt, report.receivedAt).when} />
            <MetaRow
              label="Last change"
              value={
                report.statusChangedAt && report.statusChangedByName
                  ? `${report.statusChangedByName} · ${activityLabel(report.statusChangedAt, report.statusChangedAt).when}`
                  : 'Not triaged yet'
              }
            />
          </Card>

          <View style={styles.statusBlock}>
            <Eyebrow>Status</Eyebrow>

            {/* A lifecycle RAIL, not a stepper: the line shows where a report usually travels,
                but every node is tappable from any state — the rail is a map, not a gate. */}
            <View style={styles.rail}>
              <View style={styles.railLine} />
              {FORWARD_STATUSES.map((status) => {
                const active = report.status === status;
                const busy = saving === status;
                return (
                  <Pressable
                    key={status}
                    onPress={() => changeStatus(status)}
                    disabled={saving !== null}
                    accessibilityRole="button"
                    accessibilityState={{ selected: active, disabled: saving !== null }}
                    accessibilityLabel={`Move to ${STATUS_LABELS[status]}`}
                    style={({ pressed, hovered }: PressState) => [
                      styles.node,
                      pressed && styles.nodePressed,
                      hovered && styles.nodeHovered,
                    ]}>
                    <View
                      style={[
                        styles.nodeDot,
                        { borderColor: STATUS_EDGE[status] },
                        active && { backgroundColor: STATUS_EDGE[status] },
                      ]}>
                      {busy && <ActivityIndicator size="small" color={colors.brand} />}
                    </View>
                    <Text style={[styles.nodeLabel, active && styles.nodeLabelActive]}>
                      {RAIL_LABELS[status]}
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            {/* Dismiss lives apart from the rail: "won't act on this" is not the step after
                Done, and putting it in line would read as the end of the same journey. */}
            <Pressable
              onPress={() => changeStatus('dismissed')}
              disabled={saving !== null}
              accessibilityRole="button"
              accessibilityState={{
                selected: report.status === 'dismissed',
                disabled: saving !== null,
              }}
              accessibilityLabel="Dismiss this report, we won't act on it"
              style={({ pressed, hovered }: PressState) => [
                styles.dismiss,
                report.status === 'dismissed' && styles.dismissActive,
                hovered && styles.dismissHover,
                pressed && styles.dismissPressed,
              ]}>
              {saving === 'dismissed' ? (
                <ActivityIndicator size="small" color={colors.textMuted} />
              ) : (
                <>
                  <Feather name="slash" size={14} color={colors.textMuted} />
                  <Text style={styles.dismissText}>
                    {report.status === 'dismissed' ? 'Dismissed' : 'Dismiss this report'}
                  </Text>
                </>
              )}
            </Pressable>

            <Text style={styles.statusHint}>
              Any of us can move a report — whoever is free triages.
            </Text>
          </View>

          {error && <Text style={styles.error}>{error}</Text>}

          {/* Last on the page on purpose: you read what happened, then what the team decided. */}
          <ReportNotes
            notes={report.notes}
            onAdd={(body) => addNote(report.id, body)}
            onEdit={(noteId, body) => editNote(report.id, noteId, body)}
          />
        </Scroll>
      )}
    </View>
  );
}

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.metaRow}>
      <Text style={styles.metaLabel}>{label}</Text>
      <Text style={styles.metaValue} numberOfLines={2}>
        {value}
      </Text>
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

  content: { padding: space.lg, gap: space.lg, paddingBottom: space.xxl },
  typeRow: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  typeRowSpacer: { flex: 1 },
  glyph: {
    width: 32,
    height: 32,
    borderRadius: radius.pill,
    backgroundColor: colors.brandSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // Medium weight, full ink, larger than anything else on the screen — the words are the
  // material here, not another labelled field.
  testimony: { ...type.body, fontFamily: fonts.medium, fontSize: 19, lineHeight: 28 },
  testimonyLong: { fontSize: 17, lineHeight: 26 },

  metaCard: { gap: space.sm },
  metaRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space.md },
  metaLabel: { ...type.caption, width: 92 },
  metaValue: { ...type.bodyMedium, flex: 1 },

  statusBlock: { gap: space.sm },
  statusHint: { ...type.caption, marginTop: space.xs },

  rail: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: space.sm,
    paddingHorizontal: space.xs,
  },
  // The connecting line sits behind the nodes, inset so it doesn't poke out either end.
  railLine: {
    position: 'absolute',
    left: 34,
    right: 34,
    top: 13,
    height: 2,
    backgroundColor: colors.hairline,
  },
  node: { alignItems: 'center', gap: 6, minWidth: 44, minHeight: 44, cursor: 'pointer', ...noTextSelect },
  nodePressed: { opacity: 0.7 },
  nodeHovered: { opacity: 0.9 },
  nodeDot: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 2,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nodeLabel: { ...type.caption, fontSize: 11, textAlign: 'center' },
  nodeLabelActive: { fontFamily: fonts.bold, color: colors.text },

  dismiss: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    minHeight: 44,
    marginTop: space.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.hairline,
    backgroundColor: colors.surface,
    cursor: 'pointer',
    ...noTextSelect,
  },
  dismissActive: { backgroundColor: colors.hairline },
  dismissHover: { backgroundColor: colors.bg },
  dismissPressed: { opacity: 0.8 },
  dismissText: { fontFamily: fonts.bold, fontSize: 13, color: colors.textMuted },

  missing: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.sm },
  missingText: { ...type.body, color: colors.textMuted },
  error: { ...type.body, color: colors.brand },
});

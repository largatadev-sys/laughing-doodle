import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { Card, Eyebrow, FadeInView, PillButton } from '@/components/ui';
import { noTextSelect, type PressState } from '@/components/ui/press';
import { useAuth } from '@/lib/auth';
import { activityLabel, initials } from '@/lib/datetime';
import type { ReportNote } from '@/lib/types';
import { colorForName, colors, fonts, radius, readableTextOn, space, type } from '@/theme';

const NOTE_MAX_LENGTH = 2000;
// The counter is noise until the ceiling is actually in reach — a live character count on a
// field nobody fills is the interface talking about itself.
const COUNTER_APPEARS_AT = NOTE_MAX_LENGTH - 100;

interface ReportNotesProps {
  notes: ReportNote[];
  onAdd: (body: string) => Promise<void>;
  onEdit: (noteId: string, body: string) => Promise<void>;
}

/**
 * The team's ledger on a report: why we did what we did, kept where the decision was made.
 *
 * The log is append-only — nothing here deletes, and the API has no route that could. Each
 * entry is signed testimony from one Member, and **only its author can reword it** (ADR-012 as
 * revised) — the same ownership rule time entries obey, so the Edit control simply isn't there
 * on someone else's note. An edited note still carries an "Edited · when" stamp, because a
 * teammate who read it yesterday deserves to know it has changed.
 */
export function ReportNotes({ notes, onAdd, onEdit }: ReportNotesProps) {
  const { session } = useAuth();
  const meId = session?.user.id ?? null;
  const [draft, setDraft] = useState('');
  const [composing, setComposing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function add() {
    const body = draft.trim();
    if (!body || saving) return;
    setSaving(true);
    setError(null);
    try {
      await onAdd(body);
      setDraft('');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save that note.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <View style={styles.section}>
      <Eyebrow>Notes</Eyebrow>

      {notes.length === 0 ? (
        <Text style={styles.empty}>
          No notes yet — record the decision here so it isn&apos;t lost to chat.
        </Text>
      ) : (
        // One flush card with hairline dividers: the same "single surface, many entries"
        // language the inbox list uses, because this is a log too.
        <Card flush style={styles.ledger}>
          {notes.map((note, i) => (
            <View key={note.id} style={i > 0 ? styles.divider : undefined}>
              <NoteEntry
                note={note}
                mine={note.authorId === meId}
                onEdit={(body) => onEdit(note.id, body)}
              />
            </View>
          ))}
        </Card>
      )}

      <View style={styles.composer}>
        <TextInput
          style={[styles.input, composing && styles.inputFocused]}
          placeholder="Write a note — decisions, context, next steps"
          placeholderTextColor={colors.textFaint}
          multiline
          maxLength={NOTE_MAX_LENGTH}
          value={draft}
          onFocus={() => setComposing(true)}
          onBlur={() => setComposing(false)}
          onChangeText={setDraft}
          accessibilityLabel="Write a note"
        />
        <View style={styles.composerFoot}>
          {draft.length >= COUNTER_APPEARS_AT && (
            <Text style={styles.counter}>
              {draft.length} / {NOTE_MAX_LENGTH}
            </Text>
          )}
          <PillButton
            label="Add note"
            onPress={add}
            loading={saving}
            disabled={draft.trim().length === 0}
            style={styles.addButton}
          />
        </View>
        {error && <Text style={styles.error}>{error}</Text>}
      </View>
    </View>
  );
}

function NoteEntry({
  note,
  mine,
  onEdit,
}: {
  note: ReportNote;
  /** Whether the signed-in Member wrote this one — the only person who may reword it. */
  mine: boolean;
  onEdit: (body: string) => Promise<void>;
}) {
  const [draft, setDraft] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const editing = draft !== null;
  const canSave = !saving && (draft ?? '').trim().length > 0;

  async function save() {
    const body = (draft ?? '').trim();
    if (!body || saving) return;
    setSaving(true);
    setError(null);
    try {
      await onEdit(body);
      setDraft(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save that edit.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <FadeInView style={styles.entry} distance={8}>
      <View style={styles.entryMeta}>
        <PersonDot name={note.authorName} />
        <Text style={styles.author}>{note.authorName}</Text>
        <Text style={styles.when}>{activityLabel(note.createdAt, note.createdAt).when}</Text>
        <View style={styles.metaSpacer} />
        {/* A visible control, not a hidden gesture — the long-press lesson from the inbox. It
            is absent, not disabled, on someone else's note: a control you can never use is
            noise, and the server would refuse the write anyway. */}
        {mine && !editing && (
          <Pressable
            onPress={() => setDraft(note.body)}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="Edit your note"
            style={({ pressed, hovered }: PressState) => [
              styles.editBtn,
              hovered && styles.editBtnHover,
              pressed && styles.editBtnPressed,
            ]}>
            <Text style={styles.editText}>Edit</Text>
          </Pressable>
        )}
      </View>

      {editing ? (
        <>
          <TextInput
            style={[styles.input, styles.inputFocused, styles.editInput]}
            multiline
            autoFocus
            maxLength={NOTE_MAX_LENGTH}
            value={draft ?? ''}
            onChangeText={setDraft}
            accessibilityLabel="Edit note"
          />
          <View style={styles.editActions}>
            <Pressable
              onPress={() => {
                setDraft(null);
                setError(null);
              }}
              disabled={saving}
              accessibilityRole="button"
              style={({ pressed, hovered }: PressState) => [
                styles.editBtn,
                hovered && styles.editBtnHover,
                pressed && styles.editBtnPressed,
              ]}>
              <Text style={styles.editText}>Cancel</Text>
            </Pressable>
            <Pressable
              onPress={save}
              disabled={!canSave}
              accessibilityRole="button"
              accessibilityState={{ disabled: !canSave }}
              style={({ pressed, hovered }: PressState) => [
                styles.editBtn,
                hovered && styles.editBtnHover,
                pressed && styles.editBtnPressed,
                !canSave && styles.editBtnDisabled,
              ]}>
              <Text style={[styles.editText, styles.saveText]}>
                {saving ? 'Saving…' : 'Save'}
              </Text>
            </Pressable>
          </View>
        </>
      ) : (
        <Text style={styles.body}>{note.body}</Text>
      )}

      {/* Only ever the last edit: a changed decision is meant to become a new note, not a
          revision history on this one. No name here — the editor is always the author now,
          so naming them would just repeat the line above. */}
      {note.editedAt && (
        <Text style={styles.stamp}>
          Edited · {activityLabel(note.editedAt, note.editedAt).when}
        </Text>
      )}

      {error && <Text style={styles.error}>{error}</Text>}
    </FadeInView>
  );
}

// People are the only colourful thing in this app, and only *our* people: the note's author is
// a Member. The foreign reporter never gets a hue — they are not part of this conversation.
function PersonDot({ name }: { name: string }) {
  const hue = colorForName(name);
  return (
    <View style={[styles.dot, { backgroundColor: hue }]}>
      <Text style={[styles.dotText, { color: readableTextOn(hue) }]}>{initials(name).charAt(0)}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  section: { gap: space.sm },
  empty: { ...type.body, color: colors.textMuted },

  ledger: { marginTop: space.xs },
  divider: { borderTopWidth: 1, borderTopColor: colors.hairline },

  entry: { padding: space.md, gap: 6 },
  entryMeta: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  metaSpacer: { flex: 1 },
  author: { ...type.caption, fontFamily: fonts.bold, color: colors.text },
  when: { ...type.caption },
  body: { ...type.body },
  stamp: { ...type.caption, fontSize: 11.5 },

  dot: { width: 18, height: 18, borderRadius: 9, alignItems: 'center', justifyContent: 'center' },
  dotText: { fontFamily: fonts.bold, fontSize: 9.5 },

  // Note *bodies* stay selectable on purpose — you should be able to copy a decision out of
  // the ledger. Only the controls opt out.
  editBtn: {
    minHeight: 28,
    justifyContent: 'center',
    paddingHorizontal: 6,
    borderRadius: radius.sm,
    cursor: 'pointer',
    ...noTextSelect,
  },
  editBtnHover: { backgroundColor: colors.brandSoft },
  editBtnPressed: { opacity: 0.7 },
  editBtnDisabled: { opacity: 0.4 },
  editText: { ...type.caption, fontFamily: fonts.bold },
  saveText: { color: colors.brand },
  editActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: space.sm },

  composer: { gap: space.sm },
  input: {
    minHeight: 76,
    borderWidth: 1,
    borderColor: colors.hairline,
    borderRadius: radius.md,
    paddingHorizontal: space.md,
    paddingVertical: space.md,
    textAlignVertical: 'top',
    fontFamily: fonts.regular,
    fontSize: 15,
    lineHeight: 22,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  inputFocused: { borderColor: colors.brand },
  editInput: { minHeight: 64 },
  composerFoot: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: space.md },
  counter: { ...type.caption, fontSize: 11.5 },
  addButton: { minHeight: 44, paddingHorizontal: space.lg },
  error: { ...type.caption, color: colors.brand, fontFamily: fonts.semibold },
});

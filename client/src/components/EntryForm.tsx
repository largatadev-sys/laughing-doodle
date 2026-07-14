import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { DatePickerField, PillButton } from '@/components/ui';
import { ApiError } from '@/lib/apiClient';
import { addDays, formatDuration, toISODate } from '@/lib/datetime';
import { colors, fonts, radius, space, type } from '@/theme';

export interface EntryFormValues {
  entryDate: string;
  durationMin: number;
  description: string;
}

interface EntryFormProps {
  initialValues?: EntryFormValues;
  submitLabel: string;
  onSubmit: (values: EntryFormValues) => Promise<void>;
}

type FieldErrors = Partial<Record<'entryDate' | 'durationMin' | 'description', string>>;

// Backend LocalDate deserialization 500s (not 400s) on a malformed date, so guard it here.
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const DURATION_CHIPS = [30, 60, 120, 240, 480];

export function EntryForm({ initialValues, submitLabel, onSubmit }: EntryFormProps) {
  const [entryDate, setEntryDate] = useState(initialValues?.entryDate ?? toISODate(new Date()));
  const [durationMin, setDurationMin] = useState(
    initialValues ? String(initialValues.durationMin) : '',
  );
  const [description, setDescription] = useState(initialValues?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [focused, setFocused] = useState<string | null>(null);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!DATE_PATTERN.test(entryDate)) errors.entryDate = 'Enter a date as YYYY-MM-DD.';
    const parsed = Number(durationMin);
    if (!durationMin || !Number.isInteger(parsed) || parsed <= 0) {
      errors.durationMin = 'Enter a whole number of minutes greater than 0.';
    }
    if (!description.trim()) errors.description = 'Add a short description.';
    return errors;
  }

  async function handleSubmit() {
    setSubmitError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      await onSubmit({ entryDate, durationMin: Number(durationMin), description });
    } catch (e) {
      if (e instanceof ApiError && e.details) {
        const serverErrors: FieldErrors = {};
        for (const key of ['entryDate', 'durationMin', 'description'] as const) {
          const detail = e.details[key];
          if (typeof detail === 'string') serverErrors[key] = detail;
        }
        if (Object.keys(serverErrors).length > 0) setFieldErrors(serverErrors);
        else setSubmitError(e.message);
      } else if (e instanceof ApiError) {
        setSubmitError(e.message);
      } else {
        setSubmitError('Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  const inputStyle = (field: string, hasError: boolean) => [
    styles.input,
    focused === field && styles.inputFocused,
    hasError && styles.inputError,
  ];

  return (
    <View style={styles.container}>
      {/* Entry date */}
      <View style={styles.field}>
        <Text style={styles.label}>When was the work done?</Text>
        <DatePickerField value={entryDate} onChange={setEntryDate} />
        <View style={styles.chipRow}>
          <Chip label="Today" onPress={() => setEntryDate(toISODate(new Date()))} />
          <Chip label="Yesterday" onPress={() => setEntryDate(toISODate(addDays(new Date(), -1)))} />
        </View>
        {fieldErrors.entryDate && <Text style={styles.error}>{fieldErrors.entryDate}</Text>}
      </View>

      {/* Duration */}
      <View style={styles.field}>
        <Text style={styles.label}>How long did it take?</Text>
        <TextInput
          style={inputStyle('durationMin', !!fieldErrors.durationMin)}
          placeholder="Minutes, e.g. 90"
          placeholderTextColor={colors.textFaint}
          keyboardType="number-pad"
          value={durationMin}
          onFocus={() => setFocused('durationMin')}
          onBlur={() => setFocused(null)}
          onChangeText={setDurationMin}
        />
        <View style={styles.chipRow}>
          {DURATION_CHIPS.map((m) => (
            <Chip
              key={m}
              label={formatDuration(m)}
              active={Number(durationMin) === m}
              onPress={() => setDurationMin(String(m))}
            />
          ))}
        </View>
        {fieldErrors.durationMin && <Text style={styles.error}>{fieldErrors.durationMin}</Text>}
      </View>

      {/* Description */}
      <View style={styles.field}>
        <Text style={styles.label}>What did you work on?</Text>
        <TextInput
          style={[inputStyle('description', !!fieldErrors.description), styles.multiline]}
          placeholder="A short description the team will see"
          placeholderTextColor={colors.textFaint}
          multiline
          maxLength={500}
          value={description}
          onFocus={() => setFocused('description')}
          onBlur={() => setFocused(null)}
          onChangeText={setDescription}
        />
        {fieldErrors.description && <Text style={styles.error}>{fieldErrors.description}</Text>}
      </View>

      {submitError && <Text style={styles.error}>{submitError}</Text>}

      <PillButton
        label={submitLabel}
        onPress={handleSubmit}
        loading={isSubmitting}
        style={styles.submit}
      />
    </View>
  );
}

function Chip({ label, onPress, active }: { label: string; onPress: () => void; active?: boolean }) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [styles.chip, active && styles.chipActive, pressed && styles.chipPressed]}>
      <Text style={[styles.chipText, active && styles.chipTextActive]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { gap: space.xl },
  field: { gap: space.sm },
  label: { ...type.bodyMedium, fontFamily: fonts.bold },
  input: {
    borderWidth: 1.5,
    borderColor: colors.hairline,
    borderRadius: radius.md,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    fontSize: 16,
    fontFamily: fonts.medium,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  inputFocused: { borderColor: colors.brand },
  inputError: { borderColor: colors.brand, backgroundColor: colors.brandSoft },
  multiline: { minHeight: 96, textAlignVertical: 'top', paddingTop: space.md },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm },
  chip: {
    paddingHorizontal: space.md,
    paddingVertical: 7,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    backgroundColor: colors.surface,
  },
  chipActive: { backgroundColor: colors.brand, borderColor: colors.brand },
  chipPressed: { backgroundColor: colors.brandSoft },
  chipText: { fontFamily: fonts.semibold, fontSize: 13, color: colors.brand },
  chipTextActive: { color: colors.onBrand },
  error: { ...type.caption, color: colors.brand, fontFamily: fonts.semibold },
  submit: { marginTop: space.sm },
});

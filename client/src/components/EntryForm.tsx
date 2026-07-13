import { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { ApiError } from '@/lib/apiClient';

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

// Backend LocalDate deserialization fails outside validation (a 500, not a 400) on a
// malformed date string, so this is checked client-side rather than left to the server.
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export function EntryForm({ initialValues, submitLabel, onSubmit }: EntryFormProps) {
  const [entryDate, setEntryDate] = useState(initialValues?.entryDate ?? '');
  const [durationMin, setDurationMin] = useState(
    initialValues ? String(initialValues.durationMin) : '',
  );
  const [description, setDescription] = useState(initialValues?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!DATE_PATTERN.test(entryDate)) {
      errors.entryDate = 'Enter a date as YYYY-MM-DD.';
    }
    const parsedDuration = Number(durationMin);
    if (!durationMin || !Number.isInteger(parsedDuration) || parsedDuration <= 0) {
      errors.durationMin = 'Enter a whole number of minutes greater than 0.';
    }
    if (!description.trim()) {
      errors.description = 'Description is required.';
    }
    return errors;
  }

  async function handleSubmit() {
    setSubmitError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      await onSubmit({ entryDate, durationMin: Number(durationMin), description });
    } catch (e) {
      if (e instanceof ApiError && e.details) {
        const serverErrors: FieldErrors = {};
        for (const key of ['entryDate', 'durationMin', 'description'] as const) {
          const detail = e.details[key];
          if (typeof detail === 'string') {
            serverErrors[key] = detail;
          }
        }
        if (Object.keys(serverErrors).length > 0) {
          setFieldErrors(serverErrors);
        } else {
          setSubmitError(e.message);
        }
      } else if (e instanceof ApiError) {
        setSubmitError(e.message);
      } else {
        setSubmitError('Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <View style={styles.container}>
      <View style={styles.field}>
        <Text style={styles.label}>Entry date</Text>
        <TextInput
          style={styles.input}
          placeholder="YYYY-MM-DD"
          autoCapitalize="none"
          autoCorrect={false}
          value={entryDate}
          onChangeText={setEntryDate}
        />
        {fieldErrors.entryDate && <Text style={styles.error}>{fieldErrors.entryDate}</Text>}
      </View>

      <View style={styles.field}>
        <Text style={styles.label}>Duration (minutes)</Text>
        <TextInput
          style={styles.input}
          placeholder="60"
          keyboardType="number-pad"
          value={durationMin}
          onChangeText={setDurationMin}
        />
        {fieldErrors.durationMin && <Text style={styles.error}>{fieldErrors.durationMin}</Text>}
      </View>

      <View style={styles.field}>
        <Text style={styles.label}>Description</Text>
        <TextInput
          style={[styles.input, styles.multiline]}
          placeholder="What did you work on?"
          multiline
          maxLength={500}
          value={description}
          onChangeText={setDescription}
        />
        {fieldErrors.description && <Text style={styles.error}>{fieldErrors.description}</Text>}
      </View>

      {submitError && <Text style={styles.error}>{submitError}</Text>}

      <Pressable
        style={[styles.button, isSubmitting && styles.buttonDisabled]}
        onPress={handleSubmit}
        disabled={isSubmitting}>
        {isSubmitting ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.buttonText}>{submitLabel}</Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 24,
    gap: 16,
  },
  field: {
    gap: 4,
  },
  label: {
    fontWeight: '600',
    color: '#374151',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
  },
  multiline: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  error: {
    color: '#b91c1c',
  },
  button: {
    backgroundColor: '#1d4ed8',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 16,
  },
});

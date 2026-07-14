import { useState } from 'react';
import { router } from 'expo-router';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import { FormScreen } from '@/components/FormScreen';
import { PillButton } from '@/components/ui';
import { apiClient, ApiError, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { colors, fonts, radius, space, type } from '@/theme';

export default function ChangeName() {
  const { session, logout, updateUser } = useAuth();
  const [name, setName] = useState(session?.user.name ?? '');
  const [error, setError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [focused, setFocused] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit() {
    setError(null);
    setSubmitError(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setError('Enter your name.');
      return;
    }
    if (!session) return;

    setIsSubmitting(true);
    try {
      const updated = await apiClient.updateName(trimmed, session.token);
      await updateUser(updated); // header + profile reflect the new name immediately
      router.back();
    } catch (e) {
      if (e instanceof UnauthorizedError) return logout();
      if (e instanceof ApiError && typeof e.details?.name === 'string') {
        setError(`Your name ${e.details.name}.`);
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
    <FormScreen title="Change name" subtitle="This is the name teammates see on your entries.">
      <View style={styles.container}>
        <View style={styles.field}>
          <Text style={styles.label}>Display name</Text>
          <TextInput
            style={[styles.input, focused && styles.inputFocused, !!error && styles.inputError]}
            placeholder="Your name"
            placeholderTextColor={colors.textFaint}
            value={name}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            onChangeText={setName}
            onSubmitEditing={handleSubmit}
            maxLength={100}
            autoFocus
          />
          {error && <Text style={styles.error}>{error}</Text>}
        </View>

        {submitError && <Text style={styles.error}>{submitError}</Text>}

        <PillButton label="Save name" onPress={handleSubmit} loading={isSubmitting} style={styles.submit} />
      </View>
    </FormScreen>
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
  error: { ...type.caption, color: colors.brand, fontFamily: fonts.semibold },
  submit: { marginTop: space.sm },
});

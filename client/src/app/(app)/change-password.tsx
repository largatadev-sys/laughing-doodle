import { useState } from 'react';
import { router } from 'expo-router';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import { Feather } from '@expo/vector-icons';

import { FormScreen } from '@/components/FormScreen';
import { PillButton } from '@/components/ui';
import { apiClient, ApiError, UnauthorizedError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { colors, fonts, radius, space, type } from '@/theme';

const MIN_LENGTH = 8;
type PwField = 'currentPassword' | 'newPassword' | 'confirmPassword';
type FieldErrors = Partial<Record<PwField, string>>;

export default function ChangePassword() {
  const { session, logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [focused, setFocused] = useState<PwField | null>(null);
  const [done, setDone] = useState(false);

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    if (!currentPassword) next.currentPassword = 'Enter your current password.';
    if (newPassword.length < MIN_LENGTH) next.newPassword = `Use at least ${MIN_LENGTH} characters.`;
    else if (newPassword === currentPassword) next.newPassword = 'Choose a password different from your current one.';
    if (confirmPassword !== newPassword) next.confirmPassword = 'This doesn’t match the new password.';
    return next;
  }

  async function handleSubmit() {
    setSubmitError(null);
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0 || !session) return;

    setIsSubmitting(true);
    try {
      await apiClient.changePassword(currentPassword, newPassword, session.token);
      setDone(true);
    } catch (e) {
      if (e instanceof UnauthorizedError) return logout();
      if (e instanceof ApiError && e.details) {
        const serverErrors: FieldErrors = {};
        for (const key of ['currentPassword', 'newPassword'] as const) {
          const detail = e.details[key];
          if (typeof detail === 'string') serverErrors[key] = `Your ${key === 'currentPassword' ? 'current password' : 'new password'} ${detail}.`;
        }
        if (Object.keys(serverErrors).length > 0) setErrors(serverErrors);
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

  if (done) {
    return (
      <FormScreen title="Password updated" subtitle="Your new password is ready.">
        <View style={styles.success}>
          <View style={styles.successBadge}>
            <Feather name="check" size={30} color={colors.onBrand} />
          </View>
          <Text style={styles.successText}>
            You’ll use your new password next time you log in. Your current session stays signed in.
          </Text>
          <PillButton label="Done" onPress={() => router.back()} />
        </View>
      </FormScreen>
    );
  }

  const inputStyle = (field: PwField) => [
    styles.input,
    focused === field && styles.inputFocused,
    errors[field] && styles.inputError,
  ];

  return (
    <FormScreen title="Change password" subtitle="Update the password you log in with.">
      <View style={styles.container}>
        <Field label="Current password" error={errors.currentPassword}>
          <TextInput
            style={inputStyle('currentPassword')}
            placeholder="Your current password"
            placeholderTextColor={colors.textFaint}
            secureTextEntry
            autoCapitalize="none"
            value={currentPassword}
            onFocus={() => setFocused('currentPassword')}
            onBlur={() => setFocused(null)}
            onChangeText={setCurrentPassword}
          />
        </Field>

        <Field label="New password" error={errors.newPassword} hint={`At least ${MIN_LENGTH} characters.`}>
          <TextInput
            style={inputStyle('newPassword')}
            placeholder="Your new password"
            placeholderTextColor={colors.textFaint}
            secureTextEntry
            autoCapitalize="none"
            value={newPassword}
            onFocus={() => setFocused('newPassword')}
            onBlur={() => setFocused(null)}
            onChangeText={setNewPassword}
          />
        </Field>

        <Field label="Confirm new password" error={errors.confirmPassword}>
          <TextInput
            style={inputStyle('confirmPassword')}
            placeholder="Re-enter your new password"
            placeholderTextColor={colors.textFaint}
            secureTextEntry
            autoCapitalize="none"
            value={confirmPassword}
            onFocus={() => setFocused('confirmPassword')}
            onBlur={() => setFocused(null)}
            onChangeText={setConfirmPassword}
            onSubmitEditing={handleSubmit}
          />
        </Field>

        {submitError && <Text style={styles.error}>{submitError}</Text>}

        <PillButton
          label="Update password"
          onPress={handleSubmit}
          loading={isSubmitting}
          style={styles.submit}
        />
      </View>
    </FormScreen>
  );
}

function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      {children}
      {error ? (
        <Text style={styles.error}>{error}</Text>
      ) : hint ? (
        <Text style={styles.hint}>{hint}</Text>
      ) : null}
    </View>
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
  hint: { ...type.caption },
  error: { ...type.caption, color: colors.brand, fontFamily: fonts.semibold },
  submit: { marginTop: space.sm },

  success: { alignItems: 'center', gap: space.lg, paddingVertical: space.xl },
  successBadge: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: colors.brand,
    alignItems: 'center',
    justifyContent: 'center',
  },
  successText: { ...type.body, color: colors.textMuted, textAlign: 'center' },
});

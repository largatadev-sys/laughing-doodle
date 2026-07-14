import { useState } from 'react';
import { router } from 'expo-router';
import { KeyboardAvoidingView, Platform, StyleSheet, Text, TextInput, View } from 'react-native';

import { Logo, PillButton } from '@/components/ui';
import { ApiError } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';
import { colors, fonts, radius, space, type } from '@/theme';

export default function Login() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [focused, setFocused] = useState<string | null>(null);

  async function handleSubmit() {
    setError(null);
    setIsSubmitting(true);
    try {
      await login(username, password);
      router.replace('/');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Unable to log in. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.screen}>
      <View style={styles.inner}>
        <View style={styles.brand}>
          <Logo size={34} tagline="Team Worklog" />
        </View>

        <View style={styles.form}>
          <TextInput
            style={[styles.input, focused === 'u' && styles.inputFocused]}
            placeholder="Username"
            placeholderTextColor={colors.brand}
            autoCapitalize="none"
            autoCorrect={false}
            value={username}
            onFocus={() => setFocused('u')}
            onBlur={() => setFocused(null)}
            onChangeText={setUsername}
            onSubmitEditing={handleSubmit}
          />
          <TextInput
            style={[styles.input, focused === 'p' && styles.inputFocused]}
            placeholder="Password"
            placeholderTextColor={colors.brand}
            secureTextEntry
            value={password}
            onFocus={() => setFocused('p')}
            onBlur={() => setFocused(null)}
            onChangeText={setPassword}
            onSubmitEditing={handleSubmit}
          />

          {error && <Text style={styles.error}>{error}</Text>}

          <PillButton
            label="Log in"
            onPress={handleSubmit}
            loading={isSubmitting}
            style={styles.button}
          />
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.surface },
  inner: { flex: 1, justifyContent: 'center', paddingHorizontal: space.xl, gap: space.xxl },
  brand: { alignItems: 'center' },
  form: { gap: space.md },
  input: {
    borderWidth: 1.5,
    borderColor: colors.cardBorder,
    borderRadius: radius.pill,
    paddingHorizontal: space.xl,
    paddingVertical: space.md + 2,
    fontSize: 16,
    fontFamily: fonts.medium,
    color: colors.text,
  },
  inputFocused: { borderColor: colors.brand },
  error: { ...type.caption, color: colors.brand, fontFamily: fonts.semibold, textAlign: 'center' },
  button: { marginTop: space.sm },
});

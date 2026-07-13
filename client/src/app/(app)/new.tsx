import { router, Stack } from 'expo-router';

import { EntryForm, type EntryFormValues } from '@/components/EntryForm';
import { apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';

export default function NewEntry() {
  const { session } = useAuth();

  async function handleSubmit(values: EntryFormValues) {
    if (!session) {
      return;
    }
    await apiClient.createEntry(values, session.token);
    router.back();
  }

  return (
    <>
      <Stack.Screen options={{ title: 'New Entry' }} />
      <EntryForm submitLabel="Create entry" onSubmit={handleSubmit} />
    </>
  );
}

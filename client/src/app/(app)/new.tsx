import { router } from 'expo-router';

import { EntryForm, type EntryFormValues } from '@/components/EntryForm';
import { FormScreen } from '@/components/FormScreen';
import { apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';

export default function NewEntry() {
  const { session } = useAuth();

  async function handleSubmit(values: EntryFormValues) {
    if (!session) return;
    await apiClient.createEntry(values, session.token);
    router.back();
  }

  return (
    <FormScreen title="Log time" subtitle="Add a chunk of work to the team log.">
      <EntryForm submitLabel="Log it" onSubmit={handleSubmit} />
    </FormScreen>
  );
}

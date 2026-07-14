import { router, useLocalSearchParams } from 'expo-router';

import { EntryForm, type EntryFormValues } from '@/components/EntryForm';
import { FormScreen } from '@/components/FormScreen';
import { apiClient } from '@/lib/apiClient';
import { useAuth } from '@/lib/auth';

export default function EditEntry() {
  const { session } = useAuth();
  const { id, entryDate, durationMin, description } = useLocalSearchParams<{
    id: string;
    entryDate: string;
    durationMin: string;
    description: string;
  }>();

  async function handleSubmit(values: EntryFormValues) {
    if (!session) return;
    await apiClient.updateEntry(Number(id), values, session.token);
    router.back();
  }

  return (
    <FormScreen title="Edit entry" subtitle="Update this chunk of work.">
      <EntryForm
        initialValues={{ entryDate, durationMin: Number(durationMin), description }}
        submitLabel="Save changes"
        onSubmit={handleSubmit}
      />
    </FormScreen>
  );
}

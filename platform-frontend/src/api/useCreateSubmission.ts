import { useMutation, useQueryClient } from '@tanstack/react-query';
import { WorkflowTypeSubmissionSchema, type WorkflowTypeSubmission } from '../types/WorkflowTypeSubmission';
import type { CreateSubmissionRequest } from '../store/wizardStore';
import client from './client';

export function useCreateSubmission() {
  const queryClient = useQueryClient();

  return useMutation<WorkflowTypeSubmission, Error, CreateSubmissionRequest>({
    mutationFn: async payload => {
      const { data } = await client.post('/workflow-type-submissions', payload);
      return WorkflowTypeSubmissionSchema.parse(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-type-submissions'] });
    },
  });
}

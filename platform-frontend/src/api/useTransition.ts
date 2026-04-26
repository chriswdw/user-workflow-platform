import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore';
import { WorkItemSchema, type WorkItem } from '../types/WorkItem';
import client from './client';

interface TransitionVariables {
  transition: string;
  additionalFields?: Record<string, unknown>;
}

export function useTransition(workItemId: string) {
  const tenantId = useAuthStore(s => s.tenantId);
  const queryClient = useQueryClient();

  return useMutation<WorkItem, Error, TransitionVariables>({
    mutationFn: async ({ transition, additionalFields }) => {
      const { data } = await client.post(`/work-items/${workItemId}/transitions`, {
        transition,
        additionalFields: additionalFields ?? {},
      });
      return WorkItemSchema.parse(data);
    },
    onSuccess: updated => {
      queryClient.setQueryData(['work-item', tenantId, workItemId], updated);
    },
  });
}

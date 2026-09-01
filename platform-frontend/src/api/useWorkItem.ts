import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore';
import { WorkItemSchema, type WorkItem } from '../types/WorkItem';
import { client } from './client';

export function useWorkItem(workItemId: string) {
  const tenantId = useAuthStore(s => s.tenantId);
  return useQuery<WorkItem>({
    queryKey: ['work-item', tenantId, workItemId],
    queryFn: async () => {
      const { data } = await client.get(`/work-items/${workItemId}`);
      return WorkItemSchema.parse(data);
    },
    enabled: !!workItemId && !!tenantId,
  });
}

import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { WorkItemSchema, type WorkItem } from '../types/WorkItem';
import { useAuthStore } from '../store/authStore';
import client from './client';

export function useWorkItems(workflowType: string) {
  const tenantId = useAuthStore(s => s.tenantId);
  return useQuery<WorkItem[]>({
    queryKey: ['work-items', tenantId, workflowType],
    queryFn: async () => {
      const { data } = await client.get('/work-items', { params: { workflowType } });
      return z.array(WorkItemSchema).parse(data);
    },
    enabled: !!tenantId && !!workflowType,
  });
}

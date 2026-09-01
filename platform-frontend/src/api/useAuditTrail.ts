import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore';
import { AuditEntrySchema, type AuditEntry } from '../types/AuditEntry';
import { z } from 'zod';
import { client } from './client';

export function useAuditTrail(workItemId: string) {
  const tenantId = useAuthStore(s => s.tenantId);
  return useQuery<AuditEntry[]>({
    queryKey: ['audit-trail', tenantId, workItemId],
    queryFn: async () => {
      const { data } = await client.get(`/audit/work-items/${workItemId}`);
      return z.array(AuditEntrySchema).parse(data);
    },
    enabled: !!workItemId && !!tenantId,
  });
}

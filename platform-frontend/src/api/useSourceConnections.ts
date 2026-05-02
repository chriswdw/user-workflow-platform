import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { SourceConnectionSchema, type SourceConnection } from '../types/WorkflowTypeSubmission';
import type { SourceType } from '../store/wizardStore';
import { useAuthStore } from '../store/authStore';
import client from './client';

export function useSourceConnections(type?: SourceType) {
  const tenantId = useAuthStore(s => s.tenantId);

  return useQuery<SourceConnection[]>({
    queryKey: ['source-connections', tenantId, type],
    queryFn: async () => {
      const { data } = await client.get('/source-connections', { params: type ? { type } : undefined });
      return z.array(SourceConnectionSchema).parse(data);
    },
    enabled: !!tenantId,
  });
}

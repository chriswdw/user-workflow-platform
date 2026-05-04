import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore';
import { DetailViewConfigSchema, type DetailViewConfig } from '../types/DetailViewConfig';
import { client } from './client';

export function useDetailViewConfig(workflowType: string) {
  const tenantId = useAuthStore(s => s.tenantId);
  return useQuery<DetailViewConfig>({
    queryKey: ['detail-view-config', tenantId, workflowType],
    queryFn: async () => {
      const { data } = await client.get(`/configs/detail-view/${workflowType}`);
      return DetailViewConfigSchema.parse(data);
    },
    enabled: !!workflowType && !!tenantId,
    staleTime: 5 * 60 * 1000,
  });
}

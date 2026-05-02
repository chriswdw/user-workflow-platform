import { useMutation, useQueryClient } from '@tanstack/react-query';
import { SourceConnectionSchema, type SourceConnection } from '../types/WorkflowTypeSubmission';
import client from './client';

interface CreateSourceConnectionVariables {
  displayName: string;
  type: 'KAFKA' | 'DB_POLL' | 'FILE_SHARE';
  config: Record<string, unknown>;
}

interface GrantAccessVariables {
  tenantId: string;
}

export function useCreateSourceConnection() {
  const queryClient = useQueryClient();

  return useMutation<SourceConnection, Error, CreateSourceConnectionVariables>({
    mutationFn: async variables => {
      const { data } = await client.post('/source-connections', variables);
      return SourceConnectionSchema.parse(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['source-connections'] });
    },
  });
}

export function useGrantConnectionAccess(connectionId: string) {
  const queryClient = useQueryClient();

  return useMutation<void, Error, GrantAccessVariables>({
    mutationFn: async ({ tenantId }) => {
      await client.post(`/source-connections/${connectionId}/access`, { tenantId });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['source-connections'] });
    },
  });
}

export function useRevokeConnectionAccess(connectionId: string) {
  const queryClient = useQueryClient();

  return useMutation<void, Error, { tenantId: string }>({
    mutationFn: async ({ tenantId }) => {
      await client.delete(`/source-connections/${connectionId}/access/${tenantId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['source-connections'] });
    },
  });
}

import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { WorkflowTypeSubmissionSchema, type WorkflowTypeSubmission } from '../types/WorkflowTypeSubmission';
import { useAuthStore } from '../store/authStore';
import client from './client';

export function usePendingSubmissions() {
  const tenantId = useAuthStore(s => s.tenantId);

  return useQuery<WorkflowTypeSubmission[]>({
    queryKey: ['workflow-type-submissions', tenantId, 'PENDING_APPROVAL'],
    queryFn: async () => {
      const { data } = await client.get('/workflow-type-submissions/pending');
      return z.array(WorkflowTypeSubmissionSchema).parse(data);
    },
    enabled: !!tenantId,
  });
}

export function useMyDraftSubmissions() {
  const tenantId = useAuthStore(s => s.tenantId);
  const userId = useAuthStore(s => s.userId);

  return useQuery<WorkflowTypeSubmission[]>({
    queryKey: ['workflow-type-submissions', tenantId, 'DRAFT', userId],
    queryFn: async () => {
      const { data } = await client.get('/workflow-type-submissions/my-drafts');
      return z.array(WorkflowTypeSubmissionSchema).parse(data);
    },
    enabled: !!tenantId && !!userId,
  });
}

export function useMyRejectedSubmissions() {
  const tenantId = useAuthStore(s => s.tenantId);
  const userId = useAuthStore(s => s.userId);

  return useQuery<WorkflowTypeSubmission[]>({
    queryKey: ['workflow-type-submissions', tenantId, 'REJECTED', userId],
    queryFn: async () => {
      const { data } = await client.get('/workflow-type-submissions/my-rejected');
      return z.array(WorkflowTypeSubmissionSchema).parse(data);
    },
    enabled: !!tenantId && !!userId,
  });
}

export function useAllDraftSubmissions() {
  const tenantId = useAuthStore(s => s.tenantId);

  return useQuery<WorkflowTypeSubmission[]>({
    queryKey: ['workflow-type-submissions', tenantId, 'DRAFT'],
    queryFn: async () => {
      const { data } = await client.get('/workflow-type-submissions/all-drafts');
      return z.array(WorkflowTypeSubmissionSchema).parse(data);
    },
    enabled: !!tenantId,
  });
}

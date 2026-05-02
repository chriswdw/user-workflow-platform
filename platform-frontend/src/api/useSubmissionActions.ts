import { useMutation, useQueryClient } from '@tanstack/react-query';
import { WorkflowTypeSubmissionSchema, type WorkflowTypeSubmission } from '../types/WorkflowTypeSubmission';
import client from './client';

interface SaveDraftVariables {
  draftConfigs: Record<string, unknown>;
  currentStep: number;
}

interface RejectVariables {
  rejectionReason: string;
}

function invalidateSubmission(queryClient: ReturnType<typeof useQueryClient>, submissionId: string) {
  queryClient.invalidateQueries({ queryKey: ['workflow-type-submissions'] });
  queryClient.invalidateQueries({ queryKey: ['workflow-type-submission', submissionId] });
}

export function useSaveDraft(submissionId: string) {
  const queryClient = useQueryClient();

  return useMutation<WorkflowTypeSubmission, Error, SaveDraftVariables>({
    mutationFn: async variables => {
      const { data } = await client.patch(`/workflow-type-submissions/${submissionId}/draft`, variables);
      return WorkflowTypeSubmissionSchema.parse(data);
    },
    onSuccess: () => invalidateSubmission(queryClient, submissionId),
  });
}

export function useSubmitForApproval(submissionId: string) {
  const queryClient = useQueryClient();

  return useMutation<WorkflowTypeSubmission, Error, void>({
    mutationFn: async () => {
      const { data } = await client.post(`/workflow-type-submissions/${submissionId}/submit`);
      return WorkflowTypeSubmissionSchema.parse(data);
    },
    onSuccess: () => invalidateSubmission(queryClient, submissionId),
  });
}

export function useApproveSubmission(submissionId: string) {
  const queryClient = useQueryClient();

  return useMutation<WorkflowTypeSubmission, Error, void>({
    mutationFn: async () => {
      const { data } = await client.post(`/workflow-type-submissions/${submissionId}/approve`);
      return WorkflowTypeSubmissionSchema.parse(data);
    },
    onSuccess: () => invalidateSubmission(queryClient, submissionId),
  });
}

export function useRejectSubmission(submissionId: string) {
  const queryClient = useQueryClient();

  return useMutation<WorkflowTypeSubmission, Error, RejectVariables>({
    mutationFn: async ({ rejectionReason }) => {
      const { data } = await client.post(`/workflow-type-submissions/${submissionId}/reject`, { rejectionReason });
      return WorkflowTypeSubmissionSchema.parse(data);
    },
    onSuccess: () => invalidateSubmission(queryClient, submissionId),
  });
}

export function useReviseSubmission(submissionId: string) {
  const queryClient = useQueryClient();

  return useMutation<WorkflowTypeSubmission, Error, void>({
    mutationFn: async () => {
      const { data } = await client.post(`/workflow-type-submissions/${submissionId}/revise`);
      return WorkflowTypeSubmissionSchema.parse(data);
    },
    onSuccess: () => invalidateSubmission(queryClient, submissionId),
  });
}

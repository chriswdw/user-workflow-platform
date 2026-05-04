import { z } from 'zod';

export const WorkflowTypeSubmissionSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  workflowType: z.string(),
  displayName: z.string(),
  description: z.string().nullable(),
  statusCode: z.string(),
  statusDisplayName: z.string(),
  submittedBy: z.string(),
  submittedAt: z.string().nullable(),
  reviewedBy: z.string().nullable(),
  reviewedAt: z.string().nullable(),
  rejectionReason: z.string().nullable(),
  draftConfigs: z.record(z.unknown()),
  currentStep: z.number(),
  version: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export type WorkflowTypeSubmission = z.infer<typeof WorkflowTypeSubmissionSchema>;

export const SourceConnectionSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  displayName: z.string(),
  type: z.enum(['KAFKA', 'DB_POLL', 'FILE_SHARE', 'MANUAL_UPLOAD']),
  config: z.record(z.unknown()),
  createdAt: z.string(),
});

export type SourceConnection = z.infer<typeof SourceConnectionSchema>;

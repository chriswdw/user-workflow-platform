import { z } from 'zod';

export const WorkItemSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  workflowType: z.string(),
  correlationId: z.string(),
  configVersionId: z.string().nullable(),
  source: z.enum(['KAFKA', 'DB_POLL', 'FILE_UPLOAD']),
  sourceRef: z.string(),
  idempotencyKey: z.string(),
  status: z.string(),
  assignedGroup: z.string(),
  routedByDefault: z.boolean(),
  fields: z.record(z.unknown()),
  priorityScore: z.number().nullable(),
  priorityLevel: z.string().nullable(),
  priorityLastCalculatedAt: z.string().nullable(),
  pendingCheckerId: z.string().nullable(),
  pendingCheckerTransition: z.string().nullable(),
  version: z.number(),
  makerUserId: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export type WorkItem = z.infer<typeof WorkItemSchema>;

import { z } from 'zod';

export const AuditEventTypeSchema = z.enum([
  'INGESTION', 'STATE_TRANSITION', 'FIELD_UPDATE', 'ASSIGNMENT',
  'ROUTING_FALLBACK', 'MAKER_CHECKER_APPROVAL', 'GROUP_REASSIGNMENT',
  'CHILD_WORKFLOW_CREATED', 'ATTACHED_TO_EXISTING_CHILD', 'DEPENDENCY_RESOLVED',
  'SLA_WARNING', 'SLA_BREACH', 'DUPLICATE_INGESTION_DISCARDED', 'TRANSITION_FAILED',
]);

export const ChangedFieldSchema = z.object({
  fieldPath: z.string(),
  previousValue: z.unknown(),
  newValue: z.unknown(),
});

export const AuditEntrySchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  workItemId: z.string(),
  correlationId: z.string(),
  eventType: AuditEventTypeSchema,
  previousState: z.string().nullable(),
  newState: z.string().nullable(),
  transitionName: z.string().nullable(),
  changedFields: z.array(ChangedFieldSchema),
  actorUserId: z.string(),
  actorRole: z.string(),
  timestamp: z.string(),
  idempotencyKey: z.string().nullable(),
});

export type AuditEntry = z.infer<typeof AuditEntrySchema>;

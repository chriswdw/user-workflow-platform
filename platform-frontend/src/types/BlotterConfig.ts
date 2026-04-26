import { z } from 'zod';

const FormatterSchema = z.enum(['CURRENCY', 'DATE', 'DATETIME', 'PERCENTAGE', 'TEXT', 'BADGE']);

export const BlotterColumnSchema = z.object({
  field: z.string(),
  header: z.string(),
  visible: z.boolean(),
  width: z.number().optional(),
  sortable: z.boolean().optional(),
  filterable: z.boolean().optional(),
  maskingRoles: z.array(z.string()).optional(),
  formatter: FormatterSchema.optional(),
});

export const BlotterConfigSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  workflowType: z.string(),
  columns: z.array(BlotterColumnSchema).min(1),
  defaultSort: z.object({
    field: z.string(),
    direction: z.enum(['ASC', 'DESC']),
  }),
  quickFilters: z.array(z.object({
    label: z.string(),
    filters: z.array(z.object({
      field: z.string(),
      operator: z.enum(['EQ', 'NEQ', 'IN', 'NOT_IN', 'GT', 'GTE', 'LT', 'LTE', 'CONTAINS']),
      value: z.unknown(),
    })),
  })).optional(),
  active: z.boolean(),
  version: z.number(),
});

export type BlotterColumn = z.infer<typeof BlotterColumnSchema>;
export type BlotterConfig = z.infer<typeof BlotterConfigSchema>;

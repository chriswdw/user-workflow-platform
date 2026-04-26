import { z } from 'zod';

const FormatterSchema = z.enum(['CURRENCY', 'DATE', 'DATETIME', 'PERCENTAGE', 'TEXT', 'BADGE']);

export const FieldDefSchema = z.object({
  field: z.string(),
  label: z.string(),
  formatter: FormatterSchema,
  editable: z.boolean().optional(),
  editableInStates: z.array(z.string()).optional(),
  required: z.boolean().optional(),
  visibleRoles: z.array(z.string()).optional(),
});

export const SectionSchema = z.object({
  title: z.string(),
  layout: z.enum(['TWO_COLUMN', 'ONE_COLUMN', 'TABLE']),
  collapsible: z.boolean().optional(),
  fields: z.array(FieldDefSchema),
});

export const ActionInputFieldSchema = z.object({
  field: z.string(),
  label: z.string(),
  inputType: z.enum(['TEXT', 'TEXTAREA', 'DATE', 'SELECT', 'CURRENCY']),
  required: z.boolean().optional(),
  options: z.array(z.string()).optional(),
});

export const ActionSchema = z.object({
  transition: z.string(),
  label: z.string(),
  style: z.enum(['PRIMARY', 'SECONDARY', 'DANGER']),
  visibleInStates: z.array(z.string()),
  visibleRoles: z.array(z.string()).optional(),
  confirmationRequired: z.boolean().optional(),
  confirmationMessage: z.string().optional(),
  inputFields: z.array(ActionInputFieldSchema).optional(),
});

export const DetailViewConfigSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  workflowType: z.string(),
  sections: z.array(SectionSchema).min(1),
  actions: z.array(ActionSchema),
  active: z.boolean(),
  version: z.number(),
});

export type FieldDef = z.infer<typeof FieldDefSchema>;
export type Section = z.infer<typeof SectionSchema>;
export type ActionInputField = z.infer<typeof ActionInputFieldSchema>;
export type Action = z.infer<typeof ActionSchema>;
export type DetailViewConfig = z.infer<typeof DetailViewConfigSchema>;

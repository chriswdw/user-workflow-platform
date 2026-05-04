import { create } from 'zustand';
import type { WorkflowTypeSubmission } from '../types/WorkflowTypeSubmission';

export type SourceType = 'KAFKA' | 'DB_POLL' | 'FILE_SHARE' | 'MANUAL_UPLOAD';

export interface FieldMappingRow {
  fieldPath: string;
  displayName: string;
  type: 'DATE' | 'DECIMAL' | 'BOOLEAN' | 'STRING';
  required: boolean;
}

export interface BlotterColumnDraft {
  fieldPath: string;
  headerName: string;
  formatter?: string;
  width?: number;
  sortable?: boolean;
}

export interface DetailSectionDraft {
  sectionName: string;
  layout?: 'TWO_COLUMN' | 'ONE_COLUMN' | 'TABLE';
  collapsible?: boolean;
  fields: Array<{ fieldPath: string; label: string; formatter?: string }>;
}

interface WorkflowConfig {
  initialState: string;
  states: Array<{ name: string; terminal: boolean }>;
  transitions: Array<{ name: string; fromState: string; toState: string; trigger: string }>;
}

export interface CreateSubmissionRequest {
  workflowType: string;
  displayName: string;
  description: string;
  sourceType: SourceType | null;
  sourceConnectionId: string | null;
  sourceConfig: Record<string, unknown>;
  fieldMappings: FieldMappingRow[];
  idempotencyKeyField: string | null;
  blotterColumns: BlotterColumnDraft[];
  detailSections: DetailSectionDraft[];
  workflowConfig: WorkflowConfig;
}

const AUTO_WORKFLOW_CONFIG: WorkflowConfig = {
  initialState: 'UNDER_REVIEW',
  states: [
    { name: 'UNDER_REVIEW', terminal: false },
    { name: 'CLOSED', terminal: true },
  ],
  transitions: [
    { name: 'close', fromState: 'UNDER_REVIEW', toState: 'CLOSED', trigger: 'MANUAL' },
  ],
};

interface WizardState {
  currentStep: number;
  workflowType: string;
  displayName: string;
  description: string;
  sourceType: SourceType | null;
  sourceConnectionId: string | null;
  sourceConfig: Record<string, unknown>;
  sampleFields: string[];
  fieldMappings: FieldMappingRow[];
  idempotencyKeyField: string | null;
  blotterColumns: BlotterColumnDraft[];
  detailSections: DetailSectionDraft[];
  submissionId: string | null;
  revisingSubmissionId: string | null;

  setStep: (step: number) => void;
  reset: () => void;
  setWorkflowType: (workflowType: string) => void;
  setDisplayName: (displayName: string) => void;
  setDescription: (description: string) => void;
  setSourceType: (sourceType: SourceType | null) => void;
  setSourceConnectionId: (sourceConnectionId: string | null) => void;
  setSourceConfig: (sourceConfig: Record<string, unknown>) => void;
  setSampleFields: (sampleFields: string[]) => void;
  setFieldMappings: (fieldMappings: FieldMappingRow[]) => void;
  setIdempotencyKeyField: (idempotencyKeyField: string | null) => void;
  setBlotterColumns: (blotterColumns: BlotterColumnDraft[]) => void;
  setDetailSections: (detailSections: DetailSectionDraft[]) => void;

  buildSubmissionPayload: () => CreateSubmissionRequest;
  buildDraftConfigsPayload: () => Record<string, unknown>;
  hydrateForResume: (submission: WorkflowTypeSubmission) => void;
  hydrateForRevision: (submission: WorkflowTypeSubmission) => void;
}

const INITIAL_STATE = {
  currentStep: 1,
  workflowType: '',
  displayName: '',
  description: '',
  sourceType: null as SourceType | null,
  sourceConnectionId: null as string | null,
  sourceConfig: {} as Record<string, unknown>,
  sampleFields: [] as string[],
  fieldMappings: [] as FieldMappingRow[],
  idempotencyKeyField: null as string | null,
  blotterColumns: [] as BlotterColumnDraft[],
  detailSections: [] as DetailSectionDraft[],
  submissionId: null as string | null,
  revisingSubmissionId: null as string | null,
};

function parseDraftConfigs(draftConfigs: Record<string, unknown>) {
  const wtd = (draftConfigs['workflowTypeDefinition'] as Record<string, unknown> | null) ?? {};
  const ftr = (draftConfigs['fieldTypeRegistry'] as Record<string, unknown> | null) ?? {};
  const isc = (draftConfigs['ingestionSourceConfig'] as Record<string, unknown> | null) ?? {};
  const bc = (draftConfigs['blotterConfig'] as Record<string, unknown> | null) ?? {};
  const dvc = (draftConfigs['detailViewConfig'] as Record<string, unknown> | null) ?? {};
  return {
    workflowType: (wtd['workflowType'] as string) ?? '',
    displayName: (wtd['displayName'] as string) ?? '',
    description: (wtd['description'] as string) ?? '',
    sourceType: (isc['sourceType'] as SourceType | null) ?? null,
    sourceConnectionId: (isc['sourceConnectionId'] as string | null) ?? null,
    sourceConfig: (isc['sourceConfig'] as Record<string, unknown>) ?? {},
    sampleFields: (ftr['sampleFields'] as string[]) ?? [],
    fieldMappings: (ftr['fieldMappings'] as FieldMappingRow[]) ?? [],
    idempotencyKeyField: (ftr['idempotencyKeyField'] as string | null) ?? null,
    blotterColumns: (bc['columns'] as BlotterColumnDraft[]) ?? [],
    detailSections: (dvc['sections'] as DetailSectionDraft[]) ?? [],
  };
}

interface DraftConfigsPayload {
  workflowType: string;
  displayName: string;
  description: string;
  sourceType: SourceType | null;
  sourceConnectionId: string | null;
  sourceConfig: Record<string, unknown>;
  sampleFields: string[];
  fieldMappings: FieldMappingRow[];
  idempotencyKeyField: string | null;
  blotterColumns: BlotterColumnDraft[];
  detailSections: DetailSectionDraft[];
}

function buildDraftConfigs(s: DraftConfigsPayload): Record<string, unknown> {
  return {
    workflowTypeDefinition: {
      workflowType: s.workflowType,
      displayName: s.displayName,
      description: s.description,
    },
    fieldTypeRegistry: {
      fieldMappings: s.fieldMappings,
      idempotencyKeyField: s.idempotencyKeyField,
      sampleFields: s.sampleFields,
    },
    ingestionSourceConfig: {
      sourceType: s.sourceType,
      sourceConnectionId: s.sourceConnectionId,
      sourceConfig: s.sourceConfig,
    },
    workflowConfig: AUTO_WORKFLOW_CONFIG,
    blotterConfig: { columns: s.blotterColumns },
    detailViewConfig: { sections: s.detailSections },
  };
}

export const useWizardStore = create<WizardState>((set, get) => ({
  ...INITIAL_STATE,

  setStep: step => set({ currentStep: step }),
  reset: () => set(INITIAL_STATE),
  setWorkflowType: workflowType => set({ workflowType }),
  setDisplayName: displayName => set({ displayName }),
  setDescription: description => set({ description }),
  setSourceType: sourceType => set({ sourceType }),
  setSourceConnectionId: sourceConnectionId => set({ sourceConnectionId }),
  setSourceConfig: sourceConfig => set({ sourceConfig }),
  setSampleFields: sampleFields => set({ sampleFields }),
  setFieldMappings: fieldMappings => set({ fieldMappings }),
  setIdempotencyKeyField: idempotencyKeyField => set({ idempotencyKeyField }),
  setBlotterColumns: blotterColumns => set({ blotterColumns }),
  setDetailSections: detailSections => set({ detailSections }),

  buildSubmissionPayload: () => {
    const s = get();
    return {
      workflowType: s.workflowType,
      displayName: s.displayName,
      description: s.description,
      sourceType: s.sourceType,
      sourceConnectionId: s.sourceConnectionId,
      sourceConfig: s.sourceConfig,
      fieldMappings: s.fieldMappings,
      idempotencyKeyField: s.idempotencyKeyField,
      blotterColumns: s.blotterColumns,
      detailSections: s.detailSections,
      workflowConfig: AUTO_WORKFLOW_CONFIG,
    };
  },

  buildDraftConfigsPayload: () => buildDraftConfigs(get()),

  hydrateForResume: submission =>
    set({
      ...parseDraftConfigs(submission.draftConfigs),
      submissionId: submission.id,
      revisingSubmissionId: null,
      currentStep: submission.currentStep + 1,
    }),

  hydrateForRevision: submission =>
    set({
      ...parseDraftConfigs(submission.draftConfigs),
      submissionId: submission.id,
      revisingSubmissionId: submission.id,
      currentStep: 1,
    }),
}));

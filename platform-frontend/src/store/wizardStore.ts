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
  return {
    workflowType: (draftConfigs['workflowType'] as string) ?? '',
    displayName: (draftConfigs['displayName'] as string) ?? '',
    description: (draftConfigs['description'] as string) ?? '',
    sourceType: (draftConfigs['sourceType'] as SourceType | null) ?? null,
    sourceConnectionId: (draftConfigs['sourceConnectionId'] as string | null) ?? null,
    sourceConfig: (draftConfigs['sourceConfig'] as Record<string, unknown>) ?? {},
    sampleFields: (draftConfigs['sampleFields'] as string[]) ?? [],
    fieldMappings: (draftConfigs['fieldMappings'] as FieldMappingRow[]) ?? [],
    idempotencyKeyField: (draftConfigs['idempotencyKeyField'] as string | null) ?? null,
    blotterColumns: (draftConfigs['blotterColumns'] as BlotterColumnDraft[]) ?? [],
    detailSections: (draftConfigs['detailSections'] as DetailSectionDraft[]) ?? [],
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

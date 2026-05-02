import type { SourceType, FieldMappingRow, BlotterColumnDraft, DetailSectionDraft } from '../store/wizardStore';

interface StepValidationState {
  workflowType: string;
  displayName: string;
  sourceType: SourceType | null;
  sourceConnectionId: string | null;
  fieldMappings: FieldMappingRow[];
  idempotencyKeyField: string | null;
  blotterColumns: BlotterColumnDraft[];
  detailSections: DetailSectionDraft[];
}

const WORKFLOW_TYPE_PATTERN = /^[A-Z][A-Z0-9_]*$/;

export function isStepComplete(step: number, state: StepValidationState): boolean {
  switch (step) {
    case 1:
      return WORKFLOW_TYPE_PATTERN.test(state.workflowType) && state.displayName.trim().length > 0;
    case 2:
      return (
        state.sourceType !== null &&
        (state.sourceType === 'MANUAL_UPLOAD' || state.sourceConnectionId !== null)
      );
    case 3:
      return true;
    case 4:
      return state.fieldMappings.length > 0 && state.idempotencyKeyField !== null;
    case 5:
      return state.blotterColumns.length > 0;
    case 6:
      return state.detailSections.length > 0;
    case 7:
      return true;
    default:
      return true;
  }
}

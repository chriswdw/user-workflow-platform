import { Before, Given, When, Then } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import {
  extractFieldPaths,
  extractCsvHeaders,
  isSupportedFileType,
} from '../src/utils/fieldExtractor';
import { useWizardStore } from '../src/store/wizardStore';
import type { WorkflowTypeSubmission } from '../src/types/WorkflowTypeSubmission';

let extractedPaths: string[] = [];
let fileTypeSupported: boolean = false;
let submission: WorkflowTypeSubmission;

Before({ tags: '@wizard' }, () => {
  useWizardStore.getState().reset();
});

Before(function () {
  useWizardStore.getState().reset();
  extractedPaths = [];
  fileTypeSupported = false;
});

Given('the JSON object {string}', (raw: string) => {
  const obj = JSON.parse(raw) as Record<string, unknown>;
  extractedPaths = extractFieldPaths(obj);
});

Given('the CSV string {string}', (csv: string) => {
  extractedPaths = extractCsvHeaders(csv);
});

Given('a file named {string}', (filename: string) => {
  fileTypeSupported = isSupportedFileType(filename);
});

When('I extract field paths from the object', () => {
  // paths already set in Given
});

When('I extract CSV headers', () => {
  // paths already set in Given
});

When('I check if the file type is supported', () => {
  // result already set in Given
});

Then('the extracted paths include {string}', (expected: string) => {
  assert.ok(extractedPaths.includes(expected), `Expected paths to include "${expected}" but got: ${extractedPaths.join(', ')}`);
});

Then('the file type is supported', () => {
  assert.ok(fileTypeSupported);
});

Then('the file type is not supported', () => {
  assert.ok(!fileTypeSupported);
});

// Store scenarios

Given('the wizard workflowType is {string} and displayName is {string}', (workflowType: string, displayName: string) => {
  useWizardStore.getState().setWorkflowType(workflowType);
  useWizardStore.getState().setDisplayName(displayName);
});

When('I build the submission payload', () => {
  // result accessed in Then via getState
});

Then('the payload workflowConfig initialState is {string}', (expected: string) => {
  const payload = useWizardStore.getState().buildSubmissionPayload();
  assert.equal(payload.workflowConfig.initialState, expected);
});

Then('the payload workflowConfig has {int} states', (count: number) => {
  const payload = useWizardStore.getState().buildSubmissionPayload();
  assert.equal(payload.workflowConfig.states.length, count);
});

Then('the payload workflowConfig has {int} transition named {string}', (count: number, name: string) => {
  const payload = useWizardStore.getState().buildSubmissionPayload();
  assert.equal(payload.workflowConfig.transitions.length, count);
  assert.equal(payload.workflowConfig.transitions[0]?.name, name);
});

function makeSubmission(id: string, currentStep: number, draftConfigs: Record<string, unknown> = {}): WorkflowTypeSubmission {
  return {
    id,
    tenantId: 'tenant-1',
    workflowType: 'TEST',
    displayName: 'Test',
    description: null,
    statusCode: 'DRAFT',
    statusDisplayName: 'Draft',
    submittedBy: 'user-1',
    submittedAt: '2026-01-01T00:00:00Z',
    reviewedBy: null,
    reviewedAt: null,
    rejectionReason: null,
    draftConfigs,
    currentStep,
    version: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

Given('a submission with id {string} and currentStep {int} and no draftConfigs', (id: string, currentStep: number) => {
  submission = makeSubmission(id, currentStep);
});

Given('a submission with draftConfigs containing sampleFields {string}', (fieldsCsv: string) => {
  const sampleFields = fieldsCsv.split(',').map(f => f.trim());
  submission = makeSubmission('sub-hydrate', 2, { sampleFields });
});

When('I hydrate the store for resume', () => {
  useWizardStore.getState().hydrateForResume(submission);
});

When('I hydrate the store for revision', () => {
  useWizardStore.getState().hydrateForRevision(submission);
});

Then('the store currentStep is {int}', (expected: number) => {
  assert.equal(useWizardStore.getState().currentStep, expected);
});

Then('the store submissionId is {string}', (expected: string) => {
  assert.equal(useWizardStore.getState().submissionId, expected);
});

Then('the store revisingSubmissionId is null', () => {
  assert.equal(useWizardStore.getState().revisingSubmissionId, null);
});

Then('the store revisingSubmissionId is {string}', (expected: string) => {
  assert.equal(useWizardStore.getState().revisingSubmissionId, expected);
});

Then('the store sampleFields is non-empty', () => {
  assert.ok(useWizardStore.getState().sampleFields.length > 0);
});

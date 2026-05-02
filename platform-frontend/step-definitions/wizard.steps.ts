import { Before, Given, Then } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { useWizardStore } from '../src/store/wizardStore';
import { isStepComplete } from '../src/utils/wizardValidation';

// Given('the wizard workflowType is ... and displayName is ...') — defined in wizard-foundation.steps.ts
// Given('a submission with id ... and currentStep ...') — defined in wizard-foundation.steps.ts
// When('I hydrate the store for resume/revision') — defined in wizard-foundation.steps.ts
// When('I build the submission payload') — defined in wizard-foundation.steps.ts
// Then('the store currentStep is ...') — defined in wizard-foundation.steps.ts
// Then('the store submissionId is ...') — defined in wizard-foundation.steps.ts
// Then('the store revisingSubmissionId is ...') — defined in wizard-foundation.steps.ts

Before(function () {
  useWizardStore.getState().reset();
});

Given('the wizard store is reset', () => {
  useWizardStore.getState().reset();
});

// ── Step 2 ───────────────────────────────────────────────────────────────────

Given('the wizard sourceType is not set', () => {
  useWizardStore.getState().setSourceType(null);
  useWizardStore.getState().setSourceConnectionId(null);
});

Given('the wizard sourceType is {string} with no connection', (sourceType: string) => {
  useWizardStore.getState().setSourceType(sourceType as 'KAFKA' | 'DB_POLL' | 'FILE_SHARE' | 'MANUAL_UPLOAD');
  useWizardStore.getState().setSourceConnectionId(null);
});

Given('the wizard sourceType is {string} with connectionId {string}', (sourceType: string, connectionId: string) => {
  useWizardStore.getState().setSourceType(sourceType as 'KAFKA' | 'DB_POLL' | 'FILE_SHARE' | 'MANUAL_UPLOAD');
  useWizardStore.getState().setSourceConnectionId(connectionId);
});

// ── Step 4 ───────────────────────────────────────────────────────────────────

Given('the wizard has no field mappings', () => {
  useWizardStore.getState().setFieldMappings([]);
  useWizardStore.getState().setIdempotencyKeyField(null);
});

Given('the wizard has {int} field mapping with no idempotencyKey', (count: number) => {
  const mappings = Array.from({ length: count }, (_, i) => ({
    fieldPath: `field${i}`,
    displayName: `Field ${i}`,
    type: 'STRING' as const,
    required: false,
  }));
  useWizardStore.getState().setFieldMappings(mappings);
  useWizardStore.getState().setIdempotencyKeyField(null);
});

Given('the wizard has {int} field mapping with idempotencyKey {string}', (count: number, key: string) => {
  const mappings = Array.from({ length: count }, (_, i) => ({
    fieldPath: `field${i}`,
    displayName: `Field ${i}`,
    type: 'STRING' as const,
    required: false,
  }));
  useWizardStore.getState().setFieldMappings(mappings);
  useWizardStore.getState().setIdempotencyKeyField(key);
});

// ── Step 5 ───────────────────────────────────────────────────────────────────

Given('the wizard has no blotter columns', () => {
  useWizardStore.getState().setBlotterColumns([]);
});

Given('the wizard has {int} blotter column', (count: number) => {
  const columns = Array.from({ length: count }, (_, i) => ({
    fieldPath: `field${i}`,
    headerName: `Column ${i}`,
  }));
  useWizardStore.getState().setBlotterColumns(columns);
});

// ── Step 6 ───────────────────────────────────────────────────────────────────

Given('the wizard has no detail sections', () => {
  useWizardStore.getState().setDetailSections([]);
});

Given('the wizard has {int} detail section', (count: number) => {
  const sections = Array.from({ length: count }, (_, i) => ({
    sectionName: `Section ${i}`,
    fields: [],
  }));
  useWizardStore.getState().setDetailSections(sections);
});

// ── Step assertions ───────────────────────────────────────────────────────────

Then('step {int} is complete', (step: number) => {
  const state = useWizardStore.getState();
  assert.ok(isStepComplete(step, state), `Expected step ${step} to be complete`);
});

Then('step {int} is not complete', (step: number) => {
  const state = useWizardStore.getState();
  assert.ok(!isStepComplete(step, state), `Expected step ${step} to be incomplete`);
});

// ── Payload assertions ────────────────────────────────────────────────────────

Then('the payload blotterColumns has {int} entry', (count: number) => {
  const payload = useWizardStore.getState().buildSubmissionPayload();
  assert.equal(payload.blotterColumns.length, count);
});

Then('the payload detailSections has {int} entry', (count: number) => {
  const payload = useWizardStore.getState().buildSubmissionPayload();
  assert.equal(payload.detailSections.length, count);
});

import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { QueryClient } from '@tanstack/react-query';
import { MySubmissionsView } from '../src/components/wizard/MySubmissionsView';
import { useAuthStore } from '../src/store/authStore';
import { useWizardStore } from '../src/store/wizardStore';
import type { WorkflowTypeSubmission } from '../src/types/WorkflowTypeSubmission';
import { createTestQueryClient, renderWithProviders } from './support/render';
import { stubClientMethod } from './support/mockClient';

const TENANT_ID = 'tenant-1';
const USER_ID = 'user-1';

let queryClient: QueryClient;
let drafts: WorkflowTypeSubmission[];
let rejected: WorkflowTypeSubmission[];
let rendered: RenderResult;
let opened: boolean;
let confirmReturnValue: boolean;
let deleteCallCount: number;

function makeSubmission(overrides: Partial<WorkflowTypeSubmission> = {}): WorkflowTypeSubmission {
  return {
    id: 'sub-1',
    tenantId: TENANT_ID,
    workflowType: 'SETTLEMENT_EXCEPTION',
    displayName: 'Settlement Exception',
    description: null,
    statusCode: 'DRAFT',
    statusDisplayName: 'Draft',
    submittedBy: USER_ID,
    submittedAt: '2026-01-01T00:00:00Z',
    reviewedBy: null,
    reviewedAt: null,
    rejectionReason: null,
    draftConfigs: { workflowTypeDefinition: {}, fieldTypeRegistry: {}, ingestionSourceConfig: {}, blotterConfig: {}, detailViewConfig: {} },
    currentStep: 3,
    version: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

Before({ tags: '@my-submissions' }, () => {
  useAuthStore.getState().setAuth('tok', USER_ID, 'ANALYST', TENANT_ID);
  useWizardStore.getState().reset();
  queryClient = createTestQueryClient();
  drafts = [];
  rejected = [];
  opened = false;
  confirmReturnValue = true;
  deleteCallCount = 0;

  // jsdom's window.confirm is a no-op stub that logs "not implemented" — replace it with a
  // controllable one so the discard-confirmation gating can actually be exercised.
  globalThis.confirm = () => confirmReturnValue;

  stubClientMethod('delete', async () => {
    deleteCallCount += 1;
    return { data: undefined };
  });
  // A successful discard invalidates the 'workflow-type-submissions' query family, which
  // triggers a real refetch of whatever list queries are currently mounted — stub `get` too so
  // that refetch doesn't hit the real network. The seeded queryClient data (set in the "When I
  // render" step) is what the assertions in this scenario actually check; this refetch response
  // just needs to not crash.
  stubClientMethod('get', async () => ({ data: [] }));
});

Given('a draft submission {string} for {string} at step {int}', (workflowType: string, displayName: string, step: number) => {
  drafts.push(makeSubmission({ id: `draft-${drafts.length + 1}`, workflowType, displayName, currentStep: step }));
});

Given('a rejected submission {string} for {string} with reason {string}', (workflowType: string, displayName: string, reason: string) => {
  rejected.push(makeSubmission({
    id: `rejected-${rejected.length + 1}`, workflowType, displayName,
    statusCode: 'REJECTED', statusDisplayName: 'Rejected', rejectionReason: reason,
  }));
});

Given('the user will decline the discard confirmation', () => {
  confirmReturnValue = false;
});

When('I render My Submissions', () => {
  queryClient.setQueryData(['workflow-type-submissions', TENANT_ID, 'DRAFT', USER_ID], drafts);
  queryClient.setQueryData(['workflow-type-submissions', TENANT_ID, 'REJECTED', USER_ID], rejected);

  rendered = renderWithProviders(
    <MySubmissionsView onOpenWizard={() => { opened = true; }} />,
    queryClient,
  );
});

When('I click Resume on the draft row', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Resume' });
  await userEvent.click(button);
});

When('I click Revise on the rejected row', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Revise' });
  await userEvent.click(button);
});

When('I click Discard', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Discard' });
  await userEvent.click(button);
});

Then('My Submissions shows no drafts message', () => {
  assert.ok(within(rendered.container).getByText('No drafts in progress.'));
});

Then('My Submissions lists the draft {string}', (displayName: string) => {
  assert.ok(within(rendered.container).getByText(displayName));
});

Then('My Submissions lists the rejected submission {string} with reason {string}', (displayName: string, reason: string) => {
  assert.ok(within(rendered.container).getByText(displayName));
  assert.ok(within(rendered.container).getByText(reason));
});

Then('the wizard store is hydrated for resuming that submission', () => {
  assert.equal(useWizardStore.getState().submissionId, drafts[0].id);
  assert.ok(opened, 'expected onOpenWizard to have been called');
});

Then('the wizard store is hydrated for revising that submission', () => {
  assert.equal(useWizardStore.getState().revisingSubmissionId, rejected[0].id);
  assert.ok(opened, 'expected onOpenWizard to have been called');
});

Then('the discard request is sent', () => {
  assert.equal(deleteCallCount, 1);
});

Then('no discard request is sent', () => {
  assert.equal(deleteCallCount, 0);
});

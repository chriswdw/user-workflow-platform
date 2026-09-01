import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { QueryClient } from '@tanstack/react-query';
import { AllDraftsAdminView } from '../src/components/admin/AllDraftsAdminView';
import { useAuthStore } from '../src/store/authStore';
import { useWizardStore } from '../src/store/wizardStore';
import type { WorkflowTypeSubmission } from '../src/types/WorkflowTypeSubmission';
import { createTestQueryClient, renderWithProviders } from './support/render';
import { stubClientMethod } from './support/mockClient';

const TENANT_ID = 'tenant-1';

let queryClient: QueryClient;
let allDrafts: WorkflowTypeSubmission[];
let rendered: RenderResult;
let opened: boolean;
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
    submittedBy: 'maker-1',
    submittedAt: null,
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

Before({ tags: '@all-drafts-admin' }, () => {
  useAuthStore.getState().setAuth('tok', 'admin-1', 'PLATFORM_ADMIN', TENANT_ID);
  useWizardStore.getState().reset();
  queryClient = createTestQueryClient();
  allDrafts = [];
  opened = false;
  deleteCallCount = 0;

  stubClientMethod('delete', async () => {
    deleteCallCount += 1;
    return { data: undefined };
  });
  stubClientMethod('get', async () => ({ data: [] }));
});

Given('an admin-visible draft {string} by {string} at step {int}', (workflowType: string, submittedBy: string, step: number) => {
  allDrafts.push(makeSubmission({ id: `admin-draft-${allDrafts.length + 1}`, workflowType, submittedBy, currentStep: step }));
});

Given('an admin-visible rejected submission {string} by {string}', (workflowType: string, submittedBy: string) => {
  allDrafts.push(makeSubmission({
    id: `admin-rejected-${allDrafts.length + 1}`, workflowType, submittedBy,
    statusCode: 'REJECTED', statusDisplayName: 'Rejected',
  }));
});

When('I render the all drafts admin view', () => {
  queryClient.setQueryData(['workflow-type-submissions', TENANT_ID, 'DRAFT'], allDrafts);
  rendered = renderWithProviders(
    <AllDraftsAdminView onOpenWizard={() => { opened = true; }} />,
    queryClient,
  );
});

When('I click the draft row', async () => {
  const row = within(rendered.container).getAllByRole('row')[1]; // 0 is header
  await userEvent.click(row);
});

When('I click Continue in Wizard', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Continue in Wizard' });
  await userEvent.click(button);
});

When('I click Discard in the admin detail panel', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Discard' });
  await userEvent.click(button);
});

When('I confirm the discard in the modal', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Confirm' });
  await userEvent.click(button);
});

Then('the all drafts admin view lists {int} draft', (count: number) => {
  const heading = within(rendered.container).getByText(new RegExp(`All Draft Submissions \\(${count}\\)`));
  assert.ok(heading);
});

Then('no submission detail is shown', () => {
  assert.equal(within(rendered.container).queryByText('Continue in Wizard'), null);
});

Then('the submission detail panel is shown for {string}', (displayName: string) => {
  const detailPanel = rendered.container.querySelector('.submission-detail');
  assert.ok(detailPanel, 'expected the submission detail panel to be rendered');
  assert.equal(detailPanel?.querySelector('.submission-detail-title')?.textContent, displayName);
});

Then('the detail panel shows a Continue in Wizard button but no Revise button', () => {
  assert.ok(within(rendered.container).getByRole('button', { name: 'Continue in Wizard' }));
  assert.equal(within(rendered.container).queryByRole('button', { name: 'Revise' }), null);
});

Then('the detail panel shows a Revise button but no Continue in Wizard button', () => {
  assert.ok(within(rendered.container).getByRole('button', { name: 'Revise' }));
  assert.equal(within(rendered.container).queryByRole('button', { name: 'Continue in Wizard' }), null);
});

Then('the admin wizard store is hydrated for resuming and onOpenWizard fires', () => {
  assert.equal(useWizardStore.getState().submissionId, allDrafts[0].id);
  assert.ok(opened);
});

Then('the admin discard request is sent', () => {
  assert.equal(deleteCallCount, 1);
});

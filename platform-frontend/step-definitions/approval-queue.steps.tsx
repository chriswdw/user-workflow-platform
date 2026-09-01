import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { QueryClient } from '@tanstack/react-query';
import { ApprovalQueue } from '../src/components/wizard/ApprovalQueue';
import { useAuthStore } from '../src/store/authStore';
import type { WorkflowTypeSubmission } from '../src/types/WorkflowTypeSubmission';
import { createTestQueryClient, renderWithProviders } from './support/render';
import { stubClientMethod } from './support/mockClient';

const TENANT_ID = 'tenant-1';

let queryClient: QueryClient;
let pending: WorkflowTypeSubmission[];
let rendered: RenderResult;
let approveCallCount: number;
let rejectCalls: Array<{ rejectionReason: string }>;

function makeSubmission(overrides: Partial<WorkflowTypeSubmission> = {}): WorkflowTypeSubmission {
  return {
    id: 'sub-1',
    tenantId: TENANT_ID,
    workflowType: 'SETTLEMENT_EXCEPTION',
    displayName: 'Settlement Exception',
    description: null,
    statusCode: 'PENDING_APPROVAL',
    statusDisplayName: 'Pending Approval',
    submittedBy: 'maker-1',
    submittedAt: '2026-01-01T00:00:00Z',
    reviewedBy: null,
    reviewedAt: null,
    rejectionReason: null,
    draftConfigs: { workflowTypeDefinition: {}, fieldTypeRegistry: {}, ingestionSourceConfig: {}, blotterConfig: {}, detailViewConfig: {} },
    currentStep: 7,
    version: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

Before({ tags: '@approval-queue' }, () => {
  useAuthStore.getState().setAuth('tok', 'checker-1', 'SUPERVISOR', TENANT_ID);
  queryClient = createTestQueryClient();
  pending = [];
  approveCallCount = 0;
  rejectCalls = [];

  stubClientMethod('post', async (...args: unknown[]) => {
    const url = args[0] as string;
    if (url.endsWith('/approve')) {
      approveCallCount += 1;
    } else if (url.endsWith('/reject')) {
      rejectCalls.push((args[1] as { rejectionReason: string }));
    }
    return { data: makeSubmission() };
  });
  stubClientMethod('get', async () => ({ data: [] }));
});

Given('a pending submission {string} for {string} submitted by {string}', (workflowType: string, displayName: string, submittedBy: string) => {
  pending.push(makeSubmission({ id: `pending-${pending.length + 1}`, workflowType, displayName, submittedBy }));
});

When('I render the approval queue', () => {
  queryClient.setQueryData(['workflow-type-submissions', TENANT_ID, 'PENDING_APPROVAL'], pending);
  rendered = renderWithProviders(<ApprovalQueue />, queryClient);
});

When('I click Approve', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Approve' });
  await userEvent.click(button);
});

When('I click Reject', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Reject' });
  await userEvent.click(button);
});

When('I click Cancel on the reject form', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Cancel' });
  await userEvent.click(button);
});

When('I type {string} as the rejection reason', async (reason: string) => {
  const textarea = within(rendered.container).getByPlaceholderText('Rejection reason');
  await userEvent.type(textarea, reason);
});

When('I click Confirm Reject', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Confirm Reject' });
  await userEvent.click(button);
});

Then('the approval queue shows the empty state', () => {
  assert.ok(within(rendered.container).getByText('No submissions pending approval.'));
});

Then('the approval queue lists {int} pending submission', (count: number) => {
  const heading = within(rendered.container).getByText(new RegExp(`Pending Approvals \\(${count}\\)`));
  assert.ok(heading);
});

Then('the approve request is sent', () => {
  assert.equal(approveCallCount, 1);
});

Then('the reject form is shown', () => {
  assert.ok(within(rendered.container).getByPlaceholderText('Rejection reason'));
});

Then('the reject form is not shown', () => {
  assert.equal(within(rendered.container).queryByPlaceholderText('Rejection reason'), null);
});

Then('the Confirm Reject button is disabled', () => {
  const button = within(rendered.container).getByRole('button', { name: 'Confirm Reject' }) as HTMLButtonElement;
  assert.ok(button.disabled);
});

Then('the Confirm Reject button is enabled', () => {
  const button = within(rendered.container).getByRole('button', { name: 'Confirm Reject' }) as HTMLButtonElement;
  assert.ok(!button.disabled);
});

Then('the reject request is sent with reason {string}', (reason: string) => {
  assert.equal(rejectCalls.length, 1);
  assert.equal(rejectCalls[0].rejectionReason, reason);
});

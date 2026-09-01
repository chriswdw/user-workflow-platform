import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useWizardStore } from '../src/store/wizardStore';
import { getRenderedWizardShell, wasWizardShellClosed } from './support/wizardShellHarness';
import { stubClientMethod } from './support/mockClient';

let createCalls: Array<Record<string, unknown>>;
let draftSaveCalls: Array<{ url: string; body: Record<string, unknown> }>;
let submitCalls: string[];
let callOrder: string[];

/**
 * Builds a real axios-shaped rejection so WizardShell's `axios.isAxiosError(err)` check (used to
 * distinguish a 409 duplicate-submission conflict from any other failure) behaves the way it
 * would against the real API — axios's own `isAxiosError` just checks `payload.isAxiosError ===
 * true` on an object, so a plain Error with that flag (and a `response.status`) is sufficient.
 */
function axiosError(message: string, status?: number): Error & { isAxiosError: true; response?: { status: number } } {
  const err = new Error(message) as Error & { isAxiosError: true; response?: { status: number } };
  err.isAxiosError = true;
  if (status !== undefined) err.response = { status };
  return err;
}

Before({ tags: '@wizard-shell-submission-flow' }, () => {
  useWizardStore.getState().reset();
  createCalls = [];
  draftSaveCalls = [];
  submitCalls = [];
  callOrder = [];
});

Given('submission creation will succeed with id {string}', (id: string) => {
  stubClientMethod('post', async (...args: unknown[]) => {
    const [url, body] = args as [string, Record<string, unknown>];
    if (url === '/workflow-type-submissions') {
      createCalls.push(body);
      return { data: { ...makeSubmission(), id } };
    }
    throw new Error(`unexpected POST ${url}`);
  });
});

Given('submission creation will fail with a 409 conflict', () => {
  stubClientMethod('post', async () => {
    throw axiosError('Conflict', 409);
  });
});

Given('submission creation will fail with message {string}', (message: string) => {
  stubClientMethod('post', async (...args: unknown[]) => {
    const url = args[0] as string;
    if (url === '/workflow-type-submissions') throw new Error(message);
    throw new Error(`unexpected POST ${url}`);
  });
});

Given('the wizard is on step {int} with an existing submission {string}', (step: number, submissionId: string) => {
  useWizardStore.setState({ currentStep: step, submissionId, revisingSubmissionId: null });
});

Given('the wizard is on step {int} revising submission {string}', (step: number, submissionId: string) => {
  useWizardStore.setState({ currentStep: step, submissionId, revisingSubmissionId: submissionId });
});

Given('draft saving will succeed', () => {
  stubClientMethod('patch', async (...args: unknown[]) => {
    const [url, body] = args as [string, Record<string, unknown>];
    draftSaveCalls.push({ url, body });
    return { data: makeSubmission() };
  });
});

Given('draft saving will fail with message {string}', (message: string) => {
  stubClientMethod('patch', async () => {
    throw new Error(message);
  });
});

Given('submit for approval will succeed', () => {
  stubClientMethod('post', async (...args: unknown[]) => {
    const url = args[0] as string;
    if (url.endsWith('/submit')) {
      submitCalls.push(url);
      callOrder.push('submit');
      return { data: makeSubmission() };
    }
    throw new Error(`unexpected POST ${url}`);
  });
});

Given('submit for approval will fail with message {string}', (message: string) => {
  stubClientMethod('post', async (...args: unknown[]) => {
    const url = args[0] as string;
    if (url.endsWith('/submit')) throw new Error(message);
    throw new Error(`unexpected POST ${url}`);
  });
});

Given('revision and submit for approval will both succeed', () => {
  stubClientMethod('post', async (...args: unknown[]) => {
    const url = args[0] as string;
    if (url.endsWith('/revise')) {
      callOrder.push('revise');
      return { data: makeSubmission() };
    }
    if (url.endsWith('/submit')) {
      callOrder.push('submit');
      return { data: makeSubmission() };
    }
    throw new Error(`unexpected POST ${url}`);
  });
});

When('I click Next', async () => {
  const button = within(getRenderedWizardShell().container).getByRole('button', { name: 'Next' });
  await userEvent.click(button);
});

When('I click the {string} button', async (label: string) => {
  const button = within(getRenderedWizardShell().container).getByRole('button', { name: label });
  await userEvent.click(button);
});

// Every assertion below observes the outcome of a React Query mutation fired by the click step
// above. `mutate(...)`'s onSuccess/onError callbacks run after the mutation's promise settles —
// a microtask boundary past `await userEvent.click(...)` — so a synchronous assertion here would
// run before the component has re-rendered. `waitFor` polls until the condition holds (or times
// out), which is the standard RTL pattern for asserting on the result of an async state update.

Then('the submission is created with workflowType {string}', async (workflowType: string) => {
  await waitFor(() => assert.equal(createCalls.length, 1, 'expected exactly one submission-creation call'));
  assert.equal(createCalls[0]?.['workflowType'], workflowType);
});

Then('the wizard footer shows an error containing {string}', async (fragment: string) => {
  await waitFor(() => {
    const footer = getRenderedWizardShell().container.querySelector('.wizard-footer-error');
    assert.ok(footer, 'expected a footer error to be rendered');
    assert.ok(footer!.textContent?.includes(fragment), `expected footer error to contain "${fragment}", got "${footer!.textContent}"`);
  });
});

Then('the wizard store submissionId is {string}', async (id: string) => {
  await waitFor(() => assert.equal(useWizardStore.getState().submissionId, id));
});

Then('a draft was saved for step {int}', async (step: number) => {
  await waitFor(() => assert.equal(draftSaveCalls.length, 1, 'expected exactly one draft-save call'));
  assert.equal(draftSaveCalls[0]?.body['currentStep'], step);
});

Then('the submission was submitted for approval', async () => {
  await waitFor(() => assert.equal(submitCalls.length, 1, 'expected exactly one submit-for-approval call'));
});

Then('the wizard shell onClose callback does not fire', async () => {
  // There's no positive event to await here (the assertion is that nothing happened) — give any
  // pending mutation microtasks a turn to settle first so a callback firing late doesn't slip past.
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.ok(!wasWizardShellClosed(), 'expected onClose not to have been called');
});

Then('the submission was revised before being submitted for approval', async () => {
  await waitFor(() => assert.deepEqual(callOrder, ['revise', 'submit']));
});

function makeSubmission(): {
  id: string;
  tenantId: string;
  workflowType: string;
  displayName: string;
  description: string | null;
  statusCode: string;
  statusDisplayName: string;
  submittedBy: string;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  draftConfigs: Record<string, unknown>;
  currentStep: number;
  version: number;
  createdAt: string;
  updatedAt: string;
} {
  // submittedBy is a required (non-nullable) string per WorkflowTypeSubmissionSchema — every
  // response returned from a stubbed client method here is Zod-parsed by the real API hooks
  // (useCreateSubmission etc.) exactly as a real server response would be, so this must satisfy
  // the schema or the mutation rejects (hitting onError) instead of resolving (onSuccess).
  return {
    id: 'sub-1',
    tenantId: 'tenant-1',
    workflowType: 'TRADE_BREAK',
    displayName: 'Trade Break',
    description: null,
    statusCode: 'DRAFT',
    statusDisplayName: 'Draft',
    submittedBy: 'maker-1',
    submittedAt: null,
    reviewedBy: null,
    reviewedAt: null,
    rejectionReason: null,
    draftConfigs: { workflowTypeDefinition: {}, fieldTypeRegistry: {}, ingestionSourceConfig: {}, blotterConfig: {}, detailViewConfig: {} },
    currentStep: 1,
    version: 1,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

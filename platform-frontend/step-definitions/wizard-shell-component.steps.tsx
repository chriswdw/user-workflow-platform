import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useWizardStore } from '../src/store/wizardStore';
import { renderWizardShell, getRenderedWizardShell, wasWizardShellClosed } from './support/wizardShellHarness';

Before({ tags: '@wizard-shell' }, () => {
  useWizardStore.getState().reset();
});

Given('the wizard shell has workflowType {string} and displayName {string}', (workflowType: string, displayName: string) => {
  useWizardStore.getState().setWorkflowType(workflowType);
  useWizardStore.getState().setDisplayName(displayName);
});

Given('the wizard is on step {int}', (step: number) => {
  useWizardStore.setState({ currentStep: step });
});

When('I render the wizard shell', () => {
  renderWizardShell();
});

When('I press Escape', async () => {
  const user = userEvent.setup();
  await user.keyboard('{Escape}');
});

When('I click the {string} progress step', async (label: string) => {
  const button = within(getRenderedWizardShell().container).getByRole('button', { name: new RegExp(`Go to step \\d+: ${label}`) });
  await userEvent.click(button);
});

Then('the wizard shows step {string} as active', (label: string) => {
  const heading = within(getRenderedWizardShell().container).getByText(label);
  assert.ok(heading.className.includes('wizard-progress-label') || heading.closest('.wizard-progress-step--active'),
    `expected "${label}" to be the active step`);
  assert.ok(heading.closest('.wizard-progress-step--active'), `expected "${label}" step to carry the active class`);
});

Then('the Next button is disabled', () => {
  const button = within(getRenderedWizardShell().container).getByRole('button', { name: 'Next' });
  assert.ok((button as HTMLButtonElement).disabled, 'expected Next to be disabled');
});

Then('the Next button is enabled', () => {
  const button = within(getRenderedWizardShell().container).getByRole('button', { name: 'Next' });
  assert.ok(!(button as HTMLButtonElement).disabled, 'expected Next to be enabled');
});

Then('the wizard store currentStep is {int}', async (step: number) => {
  // Shared with async submission-flow scenarios (wizard-shell-submission-flow.steps.tsx), where
  // currentStep changes inside a React Query mutation's onSuccess callback — waitFor covers both
  // that and the synchronous progress-bar-click case (resolves on the very first check).
  await waitFor(() => assert.equal(useWizardStore.getState().currentStep, step));
});

Then('the wizard shell onClose callback fires', async () => {
  await waitFor(() => assert.ok(wasWizardShellClosed(), 'expected onClose to have been called'));
});

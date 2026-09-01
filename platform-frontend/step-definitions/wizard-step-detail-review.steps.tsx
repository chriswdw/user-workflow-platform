import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StepDetailViewConfig } from '../src/components/wizard/StepDetailViewConfig';
import { StepReview } from '../src/components/wizard/StepReview';
import { useWizardStore } from '../src/store/wizardStore';
import { renderWithProviders } from './support/render';

let rendered: RenderResult;
let goToStepArg: number | null;
let submitErrorToRender: string | null;

Before({ tags: '@wizard-detail-review' }, () => {
  useWizardStore.getState().reset();
  goToStepArg = null;
  submitErrorToRender = null;
});

Given('the wizard has field mappings for detail view {string}', (pathsCsv: string) => {
  const paths = pathsCsv.split(',').map(p => p.trim());
  useWizardStore.getState().setFieldMappings(
    paths.map(p => ({ fieldPath: p, displayName: p, type: 'STRING' as const, required: false })),
  );
});

Given('the review step has submitError {string}', (message: string) => {
  submitErrorToRender = message;
});

When('I render the detail view config step', () => {
  rendered = renderWithProviders(<StepDetailViewConfig />);
});

When('I click Add section', async () => {
  const button = within(rendered.container).getByRole('button', { name: '+ Add section' });
  await userEvent.click(button);
});

When('I check the detail section field checkbox for {string}', async (path: string) => {
  const checkbox = within(rendered.container).getByRole('checkbox', { name: new RegExp(path.replace('.', '\\.')) });
  await userEvent.click(checkbox);
});

When('I render the review step', () => {
  rendered = renderWithProviders(
    <StepReview submitError={submitErrorToRender} onGoToStep={step => { goToStepArg = step; }} />,
  );
});

When('I click "Go to that step"', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Go to that step' });
  await userEvent.click(button);
});

Then('the detail view config has {int} section', (count: number) => {
  const cards = rendered.container.querySelectorAll('.detail-section-card');
  assert.equal(cards.length, count);
});

Then('a label input appears for the {string} detail field', (path: string) => {
  const labelInputs = within(rendered.container).getAllByPlaceholderText('Label');
  assert.equal(labelInputs.length, 1, `expected a label input for "${path}"`);
});

Then('the review shows {int} field mappings in the summary', (count: number) => {
  const heading = within(rendered.container).getByText(new RegExp(`Field Mappings \\(${count}\\)`));
  assert.ok(heading);
});

Then('the review shows the error banner with {string}', (message: string) => {
  const banner = within(rendered.container).getByText(message, { exact: false });
  assert.ok(banner);
});

Then('the review shows a {string} link', (label: string) => {
  const link = within(rendered.container).queryByRole('button', { name: label });
  assert.ok(link, `expected a "${label}" button/link`);
});

Then('the review does not show a {string} link', (label: string) => {
  const link = within(rendered.container).queryByRole('button', { name: label });
  assert.equal(link, null, `expected no "${label}" button/link`);
});

Then('onGoToStep fires with {int}', (step: number) => {
  assert.equal(goToStepArg, step);
});

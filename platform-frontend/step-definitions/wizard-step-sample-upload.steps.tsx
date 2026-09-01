import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StepSampleUpload } from '../src/components/wizard/StepSampleUpload';
import { useWizardStore } from '../src/store/wizardStore';
import { renderWithProviders } from './support/render';

let rendered: RenderResult;

Before({ tags: '@wizard-sample-upload' }, () => {
  useWizardStore.getState().reset();
});

Given('the wizard already has sample fields {string}', (fieldsCsv: string) => {
  useWizardStore.getState().setSampleFields(fieldsCsv.split(',').map(f => f.trim()));
});

When('I render the sample upload step', () => {
  rendered = renderWithProviders(<StepSampleUpload />);
});

When('I click Clear and re-upload', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Clear and re-upload' });
  await userEvent.click(button);
});

When('I upload a sample JSON file with content {string}', async (jsonContent: string) => {
  const fileInput = rendered.container.querySelector('input[type="file"]') as HTMLInputElement;
  const file = new File([jsonContent], 'sample.json', { type: 'application/json' });
  await userEvent.upload(fileInput, file);
});

When('I upload an unsupported file type', async () => {
  const fileInput = rendered.container.querySelector('input[type="file"]') as HTMLInputElement;
  const file = new File(['not relevant'], 'sample.txt', { type: 'text/plain' });
  await userEvent.upload(fileInput, file);
});

Then('the sample upload step shows {int} pre-loaded fields', (count: number) => {
  const banner = within(rendered.container).getByText(new RegExp(`${count} fields`));
  assert.ok(banner);
  const items = rendered.container.querySelectorAll('.field-list-item');
  assert.equal(items.length, count);
});

Then('the sample upload step shows the upload prompt with no pre-loaded fields', () => {
  const uploadButton = within(rendered.container).queryByRole('button', { name: 'Upload sample file' });
  assert.ok(uploadButton, 'expected the upload prompt to be shown');
});

Then('the wizard store sampleFields is empty', () => {
  assert.deepEqual(useWizardStore.getState().sampleFields, []);
});

Then('the wizard store sampleFields includes {string}', (field: string) => {
  assert.ok(
    useWizardStore.getState().sampleFields.includes(field),
    `expected sampleFields to include "${field}", got ${JSON.stringify(useWizardStore.getState().sampleFields)}`,
  );
});

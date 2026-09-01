import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StepFieldMapping } from '../src/components/wizard/StepFieldMapping';
import { StepBlotterConfig } from '../src/components/wizard/StepBlotterConfig';
import { useWizardStore } from '../src/store/wizardStore';
import { renderWithProviders } from './support/render';

let rendered: RenderResult;

Before({ tags: '@wizard-field-mapping' }, () => {
  useWizardStore.getState().reset();
});

Given('the wizard has sample fields {string}', (fieldsCsv: string) => {
  useWizardStore.getState().setSampleFields(fieldsCsv.split(',').map(f => f.trim()));
});

Given('the wizard has field mappings for {string}', (pathsCsv: string) => {
  const paths = pathsCsv.split(',').map(p => p.trim());
  useWizardStore.getState().setFieldMappings(
    paths.map(p => ({ fieldPath: p, displayName: p, type: 'STRING' as const, required: false })),
  );
});

When('I render the field mapping step', () => {
  rendered = renderWithProviders(<StepFieldMapping />);
});

When('I render the blotter config step', () => {
  rendered = renderWithProviders(<StepBlotterConfig />);
});

When('I type {string} into the target path of mapping row {int}', async (value: string, rowNum: number) => {
  const rows = within(rendered.container).getAllByPlaceholderText('target.path');
  const input = rows[rowNum - 1];
  await userEvent.clear(input);
  await userEvent.type(input, value);
});

When('I click Add field', async () => {
  const button = within(rendered.container).getByRole('button', { name: '+ Add field' });
  await userEvent.click(button);
});

When('I remove mapping row {int}', async (rowNum: number) => {
  const removeButtons = within(rendered.container).getAllByRole('button', { name: '✕' });
  await userEvent.click(removeButtons[rowNum - 1]);
});

When('I check the blotter column checkbox for {string}', async (path: string) => {
  const checkbox = within(rendered.container).getByRole('checkbox', { name: new RegExp(path.replace('.', '\\.')) });
  await userEvent.click(checkbox);
});

Then('the field mapping table has {int} rows', (count: number) => {
  const rows = within(rendered.container).getAllByPlaceholderText('target.path');
  assert.equal(rows.length, count);
});

Then('mapping row {int}\'s target path still reads {string}', (rowNum: number, expected: string) => {
  const rows = within(rendered.container).getAllByPlaceholderText('target.path') as HTMLInputElement[];
  assert.equal(rows[rowNum - 1].value, expected);
});

Then('only {string} is offered as a blotter column candidate', (path: string) => {
  const rows = rendered.container.querySelectorAll('.column-config-row');
  assert.equal(rows.length, 1, `expected exactly one column candidate row, got ${rows.length}`);
  assert.equal(rows[0].querySelector('code')?.textContent, path);
});

Then('a header label input appears for the {string} column', (path: string) => {
  // Once a column is selected/added, its header-label + width + formatter inputs render.
  const headerInputs = within(rendered.container).getAllByPlaceholderText('Header label');
  assert.equal(headerInputs.length, 1, `expected a header-label input for "${path}"`);
});

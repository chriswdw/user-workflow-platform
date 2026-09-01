import { Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import { StepSourceConfig } from '../src/components/wizard/StepSourceConfig';
import { useWizardStore } from '../src/store/wizardStore';
import { renderWithProviders } from './support/render';

// NOTE: never import `screen` from @testing-library/react in step definitions. It binds to
// `document` at module-import time, which happens before dom-setup.ts's BeforeAll hook installs
// the jsdom document — so `screen` ends up permanently bound to "no document". Query via
// `within(rendered.container)` off the RenderResult instead.
let rendered: RenderResult;

Given('a fresh wizard store', () => {
  useWizardStore.getState().reset();
});

When('I render the source configuration step', () => {
  rendered = renderWithProviders(<StepSourceConfig />);
});

Then('all four source type options are shown', () => {
  const radios = within(rendered.container).getAllByRole('radio');
  const labels = radios.map(r => r.getAttribute('value'));
  assert.deepEqual(labels.sort(), ['DB_POLL', 'FILE_SHARE', 'KAFKA', 'MANUAL_UPLOAD']);
});

Then('no source type is pre-selected', () => {
  const radios = within(rendered.container).getAllByRole('radio') as HTMLInputElement[];
  assert.ok(radios.every(r => !r.checked), 'expected no radio to be pre-checked');
});

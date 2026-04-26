import { Given, When, Then } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { formatValue } from '../src/utils/formatValue';
import type { Action, ActionInputField } from '../src/types/DetailViewConfig';

let rawFieldValue: unknown;
let formattedResult: string;
let currentAction: Action;
let currentWorkItemPendingCheckerId: string | null = null;
let currentWorkItemPendingCheckerTransition: string | null = null;

// ── formatValue steps ─────────────────────────────────────────────────────────

Given('a field value of {string}', (value: string) => {
  rawFieldValue = value;
});

When('I format the value with formatter {string}', (formatter: string) => {
  formattedResult = formatValue(rawFieldValue, formatter);
});

Then('the formatted value is {string}', (expected: string) => {
  assert.equal(formattedResult, expected);
});

Then('the formatted value is not raw {string}', (raw: string) => {
  assert.notEqual(formattedResult, raw);
});

// ── Action input field steps ──────────────────────────────────────────────────

Given('an action with input field {string} of type {string} labelled {string}',
  (field: string, inputType: string, label: string) => {
    currentAction = {
      transition: 'test-transition',
      label: 'Test Action',
      style: 'PRIMARY',
      visibleInStates: ['UNDER_REVIEW'],
      inputFields: [
        { field, label, inputType: inputType as ActionInputField['inputType'] },
      ],
    };
  }
);

Given('the input field is required', () => {
  const fields = currentAction.inputFields ?? [];
  currentAction = {
    ...currentAction,
    inputFields: fields.map(f => ({ ...f, required: true })),
  };
});

Given('the input field has options {string}', (optionsCsv: string) => {
  const options = optionsCsv.split(',').map(s => s.trim());
  const fields = currentAction.inputFields ?? [];
  currentAction = {
    ...currentAction,
    inputFields: fields.map(f => ({ ...f, options })),
  };
});

Then('the action has {int} input field', (count: number) => {
  assert.equal((currentAction.inputFields ?? []).length, count);
});

Then('the input field label is {string}', (label: string) => {
  assert.equal(currentAction.inputFields?.[0]?.label, label);
});

Then('the input field type is {string}', (inputType: string) => {
  assert.equal(currentAction.inputFields?.[0]?.inputType, inputType);
});

Then('the input field is marked required', () => {
  assert.equal(currentAction.inputFields?.[0]?.required, true);
});

Then('the input field has {int} options', (count: number) => {
  assert.equal((currentAction.inputFields?.[0]?.options ?? []).length, count);
});

// ── Maker-checker steps ───────────────────────────────────────────────────────

Given('a work item with pendingCheckerId {string} and pendingCheckerTransition {string}',
  (checkerId: string, transition: string) => {
    currentWorkItemPendingCheckerId = checkerId;
    currentWorkItemPendingCheckerTransition = transition;
  }
);

Given('a work item with no pending checker', () => {
  currentWorkItemPendingCheckerId = null;
  currentWorkItemPendingCheckerTransition = null;
});

Then('the maker checker transition is {string}', (transition: string) => {
  assert.equal(currentWorkItemPendingCheckerTransition, transition);
  assert.notEqual(currentWorkItemPendingCheckerId, null);
});

Then('the work item has no pending checker', () => {
  assert.equal(currentWorkItemPendingCheckerId, null);
});

import { Given, When, Then } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { resolve } from '../src/utils/fieldPathResolver';
import { maskIfNeeded } from '../src/utils/fieldMasking';
import { currentUserRole } from './shared.steps';

let fields: Record<string, unknown> = {};
let resolvedValue: unknown;
let fieldValue: unknown;
let maskingRoles: string[] | undefined;
let displayedValue: unknown;

Given('a work item with field {string} set to {string}', (path: string, value: string) => {
  fields = {};
  const parts = path.split('.');
  // Build nested object from dot-notation path
  let obj: Record<string, unknown> = fields;
  for (let i = 0; i < parts.length - 1; i++) {
    obj[parts[i]] = {};
    obj = obj[parts[i]] as Record<string, unknown>;
  }
  obj[parts[parts.length - 1]] = value;
});

Given('a work item with no fields', () => {
  fields = {};
});

When('I resolve {string} from the work item', (path: string) => {
  resolvedValue = resolve(fields, path);
});

Then('the resolved value is {string}', (expected: string) => {
  assert.equal(resolvedValue, expected);
});

Then('the resolved value is undefined', () => {
  assert.equal(resolvedValue, undefined);
});

Given('a field value {string}', (value: string) => {
  fieldValue = value;
  maskingRoles = undefined;
});

Given('the field requires role {string} to view unmasked', (role: string) => {
  maskingRoles = [role];
});

Given('the field has no masking roles', () => {
  maskingRoles = undefined;
});

When('I apply field masking', () => {
  displayedValue = maskIfNeeded(fieldValue, maskingRoles, currentUserRole);
});

Then('the displayed value is {string}', (expected: string) => {
  assert.equal(displayedValue, expected);
});

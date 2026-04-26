import { Given, Then } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { isActionVisible } from '../src/utils/actionVisibility';
import type { Action } from '../src/types/DetailViewConfig';
import { currentUserRole } from './shared.steps';

let action: Action;
let currentState: string;

Given('an action visible in states {string}', (states: string) => {
  action = {
    transition: 'test-transition',
    label: 'Test Action',
    style: 'PRIMARY',
    visibleInStates: states.split(',').map(s => s.trim()),
  };
});

Given('an action visible in states {string} for roles {string}', (states: string, roles: string) => {
  action = {
    transition: 'test-transition',
    label: 'Test Action',
    style: 'PRIMARY',
    visibleInStates: states.split(',').map(s => s.trim()),
    visibleRoles: [roles],
  };
});

Given('the work item is in state {string}', (state: string) => {
  currentState = state;
});

Then('the action should be visible', () => {
  assert.equal(isActionVisible(action, currentState, currentUserRole), true);
});

Then('the action should not be visible', () => {
  assert.equal(isActionVisible(action, currentState, currentUserRole), false);
});

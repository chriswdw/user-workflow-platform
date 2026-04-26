import { Given } from '@cucumber/cucumber';

// Shared state accessible across step files in the same scenario
export let currentUserRole: string = 'ANALYST';

Given('the current user has role {string}', (role: string) => {
  currentUserRole = role;
});

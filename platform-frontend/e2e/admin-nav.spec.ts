import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth';

test.describe('Admin nav visibility', () => {
  test('PLATFORM_ADMIN sees Source Connections and All Drafts buttons', async ({ page }) => {
    await loginAs(page, 'Administrator');

    await expect(page.getByRole('button', { name: 'Source Connections' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'All Drafts' })).toBeVisible();
  });

  test('ANALYST does not see admin-only nav buttons', async ({ page }) => {
    await loginAs(page, 'Analyst');

    await expect(page.getByRole('button', { name: 'Source Connections' })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'All Drafts' })).not.toBeVisible();
  });

  test('PLATFORM_ADMIN can navigate to All Drafts view', async ({ page }) => {
    await page.route('**/api/v1/workflow-type-submissions/all-drafts', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));

    await loginAs(page, 'Administrator');
    await page.getByRole('button', { name: 'All Drafts' }).click();
    await expect(page.getByText('All Draft Submissions (0)')).toBeVisible();
  });

  test('PLATFORM_ADMIN can navigate to Source Connections view', async ({ page }) => {
    await page.route('**/api/v1/source-connections**', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));

    await loginAs(page, 'Administrator');
    await page.getByRole('button', { name: 'Source Connections' }).click();
    await expect(page.getByRole('heading', { name: /source connections/i })).toBeVisible();
  });
});

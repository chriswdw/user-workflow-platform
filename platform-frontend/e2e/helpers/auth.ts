import { type Page } from '@playwright/test';
import { mockDefaultApis } from './api-mocks';

type RoleLabel = 'Analyst' | 'Supervisor' | 'Read Only' | 'Administrator';

export async function loginAs(page: Page, label: RoleLabel): Promise<void> {
  await mockDefaultApis(page);

  await page.route('**/api/dev/token', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ token: 'e2e-test-token' }),
    }));

  await page.goto('/');
  await page.getByRole('button', { name: `Continue as ${label}` }).click();

  await page.getByRole('button', { name: 'Blotter' }).waitFor({ state: 'visible', timeout: 5_000 });
}

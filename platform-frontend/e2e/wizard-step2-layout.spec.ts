import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth';
import { MOCK_SUBMISSION } from './helpers/fixtures';

async function navigateToStep2(page: Parameters<typeof loginAs>[0]) {
  await loginAs(page, 'Analyst');

  await page.route('**/api/v1/workflow-type-submissions', route => {
    if (route.request().method() === 'POST') {
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_SUBMISSION),
      });
    } else {
      route.continue();
    }
  });

  await page.getByRole('button', { name: 'Create Workflow Type' }).click();
  await page.locator('#workflowType').fill('E2E_TEST_TYPE');
  await page.locator('#displayName').fill('E2E Test Type');
  await page.getByRole('button', { name: 'Next →' }).click();
  await page.getByText('Source Configuration').waitFor({ state: 'visible', timeout: 5_000 });
}

test.describe('Wizard step 2 — radio button layout', () => {
  test('each radio option renders inline (input and label text share the same vertical centre)', async ({ page }) => {
    await navigateToStep2(page);

    const options = page.locator('.radio-option');
    const count = await options.count();
    expect(count).toBe(4);

    for (let i = 0; i < count; i++) {
      const option = options.nth(i);
      const input = option.locator('input[type="radio"]');

      const optionBox = await option.boundingBox();
      const inputBox = await input.boundingBox();

      expect(optionBox).not.toBeNull();
      expect(inputBox).not.toBeNull();

      const optionCentreY = optionBox!.y + optionBox!.height / 2;
      const inputCentreY = inputBox!.y + inputBox!.height / 2;

      // Without display:flex the input appears on a line above the text, so this delta >> 4px
      expect(Math.abs(inputCentreY - optionCentreY)).toBeLessThan(4);
    }
  });

  test('wizard body matches visual snapshot', async ({ page }) => {
    await navigateToStep2(page);
    await expect(page.locator('.wizard-body')).toHaveScreenshot('wizard-step2-body-chromium.png');
  });
});

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

test.describe('Wizard PATCH URL — save draft', () => {
  test('clicking Next on step 2 (MANUAL_UPLOAD) sends PATCH to /workflow-type-submissions/{id} with no /draft suffix', async ({ page }) => {
    await navigateToStep2(page);

    const patchPromise = page.waitForRequest(req =>
      req.method() === 'PATCH' &&
      req.url().includes('/workflow-type-submissions/'));

    await page.route(`**/api/v1/workflow-type-submissions/${MOCK_SUBMISSION.id}`, route => {
      if (route.request().method() === 'PATCH') {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...MOCK_SUBMISSION, currentStep: 2 }),
        });
      } else {
        route.continue();
      }
    });

    await page.getByRole('radio', { name: 'Manual Upload' }).click();
    await page.getByRole('button', { name: 'Next →' }).click();

    const patchRequest = await patchPromise;
    const url = patchRequest.url();

    expect(url).toMatch(/\/workflow-type-submissions\/sub-e2e-1$/);
    expect(url).not.toContain('/draft');
  });
});

test.describe('Submission GET hook URLs — sub-path style', () => {
  test('page load fires GET /workflow-type-submissions/pending — not ?status=PENDING_APPROVAL', async ({ page }) => {
    const requests: string[] = [];
    page.on('request', req => {
      if (req.url().includes('workflow-type-submissions')) requests.push(req.url());
    });

    await loginAs(page, 'Analyst');

    expect(requests.some(u => u.includes('/workflow-type-submissions/pending'))).toBe(true);
    expect(requests.some(u => u.includes('?status=PENDING_APPROVAL'))).toBe(false);
  });

  test('page load fires GET /workflow-type-submissions/my-drafts — not ?status=DRAFT', async ({ page }) => {
    const requests: string[] = [];
    page.on('request', req => {
      if (req.url().includes('workflow-type-submissions')) requests.push(req.url());
    });

    await loginAs(page, 'Analyst');

    expect(requests.some(u => u.includes('/workflow-type-submissions/my-drafts'))).toBe(true);
    expect(requests.some(u => u.includes('?status=DRAFT'))).toBe(false);
  });

  test('page load fires GET /workflow-type-submissions/my-rejected — not ?status=REJECTED', async ({ page }) => {
    const requests: string[] = [];
    page.on('request', req => {
      if (req.url().includes('workflow-type-submissions')) requests.push(req.url());
    });

    await loginAs(page, 'Analyst');

    expect(requests.some(u => u.includes('/workflow-type-submissions/my-rejected'))).toBe(true);
    expect(requests.some(u => u.includes('?status=REJECTED'))).toBe(false);
  });
});

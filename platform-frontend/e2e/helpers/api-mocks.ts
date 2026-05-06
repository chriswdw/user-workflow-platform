import { type Page } from '@playwright/test';

export async function mockDefaultApis(page: Page): Promise<void> {
  const emptyArray = JSON.stringify([]);

  await page.route('**/api/v1/work-items**', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: emptyArray }));

  await page.route('**/api/v1/workflow-type-submissions/pending', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: emptyArray }));

  await page.route('**/api/v1/workflow-type-submissions/my-drafts', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: emptyArray }));

  await page.route('**/api/v1/workflow-type-submissions/my-rejected', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: emptyArray }));
}

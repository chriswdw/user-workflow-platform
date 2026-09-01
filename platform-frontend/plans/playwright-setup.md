# Playwright E2E Test Setup

## Context

### Problem statement
The frontend has two working test layers:
- **BDD Cucumber** (`npm run test:bdd`): 45 scenarios across `features/*.feature`, testing utility functions, Zustand store mutations, and wizard step validation. Implemented in `step-definitions/*.steps.ts`. Does NOT render React components.
- **Jest + RTL** (`npm test`): 20 tests across `src/__tests__/`, testing component DOM structure, API hook URLs, and role-based nav visibility. Uses JSDOM — has no concept of computed CSS layout.

**Gap**: Neither layer can catch bugs caused by missing or incorrect CSS, real network call failures, or full page interaction flows. Three concrete bugs were found in this codebase that Jest/RTL cannot detect:

| Bug | Root cause | Why JSDOM misses it |
|-----|------------|---------------------|
| Radio buttons rendered on blank lines between options | `.radio-option { display: flex }` missing from CSS | JSDOM renders no CSS layout |
| 401 on wizard step 2 "Next" for MANUAL_UPLOAD | URL was `/workflow-type-submissions/{id}/draft` (extra `/draft`) | URL is now covered by the Jest hook test, but the full render + click + network round-trip is not |
| Admin "All Drafts" nav item missing | Feature not implemented | Covered by Jest but only at component level, not at full login → render flow |

A Playwright layer catches all three by running a real Chromium browser against the Vite dev server. Backend calls are intercepted with `page.route()` — no Spring Boot instance required.

### Authentication mechanism (critical context)
- `LoginPage.tsx` renders four buttons: "Continue as Analyst", "Continue as Supervisor", "Continue as Read Only", "Continue as Administrator"
- Clicking a button calls `useDevLogin().mutate({ userId, role, tenantId })`
- `useDevLogin` (`src/api/useDevLogin.ts`) POSTs to `POST /api/dev/token` using **raw axios** (not the `client` instance — so the URL is `/api/dev/token`, not `/api/v1/api/dev/token`)
- On success the hook calls `useAuthStore.getState().setAuth(token, userId, role, tenantId)`
- The Zustand auth store has **no `persist` middleware** — state is in-memory only, cannot be pre-seeded via `localStorage`

This means E2E tests must: mock `POST /api/dev/token`, navigate to the app, click the appropriate button, and wait for the nav header to appear.

### Request interception architecture
- `src/api/client.ts` creates `axios.create({ baseURL: '/api/v1' })` — all `client.*` calls produce URLs like `/api/v1/workflow-type-submissions`
- Vite proxies `/api/**` to the backend (verify exact proxy config in `vite.config.ts` if needed — it does not affect Playwright because `page.route()` intercepts at the browser network layer, before any proxy)
- In Playwright, patterns like `**/api/v1/workflow-type-submissions` match `http://localhost:5173/api/v1/workflow-type-submissions` ✓
- Pattern `**/api/dev/token` matches `http://localhost:5173/api/dev/token` ✓

### API endpoints used on page load (must be mocked to prevent console errors)
These fire immediately when `MainApp` renders:

| Hook | Method | URL |
|------|--------|-----|
| `useWorkItems` | GET | `/api/v1/work-items?workflowType=SETTLEMENT_EXCEPTION` |
| `usePendingSubmissions` | GET | `/api/v1/workflow-type-submissions/pending` |
| `useMyDraftSubmissions` | GET | `/api/v1/workflow-type-submissions/my-drafts` |
| `useMyRejectedSubmissions` | GET | `/api/v1/workflow-type-submissions/my-rejected` |

---

## Impacted Files

### New files (all relative to `platform-frontend/`)

| Path | Purpose |
|------|---------|
| `playwright.config.ts` | Root Playwright config — browser, baseURL, webServer |
| `e2e/helpers/auth.ts` | `loginAs(page, role)` helper that mocks the token endpoint and clicks the login button |
| `e2e/helpers/api-mocks.ts` | `mockDefaultApis(page)` helper that stubs the four page-load endpoints with empty arrays |
| `e2e/helpers/fixtures.ts` | Shared `MOCK_SUBMISSION` constant (valid `WorkflowTypeSubmission` shape) |
| `e2e/admin-nav.spec.ts` | Tests that PLATFORM_ADMIN sees "Source Connections" and "All Drafts"; non-admin does not |
| `e2e/wizard-step2-layout.spec.ts` | Tests radio button inline alignment (bounding-box check + visual snapshot) and that MANUAL_UPLOAD next fires no 4xx |
| `e2e/wizard-api-urls.spec.ts` | Tests that save-draft PATCH URL has no `/draft` suffix and that all four GET query hooks use sub-paths not query params |

### Modified files

| Path | Change |
|------|--------|
| `package.json` | Add `@playwright/test` to `devDependencies`; add `test:e2e` and `test:e2e:ui` scripts |

### Committed test artifacts (generated on first run)

| Path | Purpose |
|------|---------|
| `e2e/__snapshots__/wizard-step2-layout.spec.ts-snapshots/radio-group-chromium.png` | Visual baseline for radio button layout |

---

## Technical Requirements

### Package
Install `@playwright/test` (latest). Do **not** pin a version in `package.json` — use `^` range. Download Chromium only (skip Firefox/WebKit for CI speed):
```
npm install -D @playwright/test
npx playwright install --with-deps chromium
```

### Playwright config constraints
- `testDir`: `./e2e`
- `use.baseURL`: `http://localhost:5173`
- `webServer.command`: `npm run dev` — starts Vite; `reuseExistingServer: !process.env.CI` so local runs reuse an already-running server
- `testIdAttribute`: default (`data-testid`) — do not change; the app uses `id` and ARIA roles for selectors, not data-testid
- `projects`: Chromium only — one `{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }` entry
- `snapshotDir`: default (`__snapshots__` alongside each spec file) — do not override

### Authentication helper design
`loginAs(page: Page, label: 'Analyst' | 'Supervisor' | 'Read Only' | 'Administrator')`:
1. Call `mockDefaultApis(page)` to stub page-load endpoints
2. Route `**/api/dev/token` → `route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'e2e-test-token' }) })`
3. `await page.goto('/')` 
4. `await page.getByRole('button', { name: \`Continue as ${label}\` }).click()`
5. `await page.getByRole('button', { name: 'Blotter' }).waitFor()` — confirms main app rendered

Note: the `label` parameter maps to LoginPage's `ROLES[n].label` values. "Administrator" logs in as `role: 'PLATFORM_ADMIN'`.

### API mock helper design
`mockDefaultApis(page: Page)` sets up four `page.route()` handlers that return `[]` (empty JSON array) for each of the four page-load GET calls. Use `{ status: 200, contentType: 'application/json', body: '[]' }` for the fulfill call. Register these routes **before** `page.goto()`.

### Shared fixture constant
`MOCK_SUBMISSION` in `e2e/helpers/fixtures.ts` — the minimal object that satisfies `WorkflowTypeSubmissionSchema` (from `src/types/WorkflowTypeSubmission.ts`):
```ts
export const MOCK_SUBMISSION = {
  id: 'sub-e2e-1',
  tenantId: 'tenant-1',
  workflowType: 'E2E_TEST_TYPE',
  displayName: 'E2E Test Type',
  description: null,
  statusCode: 'DRAFT',
  statusDisplayName: 'Draft',
  submittedBy: 'analyst-1',
  submittedAt: null,
  reviewedBy: null,
  reviewedAt: null,
  rejectionReason: null,
  draftConfigs: {},
  currentStep: 1,
  version: 1,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};
```

### Wizard navigation — exact steps to reach step 2
The following sequence is used by multiple specs:

1. `await loginAs(page, 'Analyst')`
2. Mock `POST /api/v1/workflow-type-submissions` → `MOCK_SUBMISSION` (must be set up before step 3)
3. `await page.getByRole('button', { name: 'Create Workflow Type' }).click()` — opens WizardShell overlay (`.wizard-overlay`)
4. `await page.locator('#workflowType').fill('E2E_TEST_TYPE')` — StepBasicInfo field id is `workflowType`
5. `await page.locator('#displayName').fill('E2E Test Type')` — StepBasicInfo field id is `displayName`
6. `await page.getByRole('button', { name: 'Next →' }).click()` — triggers `handleNext()` in WizardShell; calls `createSubmission.mutate()` → POST to `/api/v1/workflow-type-submissions`
7. `await page.getByText('Source Configuration').waitFor()` — confirms step 2 (StepSourceConfig heading is `<h2 className="wizard-step-title">Source Configuration</h2>`)

Note: `Next →` is disabled until `isStepComplete(1, store)` returns true. That requires both `workflowType` (matching `/^[A-Z][A-Z0-9_]*$/`) and `displayName` (non-empty) to be set. Fill both fields before clicking Next.

### Step 2 PATCH mock
When advancing from step 2 by clicking Next, `WizardShell.handleNext()` (lines 72–83) calls `saveDraft.mutate({ draftConfigs: payload, currentStep: 2 })` which fires `PATCH /api/v1/workflow-type-submissions/{id}`. Mock this as:
```ts
page.route(`**/api/v1/workflow-type-submissions/${MOCK_SUBMISSION.id}`, route => {
  if (route.request().method() === 'PATCH') {
    route.fulfill({ status: 200, contentType: 'application/json',
      body: JSON.stringify({ ...MOCK_SUBMISSION, currentStep: 2 }) });
  } else {
    route.continue();
  }
});
```

### Visual snapshot approach
Use `expect(locator).toHaveScreenshot('name.png')` with a tight locator (`.wizard-body` or `.radio-group`). On first run Playwright writes the PNG baseline to `e2e/__snapshots__/`. On subsequent runs it pixel-diffs. If the radio CSS is missing (`display: flex` absent), the layout is a column with a blank line above each label — visually distinct from the correct inline layout. The snapshot will fail in CI until the baseline is committed.

To generate/update snapshots:
```
npx playwright test --update-snapshots e2e/wizard-step2-layout.spec.ts
```
Commit the generated PNG alongside the spec.

---

## Step-by-Step

### Step 1 — Install the package and download Chromium
```bash
cd platform-frontend
npm install -D @playwright/test
npx playwright install --with-deps chromium
```
Verify: `node_modules/.bin/playwright --version` prints a version string.

---

### Step 2 — Create `playwright.config.ts`
File: `platform-frontend/playwright.config.ts`
```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
```

---

### Step 3 — Add npm scripts to `package.json`
Add two entries to the `"scripts"` block. The file is `platform-frontend/package.json`.

After editing, the scripts block must contain:
```json
"test:e2e": "playwright test",
"test:e2e:ui": "playwright test --ui"
```
Do not remove existing scripts.

---

### Step 4 — Create `e2e/helpers/fixtures.ts`
File: `platform-frontend/e2e/helpers/fixtures.ts`
```ts
export const MOCK_SUBMISSION = {
  id: 'sub-e2e-1',
  tenantId: 'tenant-1',
  workflowType: 'E2E_TEST_TYPE',
  displayName: 'E2E Test Type',
  description: null,
  statusCode: 'DRAFT',
  statusDisplayName: 'Draft',
  submittedBy: 'analyst-1',
  submittedAt: null,
  reviewedBy: null,
  reviewedAt: null,
  rejectionReason: null,
  draftConfigs: {},
  currentStep: 1,
  version: 1,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};
```

---

### Step 5 — Create `e2e/helpers/api-mocks.ts`
File: `platform-frontend/e2e/helpers/api-mocks.ts`
```ts
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
```

Note: `**/api/v1/work-items**` uses a trailing `**` to match the `?workflowType=...` query string.

---

### Step 6 — Create `e2e/helpers/auth.ts`
File: `platform-frontend/e2e/helpers/auth.ts`
```ts
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

  // Wait until the main app header is visible — confirms auth store updated and MainApp rendered
  await page.getByRole('button', { name: 'Blotter' }).waitFor({ state: 'visible', timeout: 5_000 });
}
```

---

### Step 7 — Create `e2e/admin-nav.spec.ts`
File: `platform-frontend/e2e/admin-nav.spec.ts`
```ts
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
    // Also mock the all-drafts endpoint that AllDraftsAdminView calls
    await page.route('**/api/v1/workflow-type-submissions/all-drafts', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));

    await loginAs(page, 'Administrator');
    await page.getByRole('button', { name: 'All Drafts' }).click();
    await expect(page.getByText('All Draft Submissions (0)')).toBeVisible();
  });

  test('PLATFORM_ADMIN can navigate to Source Connections view', async ({ page }) => {
    // Mock the source connections endpoint
    await page.route('**/api/v1/source-connections**', route =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));

    await loginAs(page, 'Administrator');
    await page.getByRole('button', { name: 'Source Connections' }).click();
    // SourceConnectionsAdminView renders a heading — check it appears
    await expect(page.getByRole('heading', { name: /source connections/i })).toBeVisible();
  });
});
```

---

### Step 8 — Create `e2e/wizard-step2-layout.spec.ts`
File: `platform-frontend/e2e/wizard-step2-layout.spec.ts`

This spec navigates to step 2 of the wizard and runs two checks:
1. **Bounding-box assertion**: confirms the radio `<input>` and its label text are vertically centred within 4px of each other — fails if `display: flex` is absent from `.radio-option`
2. **Visual snapshot**: pixel-level regression guard for the entire step 2 body

```ts
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

    // Check all four radio options
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

      // Inline layout: input vertical centre within 4px of label container vertical centre
      // Without display:flex the input appears on a line above the text, so this delta >> 4px
      expect(Math.abs(inputCentreY - optionCentreY)).toBeLessThan(4);
    }
  });

  test('wizard body matches visual snapshot', async ({ page }) => {
    await navigateToStep2(page);
    // Snapshot the wizard body — any CSS regression (missing flex, wrong gap, etc.) fails here
    await expect(page.locator('.wizard-body')).toHaveScreenshot('wizard-step2-body-chromium.png');
  });
});
```

**Important**: Run with `--update-snapshots` on first execution to generate the baseline PNG, then commit it:
```bash
npx playwright test --update-snapshots e2e/wizard-step2-layout.spec.ts
git add e2e/__snapshots__/
```

---

### Step 9 — Create `e2e/wizard-api-urls.spec.ts`
File: `platform-frontend/e2e/wizard-api-urls.spec.ts`

This spec intercepts network requests to assert exact URLs — the class of bug where Spring Security returns 401 for an unregistered path.

```ts
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

    // Capture the PATCH request before triggering it
    const patchPromise = page.waitForRequest(req =>
      req.method() === 'PATCH' &&
      req.url().includes('/workflow-type-submissions/'));

    // Set up the PATCH mock response
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

    // Select Manual Upload — no connection dropdown required, Next is immediately enabled
    await page.getByRole('radio', { name: 'Manual Upload' }).click();
    await page.getByRole('button', { name: 'Next →' }).click();

    const patchRequest = await patchPromise;
    const url = patchRequest.url();

    // Must be exactly /api/v1/workflow-type-submissions/{id} — no /draft suffix
    expect(url).toMatch(/\/workflow-type-submissions\/sub-e2e-1$/);
    expect(url).not.toContain('/draft');
  });
});

test.describe('Submission GET hook URLs — sub-path style', () => {
  // These tests verify that no hook reverts to the broken ?status= query-param style
  // which Spring Security rejects (the endpoints use /pending, /my-drafts, etc.)

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
```

---

### Step 10 — Generate visual snapshot baseline and verify all tests pass

```bash
# Generate the screenshot baseline (only needed once; commit the PNG)
npx playwright test --update-snapshots e2e/wizard-step2-layout.spec.ts

# Commit the snapshots
git add e2e/__snapshots__/

# Run the full E2E suite
npm run test:e2e

# Expected: all specs pass
# wizard-step2-layout.spec.ts  — 2 tests
# admin-nav.spec.ts            — 4 tests
# wizard-api-urls.spec.ts      — 5 tests
```

If the snapshot test fails after generation, check that the `wizard-body` locator uniquely targets the step 2 content and that the Vite dev server is running in its normal state (no hot-reload artefacts).

---

## Test Plan

### What each test covers and which bug it would catch

| Spec | Test | Bug class caught | Assertion |
|------|------|-----------------|-----------|
| `wizard-step2-layout` | "each radio option renders inline" | CSS layout — missing `display: flex` on `.radio-option` | `Math.abs(inputCentreY - optionCentreY) < 4` for all 4 options |
| `wizard-step2-layout` | "wizard body matches visual snapshot" | Any CSS regression in step 2 (layout, spacing, colour) | Pixel diff against committed PNG baseline |
| `wizard-api-urls` | "PATCH to /workflow-type-submissions/{id} with no /draft suffix" | Wrong URL causing Spring Security 401 | `url.endsWith('/sub-e2e-1')` AND `!url.includes('/draft')` |
| `wizard-api-urls` | "fires GET …/pending — not ?status=" | Regression to broken query-param style | URL contains `/pending`; does NOT contain `?status=` |
| `wizard-api-urls` | "fires GET …/my-drafts — not ?status=" | Same regression class | URL contains `/my-drafts`; does NOT contain `?status=` |
| `wizard-api-urls` | "fires GET …/my-rejected — not ?status=" | Same regression class | URL contains `/my-rejected`; does NOT contain `?status=` |
| `admin-nav` | "PLATFORM_ADMIN sees Source Connections and All Drafts" | Missing admin-only nav items | `toBeVisible()` on both buttons |
| `admin-nav` | "ANALYST does not see admin-only nav buttons" | Admin buttons leaking to non-admin roles | `not.toBeVisible()` on both buttons |
| `admin-nav` | "can navigate to All Drafts view" | AllDraftsAdminView not wired into routing | Heading "All Draft Submissions (0)" visible after click |
| `admin-nav` | "can navigate to Source Connections view" | SourceConnectionsAdminView not wired into routing | Heading matching `/source connections/i` visible after click |

### What these tests cannot cover (remains for future work)
- Multi-step wizard completion through to submission (step 3–7 not navigated)
- Approval queue workflow (approver reviewing and approving/rejecting a submission)
- Blotter rendering with real data (ag-Grid interaction)
- Mobile viewport / responsive layout
- Accessibility (axe-core integration not included in this plan)

### Run commands reference

| Command | When to use |
|---------|------------|
| `npm run test:e2e` | Full E2E suite (CI or pre-PR check) |
| `npm run test:e2e:ui` | Interactive debugging with Playwright UI |
| `npx playwright test --update-snapshots` | After intentional CSS changes — regenerate visual baselines |
| `npx playwright test e2e/admin-nav.spec.ts --headed` | Debug a single spec with visible browser |
| `npx playwright show-report` | View HTML report after a run |

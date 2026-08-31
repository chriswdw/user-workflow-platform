# Prompt 15 — platform-frontend UI Components, BDD, and E2E Tests

## Goal
Implement all React components, the application shell (`App.tsx`, `main.tsx`), Cucumber.js BDD tests, and Playwright E2E tests. After this prompt the entire frontend is complete.

## Component architecture rules
- Every component has a named `interface <ComponentName>Props { readonly ... }` for its props
- All React function components use destructured props with explicit type: `function Foo({ bar }: FooProps)`
- Named exports only — no default exports
- Data-fetching hooks in `src/api/`; pure logic in `src/utils/`; components only render
- `React.FC` only when `children` is used; otherwise plain function with explicit return type

---

## `src/main.tsx`
```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import { App } from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode><App /></StrictMode>
);
```

## `src/App.tsx`
Wrap everything in `<QueryClientProvider client={queryClient}>`. Show `<LoginPage />` when no auth token, otherwise `<MainApp />`.

`MainApp` manages view state (`AppView = 'blotter' | 'wizard' | 'approval-queue' | 'my-submissions' | 'admin-connections' | 'admin-drafts'`). Header with:
- Blotter nav button
- Create Workflow Type button (calls `reset()` then `setView('wizard')`)
- Pending Approvals button with badge showing count from `usePendingSubmissions()`
- My Submissions button with badge showing `myDrafts.length + myRejected.length`
- PLATFORM_ADMIN-only: Source Connections and All Drafts nav buttons
- Workflow type selector (dropdown bound to `workflowType` state, disabled when not in blotter view)
- User info display (`userId · role`)
- Logout button

Main content area: route to appropriate component by `view`. Wizard and DetailPanel are overlays (absolute-positioned) — wizard shows when `view === 'wizard'`, detail panel shows when in blotter view and `selectedItemId` is non-null. `<Toaster>` from react-hot-toast at bottom-right.

---

## `src/components/LoginPage.tsx`

```tsx
interface LoginPageProps {}
export function LoginPage({}: LoginPageProps): React.ReactElement
```
Dev login form with fields `userId`, `role` (select: ANALYST/PLATFORM_ADMIN/COMPLIANCE), `tenantId`. On submit, calls `useDevLogin()` which GETs `/api/dev/token?userId=&role=&tenantId=` and stores the returned JWT in `authStore`. Show loading/error states.

---

## `src/components/blotter/Blotter.tsx`

```tsx
interface BlotterProps {
  readonly config: BlotterConfig;
  readonly items: WorkItem[];
  readonly userRole: string;
  readonly onSelectItem: (id: string) => void;
}
export function Blotter({ config, items, userRole, onSelectItem }: BlotterProps): React.ReactElement
```
Uses `AgGridReact` with `columnDefs` derived from `config.columns`. For each column:
- `field` maps to a dot-notation AG Grid field path
- `formatter` maps to a `valueFormatter` using `formatValue(value, formatter)`
- Apply `fieldMasking` for columns with classification (check `fieldTypeRegistry` if available)

Row click calls `onSelectItem(item.id)`. Use `ag-theme-balham` class. Row height 36px.

---

## `src/components/detail/DetailPanel.tsx`

```tsx
interface DetailPanelProps {
  readonly workItemId: string;
  readonly onClose: () => void;
}
export function DetailPanel({ workItemId, onClose }: DetailPanelProps): React.ReactElement
```
Slide-in panel. Fetches `useWorkItem(workItemId)` and `useDetailViewConfig(workItem.workflowType)`. Shows:
- Header with status badge, close button, action buttons
- `<DetailView>` with sections
- `<AuditTrail>` collapsible at bottom
- `<MakerCheckerBanner>` if `workItem.pendingCheckerId` is set

### `src/components/detail/DetailView.tsx`
```tsx
interface DetailViewProps {
  readonly workItem: WorkItem;
  readonly config: DetailViewConfig;
}
```
Renders sections from config. Each section has a title and fields. Uses `resolveFieldPath(workItem.fields, field.path)` to get values, `formatValue(value, field.format)` for display.

### `src/components/detail/SectionRenderer.tsx`
Renders a single detail section with TWO_COLUMN, ONE_COLUMN, or TABLE layout.

### `src/components/detail/ActionButton.tsx`
```tsx
interface ActionButtonProps {
  readonly label: string;
  readonly onClick: React.MouseEventHandler<HTMLButtonElement>;
  readonly disabled?: boolean;
  readonly variant?: 'primary' | 'danger' | 'secondary';
}
```

### `src/components/detail/ActionFormModal.tsx`
Modal for transitions that require additional fields (e.g. rejection reason). Uses React Hook Form.

### `src/components/detail/ConfirmModal.tsx`
Generic confirmation modal.

### `src/components/detail/MakerCheckerBanner.tsx`
```tsx
interface MakerCheckerBannerProps {
  readonly pendingCheckerId: string;
  readonly pendingCheckerTransition: string | null;
}
```
Yellow banner: "Pending maker-checker approval for transition: {transition}"

---

## `src/components/audit/AuditTrail.tsx`

```tsx
interface AuditTrailProps { readonly workItemId: string; }
export function AuditTrail({ workItemId }: AuditTrailProps): React.ReactElement
```
Fetches `useAuditTrail(workItemId)`. Renders `<AuditEntryRow>` for each entry.

### `src/components/audit/AuditEntryRow.tsx`
```tsx
interface AuditEntryRowProps { readonly entry: AuditEntry; }
```
Shows timestamp, eventType, actorUserId, previousState→newState, list of changedFields.

---

## Wizard components

### `src/components/wizard/WizardShell.tsx`
```tsx
interface WizardShellProps { readonly onClose: () => void; }
```
Full-screen overlay with:
- Step indicator bar (7 steps with STEPS labels)
- Active step component
- Back/Next/Submit navigation buttons
- Error display

Navigation logic:
- **Step 1 Next**: call `useCreateSubmission().mutate(buildSubmissionPayload())`, on success store `submissionId`, advance to step 2. On 409: show "already exists" message pointing to My Submissions.
- **Steps 2–6 Next**: call `useSaveDraft(submissionId).mutate({draftConfigs: buildDraftConfigsPayload(), currentStep})`, advance to next step.
- **Step 7 Submit**: if `revisingSubmissionId`, call `useReviseSubmission(revisingSubmissionId).mutate(...)`, else call `useSubmitForApproval(submissionId).mutate()`. On success, close wizard and show toast.
- Escape key closes the wizard (addEventListener on mount).

Step components: `StepBasicInfo`, `StepSourceConfig`, `StepSampleUpload`, `StepFieldMapping`, `StepBlotterConfig`, `StepDetailViewConfig`, `StepReview`.

### `src/components/wizard/StepBasicInfo.tsx`
Fields: Workflow Type (text, pattern `^[A-Z][A-Z0-9_]*$`), Display Name, Description (textarea). Bound to `useWizardStore` setters.

### `src/components/wizard/StepSourceConfig.tsx`
Source type selector (KAFKA / DB_POLL / FILE_SHARE / MANUAL_UPLOAD). If not MANUAL_UPLOAD, shows source connection dropdown loaded from `useSourceConnections(sourceType)`. Shows connection type-specific config fields.

### `src/components/wizard/StepSampleUpload.tsx`
File input for CSV upload. On file selected, read with `FileReader`, parse headers with `extractCsvHeaders()`, extract field paths with `extractFieldPaths()`, call `setSampleFields()`.

### `src/components/wizard/StepFieldMapping.tsx`
Table of `fieldMappings`. Add rows. Each row: field path (from sampleFields dropdown or freetext), display name, type selector, required checkbox. Idempotency key selector (dropdown of existing field paths). Bound to `setFieldMappings()` and `setIdempotencyKeyField()`.

### `src/components/wizard/StepBlotterConfig.tsx`
Add/remove/reorder blotter columns. Each column: field path, header name, formatter (CURRENCY/DATE/DATETIME/BADGE/PERCENTAGE), width. Bound to `setBlotterColumns()`.

### `src/components/wizard/StepDetailViewConfig.tsx`
Add/remove sections. Each section: name, layout select, fields list (path, label, formatter). Bound to `setDetailSections()`.

### `src/components/wizard/StepReview.tsx`
Read-only summary of all wizard state. Shows all 6 config payloads as formatted JSON previews.

---

## `src/components/wizard/ApprovalQueue.tsx`
Fetches `usePendingSubmissions()`. Shows list of submissions with Approve/Reject buttons. Approve calls `useApproveSubmission().mutate(id)`. Reject shows a reason input modal, then calls `useRejectSubmission().mutate({id, reason})`.

## `src/components/wizard/MySubmissionsView.tsx`
```tsx
interface MySubmissionsViewProps { readonly onOpenWizard: () => void; }
```
Two sections: "My Drafts" (from `useMyDraftSubmissions()`) and "My Rejected" (from `useMyRejectedSubmissions()`). For each draft: Resume button (calls `hydrateForResume(submission)` then `onOpenWizard()`). For each rejected: Revise button (calls `hydrateForRevision(submission)` then `onOpenWizard()`). Discard button on each.

## `src/components/admin/SourceConnectionsAdminView.tsx`
Fetches `useAllSourceConnections()`. Create/edit/delete connections. Grant/revoke tenant access.

## `src/components/admin/AllDraftsAdminView.tsx`
```tsx
interface AllDraftsAdminViewProps { readonly onOpenWizard: () => void; }
```
Fetches `useAllDrafts()` (calls `GET /workflow-type-submissions/all-drafts`). Shows all draft submissions across users. Admin can discard any. Can open wizard to resume any.

---

## BDD Feature Files (`features/`)

### `features/wizard-foundation.feature`
```gherkin
Feature: Wizard step validation

  Scenario: Step 1 incomplete without workflowType
    Given the wizard is at step 1
    When workflowType is empty
    Then step 1 is not complete

  Scenario: Step 1 complete with valid inputs
    Given the wizard is at step 1
    When workflowType is "TRADE_BREAK" and displayName is "Trade Break"
    Then step 1 is complete

  Scenario: Step 1 rejects lowercase workflowType
    When workflowType is "trade_break" and displayName is "Trade Break"
    Then step 1 is not complete
```

### `features/workflow-wizard.feature`
```gherkin
Feature: Workflow wizard draft config payload

  Scenario: buildDraftConfigsPayload includes all 6 sections
    Given wizard state with workflowType "TRADE_BREAK" and 2 blotter columns
    When buildDraftConfigsPayload is called
    Then the payload contains keys workflowTypeDefinition, fieldTypeRegistry, ingestionSourceConfig, workflowConfig, blotterConfig, detailViewConfig

  Scenario: hydrateForResume restores wizard state from submission
    Given a submission with draftConfigs containing workflowType "TRADE_BREAK"
    When hydrateForResume is called with the submission at step 3
    Then the wizard workflowType is "TRADE_BREAK"
    And the wizard currentStep is 4
```

### `features/blotter.feature`
```gherkin
Feature: Blotter field resolution

  Scenario: Nested field path resolved from WorkItem.fields
    Given a work item with fields.trade.tradeId = "TRD-001"
    When the blotter resolves field "fields.trade.tradeId"
    Then the value is "TRD-001"

  Scenario: CURRENCY formatter applied
    Given a value of 1234567.89
    When formatValue is called with formatter "CURRENCY"
    Then the result contains "$1,234,567.89"
```

### `features/detail-view.feature`
```gherkin
Feature: Detail view field rendering

  Scenario: Field resolved from WorkItem.fields via dot path
    Given a work item with field "settlement.settlementDate" = "2024-01-15"
    When the detail view renders with config path "settlement.settlementDate" and format "DATE"
    Then the displayed value is the formatted date
```

### `step-definitions/` — Step definition files
- `wizard-foundation.steps.ts` — exercise `isStepComplete` directly (no React rendering needed)
- `wizard.steps.ts` — exercise `buildDraftConfigsPayload`, `hydrateForResume`, `hydrateForRevision` via store
- `blotter.steps.ts` — exercise `resolveFieldPath` and `formatValue`
- `detail-view.steps.ts` — exercise `resolveFieldPath` and `formatValue`
- `shared.steps.ts` — shared context setup (work item builders, etc.)

---

## Playwright E2E Tests (`e2e/`)

### `e2e/helpers/auth.ts`
```ts
export async function loginAs(page: Page, userId: string, role: string, tenantId: string): Promise<void>
// Navigate to app, fill login form, submit, wait for blotter to appear
```

### `e2e/helpers/api-mocks.ts`
```ts
export async function mockWorkItems(page: Page, items: Partial<WorkItem>[]): Promise<void>
// Use page.route('/api/v1/work-items*', ...) to intercept and return mock data
```

### `e2e/helpers/fixtures.ts`
```ts
export const DEMO_WORK_ITEM: WorkItem = { ... }  // realistic SETTLEMENT_EXCEPTION work item
```

### `e2e/wizard-api-urls.spec.ts`
```ts
test('create submission POSTs to correct endpoint', async ({ page }) => {
  await loginAs(page, 'alice', 'ANALYST', 'tenant-1');
  // intercept POST /api/v1/workflow-type-submissions
  // open wizard, fill step 1, click next
  // verify the intercepted request body has correct workflowType
});
```

### `e2e/wizard-step2-layout.spec.ts`
```ts
test('step 2 shows source type selector', async ({ page }) => {
  // open wizard, complete step 1, advance to step 2
  // assert source type radio buttons are visible
  // take screenshot for visual regression (snapshot stored in e2e/-snapshots/)
});
```

### `e2e/admin-nav.spec.ts`
```ts
test('PLATFORM_ADMIN sees admin nav items', async ({ page }) => {
  await loginAs(page, 'admin', 'PLATFORM_ADMIN', 'tenant-1');
  await expect(page.getByRole('button', { name: /Source Connections/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /All Drafts/ })).toBeVisible();
});

test('ANALYST does not see admin nav items', async ({ page }) => {
  await loginAs(page, 'alice', 'ANALYST', 'tenant-1');
  await expect(page.getByRole('button', { name: /Source Connections/ })).not.toBeVisible();
});
```

---

## `src/__tests__/` (Jest unit tests)

### `src/__tests__/App.test.tsx`
```tsx
test('renders login page when not authenticated', () => {
  // clear authStore token
  // render App
  // expect login form visible
});
test('renders blotter when authenticated', () => {
  // set authStore with test token
  // mock useWorkItems to return []
  // render App
  // expect blotter or "No work items" message visible
});
```

### `src/__tests__/api/useWorkflowTypeSubmissions.test.tsx`
Test `useMyDraftSubmissions` hook: mock axios, verify queryKey and endpoint called.

### `src/__tests__/api/useSubmissionActions.test.tsx`
Test `useApproveSubmission` mutation: verify correct endpoint and method.

---

## `src/config/blotterConfigs.ts` update
Add `WORKFLOW_TYPES` export:
```ts
export const WORKFLOW_TYPES: string[] = ['SETTLEMENT_EXCEPTION'];
```
(Imported by `App.tsx` for the workflow type selector.)

---

## Verification
```bash
cd platform-frontend
npm run build           # TypeScript check + Vite build
npm run test            # Jest unit tests
npm run test:bdd        # Cucumber.js BDD (exercises utils and store logic)
npm run test:e2e        # Playwright E2E (starts dev server automatically)
```

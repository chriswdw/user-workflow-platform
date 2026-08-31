# Prompt 14 — platform-frontend Core (Config, Types, Utils, Stores)

## Goal
Create the entire `platform-frontend` project scaffold including all configuration files, Zod type schemas, utility functions, and Zustand stores. The UI components come in Prompt 15.

## `platform-frontend/` root

### `package.json`
```json
{
  "name": "platform-frontend",
  "version": "0.0.1",
  "private": true,
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "test": "jest --passWithNoTests",
    "test:bdd": "TS_NODE_PROJECT=tsconfig.cucumber.json cucumber-js --config cucumber.cjs",
    "test:coverage": "TS_NODE_PROJECT=tsconfig.cucumber.json c8 --reporter=lcov --reporter=text --include='src/**/*.ts' cucumber-js --config cucumber.cjs",
    "test:e2e": "playwright test",
    "test:e2e:ui": "playwright test --ui"
  },
  "dependencies": {
    "@tanstack/react-query": "^5.56.0",
    "ag-grid-community": "^32.3.0",
    "ag-grid-react": "^32.3.0",
    "axios": "^1.7.7",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-hook-form": "^7.53.0",
    "react-hot-toast": "^2.6.0",
    "zod": "^3.23.8",
    "zustand": "^4.5.5"
  },
  "devDependencies": {
    "@cucumber/cucumber": "^11.0.1",
    "@playwright/test": "^1.59.1",
    "@testing-library/jest-dom": "^6.5.0",
    "@testing-library/react": "^16.0.1",
    "@testing-library/user-event": "^14.5.2",
    "@types/jest": "^29.5.13",
    "@types/node": "^22.7.5",
    "@types/react": "^18.3.11",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.2",
    "c8": "^11.0.0",
    "jest": "^29.7.0",
    "jest-environment-jsdom": "^29.7.0",
    "ts-jest": "^29.2.5",
    "ts-node": "^10.9.2",
    "typescript": "^5.6.3",
    "vite": "^5.4.8"
  }
}
```

### `tsconfig.json`
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

### `tsconfig.node.json`
```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts"]
}
```

### `tsconfig.cucumber.json`
```json
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "module": "CommonJS",
    "moduleResolution": "node",
    "noEmit": false,
    "allowImportingTsExtensions": false,
    "outDir": "./dist-cucumber"
  },
  "include": ["src", "step-definitions", "features"]
}
```

### `vite.config.ts`
```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://localhost:8080' } },
});
```

### `jest.config.ts`
```ts
export default {
  preset: 'ts-jest',
  testEnvironment: 'jsdom',
  setupFilesAfterFramework: ['<rootDir>/src/setupTests.ts'],
  moduleNameMapper: { '\\.(css|less|scss)$': '<rootDir>/__mocks__/styleMock.js' },
};
```

### `cucumber.cjs`
```js
module.exports = {
  require: ['step-definitions/**/*.ts'],
  requireModule: ['ts-node/register'],
  paths: ['features/**/*.feature'],
  format: ['progress-bar'],
};
```

### `playwright.config.ts`
```ts
import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  use: { baseURL: 'http://localhost:5173', trace: 'on-first-retry' },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
```

### `src/setupTests.ts`
```ts
import '@testing-library/jest-dom';
```

### `src/index.css`
Minimal global styles: CSS reset (`*, *::before, *::after { box-sizing: border-box }`, `body { margin: 0; font-family: system-ui, sans-serif }`). AG Grid theme import: `@import "ag-grid-community/styles/ag-grid.css"; @import "ag-grid-community/styles/ag-theme-balham.css";`

---

## `src/api/client.ts`
```ts
import axios from 'axios';
import { useAuthStore } from '../store/authStore';

export const apiClient = axios.create({ baseURL: '/api/v1' });

apiClient.interceptors.request.use(config => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

## `src/store/authStore.ts`
Zustand store:
```ts
interface AuthState {
  token: string | null;
  userId: string | null;
  role: string | null;
  tenantId: string | null;
  setAuth: (token: string, userId: string, role: string, tenantId: string) => void;
  logout: () => void;
}
```
Persist to `localStorage` using Zustand's `persist` middleware.

## `src/store/wizardStore.ts`
Full Zustand store exactly as shown in the context. Key types:
- `SourceType = 'KAFKA' | 'DB_POLL' | 'FILE_SHARE' | 'MANUAL_UPLOAD'`
- `FieldMappingRow { fieldPath, displayName, type: 'DATE'|'DECIMAL'|'BOOLEAN'|'STRING', required }`
- `BlotterColumnDraft { fieldPath, headerName, formatter?, width?, sortable? }`
- `DetailSectionDraft { sectionName, layout?: 'TWO_COLUMN'|'ONE_COLUMN'|'TABLE', collapsible?, fields[] }`

`AUTO_WORKFLOW_CONFIG` constant:
```ts
{ initialState: 'UNDER_REVIEW', states: [{name:'UNDER_REVIEW',terminal:false},{name:'CLOSED',terminal:true}], transitions: [{name:'close',fromState:'UNDER_REVIEW',toState:'CLOSED',trigger:'MANUAL'}] }
```

Functions:
- `buildDraftConfigsPayload()` — maps store state into 6-key object (`workflowTypeDefinition`, `fieldTypeRegistry`, `ingestionSourceConfig`, `workflowConfig`, `blotterConfig`, `detailViewConfig`)
- `hydrateForResume(submission)` — parse `submission.draftConfigs`, set state + `submissionId`, advance `currentStep` by +1
- `hydrateForRevision(submission)` — same but set `revisingSubmissionId` and `currentStep=1`

---

## `src/types/`

### `WorkItem.ts`
Zod schema + inferred type:
```ts
export const WorkItemSchema = z.object({
  id: z.string(), tenantId: z.string(), workflowType: z.string(),
  correlationId: z.string(), configVersionId: z.string().nullable(),
  source: z.enum(['KAFKA', 'DB_POLL', 'FILE_UPLOAD']),
  sourceRef: z.string(), idempotencyKey: z.string(),
  status: z.string(), assignedGroup: z.string(), routedByDefault: z.boolean(),
  fields: z.record(z.unknown()),
  priorityScore: z.number().nullable(), priorityLevel: z.string().nullable(),
  priorityLastCalculatedAt: z.string().nullable(),
  pendingCheckerId: z.string().nullable(), pendingCheckerTransition: z.string().nullable(),
  version: z.number(), makerUserId: z.string(),
  createdAt: z.string(), updatedAt: z.string(),
});
export type WorkItem = z.infer<typeof WorkItemSchema>;
```

### `WorkflowTypeSubmission.ts`
```ts
export const WorkflowTypeSubmissionSchema = z.object({
  id: z.string(), tenantId: z.string(), workflowType: z.string(),
  displayName: z.string(), description: z.string().nullable(),
  statusCode: z.string(), statusDisplayName: z.string(),
  submittedBy: z.string(),
  submittedAt: z.string().nullable(), reviewedBy: z.string().nullable(),
  reviewedAt: z.string().nullable(), rejectionReason: z.string().nullable(),
  draftConfigs: z.record(z.unknown()),
  currentStep: z.number(), version: z.number(),
  createdAt: z.string(), updatedAt: z.string(),
});
export type WorkflowTypeSubmission = z.infer<typeof WorkflowTypeSubmissionSchema>;

export const SourceConnectionSchema = z.object({
  id: z.string(), name: z.string(), displayName: z.string(),
  connectionType: z.enum(['KAFKA', 'DB_POLL', 'FILE_SHARE']),
  config: z.record(z.unknown()), credentialsRef: z.string().nullable(),
  createdAt: z.string(),
});
export type SourceConnection = z.infer<typeof SourceConnectionSchema>;
```

### `AuditEntry.ts`
```ts
export const ChangedFieldSchema = z.object({ fieldPath: z.string(), previousValue: z.unknown(), newValue: z.unknown() });
export const AuditEntrySchema = z.object({
  id: z.string(), tenantId: z.string(), workItemId: z.string().nullable(),
  correlationId: z.string().nullable(), eventType: z.string(),
  previousState: z.string().nullable(), newState: z.string().nullable(),
  transitionName: z.string().nullable(), changedFields: z.array(ChangedFieldSchema),
  actorUserId: z.string().nullable(), actorRole: z.string().nullable(),
  timestamp: z.string(), idempotencyKey: z.string().nullable(),
});
export type AuditEntry = z.infer<typeof AuditEntrySchema>;
```

### `BlotterConfig.ts`
```ts
export const BlotterColumnSchema = z.object({
  field: z.string(), headerName: z.string(),
  formatter: z.string().optional(), width: z.number().optional(),
  sortable: z.boolean().optional(),
});
export const BlotterConfigSchema = z.object({ columns: z.array(BlotterColumnSchema) });
export type BlotterConfig = z.infer<typeof BlotterConfigSchema>;
export type BlotterColumn = z.infer<typeof BlotterColumnSchema>;
```

### `DetailViewConfig.ts`
```ts
export const DetailFieldSchema = z.object({ path: z.string(), label: z.string(), format: z.string().optional() });
export const DetailSectionSchema = z.object({ title: z.string(), fields: z.array(DetailFieldSchema), collapsible: z.boolean().optional() });
export const DetailViewConfigSchema = z.object({ sections: z.array(DetailSectionSchema) });
export type DetailViewConfig = z.infer<typeof DetailViewConfigSchema>;
```

---

## `src/utils/`

### `fieldPathResolver.ts`
```ts
export function resolveFieldPath(fields: Record<string, unknown>, dotPath: string): unknown {
  const parts = dotPath.split('.');
  let current: unknown = fields;
  for (const part of parts) {
    if (current == null || typeof current !== 'object') return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return current;
}
```

### `fieldMasking.ts`
```ts
export type FieldClassification = 'PUBLIC' | 'INTERNAL' | 'RESTRICTED' | 'CONFIDENTIAL';
export function maskValue(value: unknown, classification: FieldClassification, userRole: string): string {
  if (classification === 'CONFIDENTIAL' && userRole !== 'COMPLIANCE') return '***';
  if (classification === 'RESTRICTED' && !['ANALYST', 'MANAGER', 'COMPLIANCE'].includes(userRole)) return '***';
  if (value == null) return '—';
  return String(value);
}
```

### `formatValue.ts`
```ts
export function formatValue(value: unknown, formatter?: string): string {
  if (value == null) return '—';
  switch (formatter) {
    case 'CURRENCY': return typeof value === 'number' ? value.toLocaleString('en-US', { style: 'currency', currency: 'USD' }) : String(value);
    case 'DATE': return new Date(String(value)).toLocaleDateString();
    case 'DATETIME': return new Date(String(value)).toLocaleString();
    case 'PERCENTAGE': return `${value}%`;
    case 'BADGE': return String(value);  // rendered as coloured badge in UI
    default: {
      if (typeof value === 'object') return JSON.stringify(value);
      return String(value);
    }
  }
}
```

### `actionVisibility.ts`
```ts
export interface WorkflowTransitionDef { name: string; allowedRoles: string[]; fromState: string; }
export function getAvailableActions(currentStatus: string, userRole: string, transitions: WorkflowTransitionDef[]): WorkflowTransitionDef[] {
  return transitions.filter(t => t.fromState === currentStatus && t.allowedRoles.includes(userRole));
}
```

### `fieldExtractor.ts`
Utilities for CSV upload and field detection:
```ts
export function extractCsvHeaders(csvText: string): string[]  // split first line on comma
export function extractFieldPaths(fields: Record<string, unknown>, prefix = ''): string[]  // recursive dot-path extraction
export type InferredType = 'DATE' | 'DECIMAL' | 'BOOLEAN' | 'STRING';
export function inferType(value: string): InferredType  // heuristic: ISO date → DATE, numeric → DECIMAL, true/false → BOOLEAN, else STRING
```

### `wizardValidation.ts`
Exactly as shown in context:
```ts
const WORKFLOW_TYPE_PATTERN = /^[A-Z][A-Z0-9_]*$/;
export function isStepComplete(step: number, state: StepValidationState): boolean {
  // step 1: workflowType pattern + displayName non-empty
  // step 2: sourceType non-null AND (sourceType=MANUAL_UPLOAD OR sourceConnectionId non-null)
  // step 3: always true (sample upload optional)
  // step 4: fieldMappings.length > 0 AND idempotencyKeyField non-null
  // step 5: blotterColumns.length > 0
  // step 6: detailSections.length > 0
  // step 7: always true (review)
}
```

---

## `src/config/blotterConfigs.ts`
Static config for SETTLEMENT_EXCEPTION blotter — 11 columns demonstrating nested field paths:
```ts
export const BLOTTER_CONFIGS: Record<string, BlotterConfig> = {
  SETTLEMENT_EXCEPTION: {
    columns: [
      { field: 'id', headerName: 'ID', width: 120 },
      { field: 'status', headerName: 'Status', formatter: 'BADGE', width: 120 },
      { field: 'assignedGroup', headerName: 'Group', width: 130 },
      { field: 'fields.trade.tradeId', headerName: 'Trade ID', width: 120 },
      { field: 'fields.counterparty.name', headerName: 'Counterparty', width: 150 },
      { field: 'fields.trade.notionalAmount.amount', headerName: 'Notional', formatter: 'CURRENCY', width: 130 },
      { field: 'fields.trade.notionalAmount.currency', headerName: 'CCY', width: 70 },
      { field: 'fields.settlement.settlementDate', headerName: 'Settle Date', formatter: 'DATE', width: 120 },
      { field: 'fields.settlement.failReason', headerName: 'Fail Reason', width: 200 },
      { field: 'fields.counterparty.region', headerName: 'Region', width: 90 },
      { field: 'priorityScore', headerName: 'Priority', width: 90, sortable: true },
    ],
  },
};
```

---

## `src/api/` hooks (all 11 files)

All hooks use `@tanstack/react-query` v5 and `apiClient` from `client.ts`. Follow naming: `useXxx` for queries, `useXxxMutation` or named mutation hooks for mutations.

| File | Hook | Purpose |
|---|---|---|
| `useWorkItems.ts` | `useWorkItems(workflowType)` | `GET /work-items?workflowType=` |
| `useWorkItem.ts` | `useWorkItem(id)` | `GET /work-items/{id}` |
| `useTransition.ts` | `useTransition()` | `POST /work-items/{id}/transitions` mutation |
| `useAuditTrail.ts` | `useAuditTrail(workItemId)` | `GET /audit/work-items/{id}` |
| `useDetailViewConfig.ts` | `useDetailViewConfig(workflowType)` | `GET /configs/{workflowType}/DETAIL_VIEW_CONFIG` |
| `useWorkflowTypeSubmissions.ts` | `useMyDrafts()`, `useMyRejected()`, `usePendingSubmissions()`, `useAllDrafts()`, `useSubmissionById(id)` | various GET endpoints |
| `useCreateSubmission.ts` | `useCreateSubmission()` | `POST /workflow-type-submissions` mutation |
| `useSubmissionActions.ts` | `useSaveDraft()`, `useSubmitForApproval()`, `useApproveSubmission()`, `useRejectSubmission()`, `useReviseSubmission()`, `useDiscardSubmission()` | mutations for lifecycle |
| `useSourceConnections.ts` | `useSourceConnections(type?)` | `GET /source-connections?type=` |
| `useSourceConnectionAdmin.ts` | `useAllSourceConnections()`, `useCreateSourceConnection()`, `useUpdateSourceConnection()`, `useGrantAccess()`, `useRevokeAccess()` | admin CRUD |
| `useDevLogin.ts` | `useDevLogin()` | `GET /dev/token?userId=&role=&tenantId=` then set authStore |
| `client.ts` | axios instance | base URL `/api/v1`, JWT interceptor |

Each hook exports a named function. Query hooks use `queryKey` arrays including all parameters. Mutations use `useMutation` with `onSuccess` cache invalidation.

---

## Verification
```bash
cd platform-frontend
npm install
npm run build    # TypeScript check + Vite build
npm run test     # Jest unit tests
```

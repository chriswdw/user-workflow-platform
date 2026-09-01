import { Before, Given, Then, When, type DataTable } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import type { QueryClient } from '@tanstack/react-query';
import { within, waitFor, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DetailPanel } from '../src/components/detail/DetailPanel';
import { useAuthStore } from '../src/store/authStore';
import type { WorkItem } from '../src/types/WorkItem';
import type { DetailViewConfig, ActionInputField } from '../src/types/DetailViewConfig';
import type { AuditEntry } from '../src/types/AuditEntry';
import { createTestQueryClient, renderWithProviders } from './support/render';
import { stubClientMethod } from './support/mockClient';

const TENANT_ID = 'tenant-1';
const WORK_ITEM_ID = 'wi-1';

let queryClient: QueryClient;
let workItem: WorkItem;
let config: DetailViewConfig;
let auditEntries: AuditEntry[];
let rendered: RenderResult;
let closed: boolean;
let transitionCalls: Array<{ transition: string; additionalFields: Record<string, unknown> }>;

Before({ tags: '@detail-panel' }, () => {
  useAuthStore.getState().setAuth('tok', 'user-1', 'ANALYST', TENANT_ID);
  queryClient = createTestQueryClient();
  auditEntries = [];
  closed = false;
  transitionCalls = [];
  workItem = {
    id: WORK_ITEM_ID,
    tenantId: TENANT_ID,
    workflowType: 'SETTLEMENT_EXCEPTION',
    correlationId: 'corr-1',
    configVersionId: null,
    source: 'KAFKA',
    sourceRef: 'topic-ref',
    idempotencyKey: 'idem-1',
    status: 'UNDER_REVIEW',
    assignedGroup: 'group-ops',
    routedByDefault: false,
    fields: { trade: { ref: 'TRD-001' } },
    priorityScore: 50,
    priorityLevel: 'HIGH',
    priorityLastCalculatedAt: null,
    pendingCheckerId: null,
    pendingCheckerTransition: null,
    version: 1,
    makerUserId: 'system',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
  config = {
    id: 'dvc-1',
    tenantId: TENANT_ID,
    workflowType: 'SETTLEMENT_EXCEPTION',
    sections: [{
      title: 'Trade Details',
      layout: 'ONE_COLUMN',
      fields: [{ field: 'trade.ref', label: 'Trade Ref', formatter: 'TEXT' }],
    }],
    actions: [],
    active: true,
    version: 1,
  };
});

Given('a detail section field {string} visible only to role {string}', (field: string, role: string) => {
  config.sections[0].fields.push({
    field, label: field, formatter: 'TEXT', visibleRoles: [role],
  });
});

Given('the current viewer has role {string}', (role: string) => {
  useAuthStore.getState().setAuth('tok', 'user-1', role, TENANT_ID);
});

Given('a detail section field {string} editable while status is {string}', (field: string, status: string) => {
  config.sections[0].fields.push({
    field, label: field, formatter: 'TEXT', editable: true, editableInStates: [status],
  });
});

Given('an audit entry with event type {string} for the work item', (eventType: string) => {
  auditEntries.push({
    id: `audit-${auditEntries.length + 1}`,
    tenantId: TENANT_ID,
    workItemId: WORK_ITEM_ID,
    correlationId: 'corr-1',
    eventType: eventType as AuditEntry['eventType'],
    previousState: 'UNDER_REVIEW',
    newState: 'CLOSED',
    transitionName: 'close',
    changedFields: [],
    actorUserId: 'user-1',
    actorRole: 'ANALYST',
    timestamp: '2026-01-01T00:00:00Z',
    idempotencyKey: null,
  });
});

Given('an audit entry with event type {string} for the work item with these changed fields:',
  (eventType: string, table: DataTable) => {
    auditEntries.push({
      id: `audit-${auditEntries.length + 1}`,
      tenantId: TENANT_ID,
      workItemId: WORK_ITEM_ID,
      correlationId: 'corr-1',
      eventType: eventType as AuditEntry['eventType'],
      previousState: 'UNDER_REVIEW',
      newState: 'CLOSED',
      transitionName: 'close',
      changedFields: table.hashes().map(row => ({
        fieldPath: row.fieldPath,
        previousValue: row.previousValue === '' ? null : row.previousValue,
        newValue: row.newValue === '' ? null : row.newValue,
      })),
      actorUserId: 'user-1',
      actorRole: 'ANALYST',
      timestamp: '2026-01-01T00:00:00Z',
      idempotencyKey: null,
    });
  });

const OBJECT_CHANGED_FIELD_VALUE = { legalEntity: 'ACME' };

Given('an audit entry with event type {string} for the work item with an object-valued changed field {string}',
  (eventType: string, fieldPath: string) => {
    auditEntries.push({
      id: `audit-${auditEntries.length + 1}`,
      tenantId: TENANT_ID,
      workItemId: WORK_ITEM_ID,
      correlationId: 'corr-1',
      eventType: eventType as AuditEntry['eventType'],
      previousState: 'UNDER_REVIEW',
      newState: 'CLOSED',
      transitionName: 'close',
      changedFields: [{ fieldPath, previousValue: null, newValue: OBJECT_CHANGED_FIELD_VALUE }],
      actorUserId: 'user-1',
      actorRole: 'ANALYST',
      timestamp: '2026-01-01T00:00:00Z',
      idempotencyKey: null,
    });
  });

When('I render the detail panel for that work item', () => {
  queryClient.setQueryData(['work-item', TENANT_ID, WORK_ITEM_ID], workItem);
  queryClient.setQueryData(['detail-view-config', TENANT_ID, workItem.workflowType], config);
  queryClient.setQueryData(['audit-trail', TENANT_ID, WORK_ITEM_ID], auditEntries);

  rendered = renderWithProviders(
    <DetailPanel workItemId={WORK_ITEM_ID} onClose={() => { closed = true; }} />,
    queryClient,
  );
});

When('I click the Audit Trail tab', async () => {
  const tab = within(rendered.container).getByRole('tab', { name: 'Audit Trail' });
  await userEvent.click(tab);
});

When('I click the close button', async () => {
  const closeButton = within(rendered.container).getByRole('button', { name: 'Close' });
  await userEvent.click(closeButton);
});

Then('the detail panel shows the work item id and status', () => {
  const container = within(rendered.container);
  assert.ok(container.getByText(WORK_ITEM_ID));
  assert.ok(container.getByText('UNDER REVIEW'));
});

Then('the details tab panel is visible and the audit tab panel is hidden', () => {
  const detailsPanel = rendered.container.querySelector('#panel-details');
  const auditPanel = rendered.container.querySelector('#panel-audit');
  assert.ok(detailsPanel && !detailsPanel.hasAttribute('hidden'), 'details panel should be visible');
  assert.ok(auditPanel?.hasAttribute('hidden'), 'audit panel should be hidden');
});

Then('the audit tab panel is visible and the details tab panel is hidden', () => {
  const detailsPanel = rendered.container.querySelector('#panel-details');
  const auditPanel = rendered.container.querySelector('#panel-audit');
  assert.ok(auditPanel && !auditPanel.hasAttribute('hidden'), 'audit panel should be visible');
  assert.ok(detailsPanel?.hasAttribute('hidden'), 'details panel should be hidden');
});

Then('an audit entry with event type {string} is shown in the audit trail', (eventType: string) => {
  const text = within(rendered.container).getAllByText(eventType);
  assert.ok(text.length > 0, `expected an audit entry mentioning "${eventType}"`);
});

function getAuditEntryRow(): HTMLElement {
  const row = rendered.container.querySelector('.audit-table tbody tr');
  assert.ok(row, 'expected an audit entry row to be rendered');
  return row as HTMLElement;
}

When('I click the audit entry row', async () => {
  await userEvent.click(getAuditEntryRow());
});

Then('the changed-fields detail table for that audit entry is shown', () => {
  const table = rendered.container.querySelector('.audit-changed-fields');
  assert.ok(table, 'expected the changed-fields detail table to be shown');
});

Then('the changed-fields detail table for that audit entry is not shown', () => {
  const table = rendered.container.querySelector('.audit-changed-fields');
  assert.equal(table, null, 'expected the changed-fields detail table NOT to be shown');
});

Then('the changed field {string} shows previous value {string} and new value {string}',
  (fieldPath: string, previousValue: string, newValue: string) => {
    const table = rendered.container.querySelector('.audit-changed-fields');
    assert.ok(table, 'expected the changed-fields detail table to be shown');
    const row = within(table as HTMLElement).getByText(fieldPath).closest('tr');
    assert.ok(row, `expected a row for field "${fieldPath}"`);
    const cells = within(row as HTMLElement).getAllByRole('cell');
    assert.equal(cells[1].textContent, previousValue);
    assert.equal(cells[2].textContent, newValue);
  });

Then('the changed field {string} shows its new value as JSON', (fieldPath: string) => {
  const table = rendered.container.querySelector('.audit-changed-fields');
  assert.ok(table, 'expected the changed-fields detail table to be shown');
  const row = within(table as HTMLElement).getByText(fieldPath).closest('tr');
  assert.ok(row, `expected a row for field "${fieldPath}"`);
  const cells = within(row as HTMLElement).getAllByRole('cell');
  assert.equal(cells[2].textContent, JSON.stringify(OBJECT_CHANGED_FIELD_VALUE));
});

Then('the audit entry row shows no expand indicator', () => {
  const row = getAuditEntryRow();
  const cells = within(row).getAllByRole('cell');
  assert.equal(cells[cells.length - 1].textContent, '');
});

Then('the onClose callback fires', () => {
  assert.ok(closed, 'expected onClose to have been called');
});

Then('the field {string} is visible in the details panel', (field: string) => {
  const dt = within(rendered.container).queryByText(field);
  assert.ok(dt, `expected field label "${field}" to be visible`);
});

Then('the field {string} is not shown in the details panel', (field: string) => {
  const dt = within(rendered.container).queryByText(field);
  assert.equal(dt, null, `expected field label "${field}" NOT to be visible`);
});

Then('the field {string} renders as an editable input', (field: string) => {
  const input = within(rendered.container).getByLabelText(field) as HTMLInputElement;
  assert.equal(input.tagName, 'INPUT');
});

// ── Actions: ActionButton / ActionFormModal / ConfirmModal ──────────────────────────────────

Given('a direct action {string} for transition {string}', (label: string, transition: string) => {
  config.actions.push({ transition, label, style: 'PRIMARY', visibleInStates: [workItem.status] });
});

Given('a confirmation-required action {string} for transition {string} with message {string}',
  (label: string, transition: string, message: string) => {
    config.actions.push({
      transition, label, style: 'DANGER', visibleInStates: [workItem.status],
      confirmationRequired: true, confirmationMessage: message,
    });
  });

Given('an action {string} for transition {string} with these input fields:',
  (label: string, transition: string, table: DataTable) => {
    const rows = table.hashes();
    const inputFields: ActionInputField[] = rows.map(r => ({
      field: r['field'],
      label: r['label'],
      inputType: r['inputType'] as ActionInputField['inputType'],
      required: r['required'] === 'true',
      options: r['options'] ? r['options'].split(',') : undefined,
    }));
    config.actions.push({ transition, label, style: 'PRIMARY', visibleInStates: [workItem.status], inputFields });
  });

Given('the work item has a pending checker approval for transition {string}', (transition: string) => {
  workItem.pendingCheckerId = 'checker-1';
  workItem.pendingCheckerTransition = transition;
});

Given('the transition will succeed', () => {
  stubClientMethod('post', async (...args: unknown[]) => {
    const [url, body] = args as [string, { transition: string; additionalFields: Record<string, unknown> }];
    if (url === `/work-items/${WORK_ITEM_ID}/transitions`) {
      transitionCalls.push(body);
      return { data: { ...workItem, status: 'CLOSED' } };
    }
    throw new Error(`unexpected POST ${url}`);
  });
});

Given('the transition will fail with message {string}', (message: string) => {
  stubClientMethod('post', async (...args: unknown[]) => {
    const url = args[0] as string;
    if (url === `/work-items/${WORK_ITEM_ID}/transitions`) throw new Error(message);
    throw new Error(`unexpected POST ${url}`);
  });
});

When('I click the {string} action button', async (label: string) => {
  const button = within(rendered.container).getByRole('button', { name: label });
  await userEvent.click(button);
});

When('I click Confirm in the confirmation dialog', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Confirm' });
  await userEvent.click(button);
});

When('I click Cancel in the confirmation dialog', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Cancel' });
  await userEvent.click(button);
});

When('I click Submit in the action form', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Submit' });
  await userEvent.click(button);
});

When('I click Cancel in the action form', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Cancel' });
  await userEvent.click(button);
});

When('I type {string} into the {string} field', async (value: string, label: string) => {
  // ActionInputField's required fields render their label as "<label> *" (see ActionFormModal's
  // FieldInput) — { exact: false } matches on substring so the plain label text in Gherkin still
  // resolves.
  const input = within(rendered.container).getByLabelText(label, { exact: false });
  await userEvent.type(input, value);
});

Then('a confirmation dialog showing {string} is visible', (message: string) => {
  const dialog = within(rendered.container).getByRole('dialog', { hidden: true });
  assert.ok(within(dialog).getByText(message));
});

Then('the {string} field offers options {string}', (label: string, optionsCsv: string) => {
  const select = within(rendered.container).getByLabelText(label, { exact: false }) as HTMLSelectElement;
  const values = Array.from(select.options).map(o => o.value).filter(v => v !== '');
  assert.deepEqual(values, optionsCsv.split(','));
});

Then('the transition {string} was fired with no additional fields', async (transition: string) => {
  await waitFor(() => assert.equal(transitionCalls.length, 1, 'expected exactly one transition call'));
  assert.equal(transitionCalls[0]?.transition, transition);
  assert.deepEqual(transitionCalls[0]?.additionalFields, {});
});

Then('the transition {string} was fired with additional fields:', async (transition: string, table: DataTable) => {
  await waitFor(() => assert.equal(transitionCalls.length, 1, 'expected exactly one transition call'));
  assert.equal(transitionCalls[0]?.transition, transition);
  assert.deepEqual(transitionCalls[0]?.additionalFields, table.rowsHash());
});

Then('no transition was fired', async () => {
  // Give any pending mutation microtasks a turn to settle so a call firing late doesn't slip past.
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(transitionCalls.length, 0, 'expected no transition to have been fired');
});

Then('the action form shows a validation error for {string}', async (label: string) => {
  await waitFor(() => {
    const input = within(rendered.container).getByLabelText(label, { exact: false });
    const field = input.closest('.form-field');
    assert.ok(field, `expected "${label}" to be wrapped in a .form-field`);
    const error = field!.querySelector('.field-error');
    assert.ok(error?.textContent, `expected a validation error for "${label}"`);
  });
});

Then('the action form shows the server error {string}', (message: string) => {
  const error = within(rendered.container).getByText(message);
  assert.ok(error.className.includes('form-error') || error.closest('.modal-box'), `expected "${message}" to be shown as the form's server error`);
});

Then('the maker-checker banner shows {string}', (transition: string) => {
  const banner = within(rendered.container).getByRole('status');
  assert.ok(banner.textContent?.includes(transition), `expected the maker-checker banner to mention "${transition}"`);
});

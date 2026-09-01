import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, waitFor, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Blotter } from '../src/components/blotter/Blotter';
import type { BlotterConfig } from '../src/types/BlotterConfig';
import type { WorkItem } from '../src/types/WorkItem';
import { renderWithProviders } from './support/render';

let config: BlotterConfig;
let items: WorkItem[];
let userRole: string;
let selectedItemId: string | null;
let rendered: RenderResult;

Before({ tags: '@blotter' }, () => {
  userRole = 'ANALYST';
  items = [];
  selectedItemId = null;
});

function baseWorkItem(overrides: Partial<WorkItem> = {}): WorkItem {
  return {
    id: 'wi-1',
    tenantId: 'tenant-1',
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
    priorityLevel: 'MEDIUM',
    priorityLastCalculatedAt: null,
    pendingCheckerId: null,
    pendingCheckerTransition: null,
    version: 1,
    makerUserId: 'system',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function baseBlotterConfig(columns: BlotterConfig['columns']): BlotterConfig {
  return {
    id: 'blotter-1',
    tenantId: 'tenant-1',
    workflowType: 'SETTLEMENT_EXCEPTION',
    columns,
    defaultSort: { field: 'status', direction: 'ASC' },
    active: true,
    version: 1,
  };
}

Given('a blotter config with columns for {string} and {string}', (fieldA: string, fieldB: string) => {
  config = baseBlotterConfig([
    { field: fieldA, header: fieldA, visible: true },
    { field: fieldB, header: fieldB, visible: true },
  ]);
});

Given('a blotter column {string} masked for roles {string}', (field: string, rolesCsv: string) => {
  config = baseBlotterConfig([
    { field, header: field, visible: true, maskingRoles: rolesCsv.split(',').map(r => r.trim()) },
  ]);
});

Given('one work item with status {string}', (status: string) => {
  items = [baseWorkItem({ status })];
});

Given('one work item with field {string} set to {string}', (path: string, value: string) => {
  const parts = path.split('.');
  const fields: Record<string, unknown> = {};
  let cursor = fields;
  for (let i = 0; i < parts.length - 1; i++) {
    cursor[parts[i]] = {};
    cursor = cursor[parts[i]] as Record<string, unknown>;
  }
  cursor[parts[parts.length - 1]] = value;
  items = [baseWorkItem({ fields })];
});

Given('the current blotter viewer has role {string}', (role: string) => {
  userRole = role;
});

When('I render the blotter', () => {
  selectedItemId = null;
  rendered = renderWithProviders(
    <Blotter
      config={config}
      items={items}
      userRole={userRole ?? 'ANALYST'}
      onSelectItem={id => { selectedItemId = id; }}
    />,
  );
});

When('I click the first blotter row', async () => {
  const rows = await waitFor(() => {
    const found = within(rendered.container).getAllByRole('row');
    assert.ok(found.length > 1, 'blotter grid has not rendered any data rows yet');
    return found;
  });
  await userEvent.click(rows[1]); // row 0 is the header
});

Then('the blotter header shows {string} and {string}', async (headerA: string, headerB: string) => {
  const headerCells = await waitFor(() =>
    within(rendered.container).getAllByRole('columnheader').map(el => el.textContent?.trim()));
  assert.ok(headerCells.includes(headerA), `expected header "${headerA}" in ${headerCells}`);
  assert.ok(headerCells.includes(headerB), `expected header "${headerB}" in ${headerCells}`);
});

Then('the blotter shows a cell with text {string}', async (text: string) => {
  const cells = await waitFor(() =>
    within(rendered.container).getAllByRole('gridcell').map(el => el.textContent?.trim()));
  assert.ok(cells.includes(text), `expected a cell with "${text}" in ${JSON.stringify(cells)}`);
});

Then('the blotter shows a masked cell instead of {string}', async (unmaskedValue: string) => {
  const cells = await waitFor(() =>
    within(rendered.container).getAllByRole('gridcell').map(el => el.textContent?.trim()));
  assert.ok(!cells.includes(unmaskedValue), `expected "${unmaskedValue}" NOT to be visible in ${JSON.stringify(cells)}`);
  assert.ok(cells.some(c => c === '***'), `expected a masked "***" cell in ${JSON.stringify(cells)}`);
});

Then('onSelectItem fires with the clicked row\'s id', () => {
  assert.equal(selectedItemId, items[0].id);
});

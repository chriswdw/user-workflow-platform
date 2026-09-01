import { Before, Given, Then, When } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { within, type RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { QueryClient } from '@tanstack/react-query';
import { SourceConnectionsAdminView } from '../src/components/admin/SourceConnectionsAdminView';
import { useAuthStore } from '../src/store/authStore';
import type { SourceConnection } from '../src/types/WorkflowTypeSubmission';
import { createTestQueryClient, renderWithProviders } from './support/render';
import { stubClientMethod } from './support/mockClient';

const TENANT_ID = 'tenant-1';

let queryClient: QueryClient;
let connections: SourceConnection[];
let rendered: RenderResult;
let createCalls: Array<{ displayName: string; type: string; config: Record<string, unknown> }>;
let grantCalls: Array<{ tenantId: string }>;
let revokeCalls: Array<{ tenantId: string }>;

function makeConnection(overrides: Partial<SourceConnection> = {}): SourceConnection {
  return {
    id: 'conn-1',
    tenantId: TENANT_ID,
    displayName: 'Kafka Primary',
    type: 'KAFKA',
    config: { bootstrapServers: 'localhost:9092', topicName: 'work-items' },
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

Before({ tags: '@source-connections-admin' }, () => {
  useAuthStore.getState().setAuth('tok', 'admin-1', 'PLATFORM_ADMIN', TENANT_ID);
  queryClient = createTestQueryClient();
  connections = [];
  createCalls = [];
  grantCalls = [];
  revokeCalls = [];

  stubClientMethod('post', async (...args: unknown[]) => {
    const url = args[0] as string;
    if (url === '/source-connections') {
      createCalls.push(args[1] as { displayName: string; type: string; config: Record<string, unknown> });
    } else if (url.endsWith('/access')) {
      grantCalls.push(args[1] as { tenantId: string });
    }
    return { data: makeConnection() };
  });
  stubClientMethod('delete', async (...args: unknown[]) => {
    const url = args[0] as string;
    const tenantId = url.split('/').pop() ?? '';
    revokeCalls.push({ tenantId });
    return { data: undefined };
  });
  stubClientMethod('get', async () => ({ data: [] }));
});

Given('an admin-visible source connection {string} of type {string}', (displayName: string, type: string) => {
  connections.push(makeConnection({ id: `conn-${connections.length + 1}`, displayName, type: type as SourceConnection['type'] }));
});

When('I render the source connections admin view', () => {
  queryClient.setQueryData(['source-connections', TENANT_ID, undefined], connections);
  rendered = renderWithProviders(<SourceConnectionsAdminView />, queryClient);
});

When('I click Add Connection', async () => {
  const button = within(rendered.container).getByRole('button', { name: '+ Add Connection' });
  await userEvent.click(button);
});

When('I select connection type {string}', async (type: string) => {
  const select = within(rendered.container).getByRole('combobox');
  await userEvent.selectOptions(select, type);
});

When('I type {string} as the connection display name', async (name: string) => {
  const input = within(rendered.container).getByPlaceholderText('Display name');
  await userEvent.type(input, name);
});

When('I click Save on the add connection form', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Save' });
  await userEvent.click(button);
});

When('I click Manage Access', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Manage Access' });
  await userEvent.click(button);
});

When('I type {string} as the grant tenant id', async (tenantId: string) => {
  const input = within(rendered.container).getByPlaceholderText('Tenant ID');
  await userEvent.type(input, tenantId);
});

When('I click Grant Access', async () => {
  const button = within(rendered.container).getByRole('button', { name: 'Grant Access' });
  await userEvent.click(button);
});

Then('the connections table lists {string}', (displayName: string) => {
  assert.ok(within(rendered.container).getByText(displayName));
});

Then('the add connection form shows Kafka fields', () => {
  assert.ok(within(rendered.container).getByPlaceholderText('Bootstrap servers'));
});

Then('the add connection form shows DB poll fields', () => {
  assert.ok(within(rendered.container).getByPlaceholderText('JDBC URL'));
});

Then('the Save button is disabled', () => {
  const button = within(rendered.container).getByRole('button', { name: 'Save' }) as HTMLButtonElement;
  assert.ok(button.disabled);
});

Then('a create connection request is sent with displayName {string}', (displayName: string) => {
  assert.equal(createCalls.length, 1);
  assert.equal(createCalls[0].displayName, displayName);
});

Then('the access modal is shown for {string}', (displayName: string) => {
  const heading = within(rendered.container).getByText(new RegExp(`Manage Access.*${displayName}`));
  assert.ok(heading);
});

Then('a grant access request is sent for tenant {string}', (tenantId: string) => {
  assert.equal(grantCalls.length, 1);
  assert.equal(grantCalls[0].tenantId, tenantId);
});

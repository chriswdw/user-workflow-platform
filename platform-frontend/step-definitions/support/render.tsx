import type { ReactElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';

/**
 * A fresh QueryClient with no network retries and no background refetching. Components under
 * test that call React Query hooks read whatever's been seeded into this client via
 * `queryClient.setQueryData([...], value)` before rendering — there's no mocked HTTP layer here,
 * so a query with nothing seeded and `enabled: true` will attempt a real `client.get(...)` call
 * against a non-existent server. Seed every query key a scenario's component will read.
 *
 * `refetchOnMount`/`refetchOnWindowFocus`/`refetchOnReconnect` are all disabled so seeded data —
 * which React Query otherwise treats as immediately stale (default staleTime is 0) — doesn't
 * trigger a background refetch attempt the instant the component mounts.
 *
 * `gcTime: 0` matters specifically under cucumber-js (unlike Jest, which force-exits its worker
 * processes): React Query schedules cache garbage-collection for an unmounted query via a
 * non-`unref`'d `setTimeout`, defaulting to 5 minutes. Each scenario here creates its own
 * QueryClient, so without this the process has dozens of live 5-minute timers after the last
 * scenario finishes and never exits on its own. `gcTime: 0` collects an unmounted query on the
 * next tick instead — safe here since every client is scenario-scoped and torn down by RTL's
 * `cleanup()` in the `After` hook (step-definitions/support/dom-setup.ts) before it could ever be
 * reused across scenarios.
 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        refetchOnMount: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        gcTime: 0,
      },
      mutations: { retry: false, gcTime: 0 },
    },
  });
}

/**
 * Renders a component wrapped in a QueryClientProvider, matching the real provider tree in
 * src/App.tsx. Components under test that call React Query hooks (useSourceConnections,
 * useWorkItem, etc.) need this — a bare `render()` throws "No QueryClient set" otherwise.
 *
 * Pass a `queryClient` pre-seeded via `createTestQueryClient()` + `setQueryData(...)` when the
 * component under test reads query data; omit it for components with no data dependencies (a
 * fresh, empty client is created automatically).
 *
 * Query the result via `within(result.container)...`, not the `screen` singleton — `screen`
 * binds to `document` at module-import time, before dom-setup.ts installs the jsdom document, so
 * it never sees the real DOM under cucumber-js.
 */
export function renderWithProviders(ui: ReactElement, queryClient: QueryClient = createTestQueryClient()): RenderResult {
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  );
}

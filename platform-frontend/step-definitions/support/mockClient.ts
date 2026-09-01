import { After } from '@cucumber/cucumber';
import { client } from '../../src/api/client';

type ClientMethod = 'get' | 'post' | 'put' | 'delete' | 'patch';

const restoreFns: Array<() => void> = [];

/**
 * Stubs one HTTP method on the shared axios `client` instance for the current scenario only —
 * there's no mocking framework available under cucumber-js/ts-node (no `jest.mock`), so this
 * monkey-patches the real axios instance directly. Every stub is automatically restored by the
 * `After` hook below, regardless of which scenario used it, so stubs never leak across scenarios.
 *
 * Use this for any component interaction that triggers a real mutation/query call
 * (discard/approve/reject/create/etc.) — without it, that call would hit a real `client.<method>`
 * against a non-existent server.
 */
export function stubClientMethod(method: ClientMethod, impl: (...args: unknown[]) => Promise<unknown>): void {
  const original = client[method];
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (client as any)[method] = impl;
  restoreFns.push(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (client as any)[method] = original;
  });
}

After(function restoreClientStubs() {
  while (restoreFns.length > 0) {
    restoreFns.pop()?.();
  }
});

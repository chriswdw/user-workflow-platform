import { After, AfterAll } from '@cucumber/cucumber';
import { JSDOM } from 'jsdom';
// NOTE: '@testing-library/react' is required lazily inside the After hook below, not imported
// here at the top of the file. A top-level import would pull in react-dom, which runs its own
// DOM feature-detection (canUseDOM, isEventSupported('input'), etc.) as soon as it's imported —
// and imports resolve before this file's own body (below) has installed window/document. That
// causes react-dom to permanently believe it's running in IE9-without-native-input-events and
// fall back to an attachEvent/detachEvent polyfill path jsdom doesn't implement, throwing
// "activeElement.detachEvent is not a function" the first time a step definition types into an
// input. Requiring it lazily, after the DOM is installed, avoids the false detection entirely.

/**
 * cucumber-js runs under plain Node — unlike Jest, it has no built-in jsdom testEnvironment.
 * The BDD suite historically only exercised pure TS logic in src/store and src/utils (see
 * tsconfig.cucumber.json's `include`) because nothing here ever gave React Testing Library a
 * document to render into. This installs the same kind of global DOM that
 * jest-environment-jsdom provides, so step definitions can `render(<Component />)` exactly the
 * way the existing Jest component tests do (see src/__tests__/App.test.tsx).
 *
 * IMPORTANT: this runs as plain module-level code, not inside a BeforeAll hook. Libraries like
 * ag-grid feature-detect the DOM (`typeof window !== 'undefined'`, `instanceof HTMLElement`, …)
 * at MODULE IMPORT time, and those modules get imported while cucumber-js is still loading every
 * file in the `require` glob — which all happens before any BeforeAll hook fires. A BeforeAll
 * hook here would install the DOM too late: ag-grid would already have captured "no window"
 * during its own import and thrown when actually used. So this file is loaded via cucumber.cjs's
 * `requireModule` (which runs before the `require` glob'd step-definition/component files),
 * immediately after `ts-node/register`, ensuring the DOM exists before anything else is imported.
 *
 * One JSDOM instance is shared for the whole run — creating a fresh document per scenario would
 * be slower for no real benefit, since RTL's `cleanup()` after each scenario already unmounts
 * every rendered tree and the document itself carries no scenario-specific state once that
 * happens.
 */
const dom = new JSDOM('<!doctype html><html><body></body></html>', {
  url: 'http://localhost/',
  pretendToBeVisual: true, // enables requestAnimationFrame / getComputedStyle, needed by RTL & user-event
});

installGlobalDom(dom);

function installGlobalDom(jsdom: JSDOM): void {
  const domGlobal = globalThis as unknown as Record<string, unknown>;
  const window = jsdom.window as unknown as Record<string, unknown>;

  // Node 21+ already defines a few of these globals itself (e.g. `navigator`) as a getter-only
  // property, so a plain assignment throws — redefine them instead of assigning directly.
  const setGlobal = (target: Record<string, unknown>, name: string, value: unknown): void => {
    Object.defineProperty(target, name, {
      value,
      configurable: true,
      writable: true,
      enumerable: true,
    });
  };

  setGlobal(domGlobal, 'window', jsdom.window);
  setGlobal(domGlobal, 'document', jsdom.window.document);
  setGlobal(domGlobal, 'navigator', jsdom.window.navigator);

  // Copy every other window property (HTMLElement, Node, Event, getComputedStyle, ...) that
  // Node's global scope doesn't already define, mirroring what jest-environment-jsdom does.
  for (const prop of Object.getOwnPropertyNames(jsdom.window)) {
    if (!(prop in domGlobal)) {
      setGlobal(domGlobal, prop, window[prop]);
    }
  }

  installObserverPolyfills(domGlobal, window, setGlobal);
}

/**
 * jsdom does not implement ResizeObserver or IntersectionObserver (see
 * https://github.com/jsdom/jsdom/issues/3368) — ag-grid (the Blotter grid) depends on
 * ResizeObserver to size and virtualise its rows, and throws without it. These are minimal
 * no-op stand-ins, not real observers: they never fire callbacks (jsdom has no real layout engine
 * to observe size changes from), but simply having the constructors present is enough for ag-grid
 * to initialise instead of crashing.
 *
 * Installed on BOTH `globalThis` and `window` — code under test may feature-detect via either the
 * bare identifier or `window.ResizeObserver`, and those are two different objects here (the jsdom
 * `window` is not the same object as Node's `globalThis`, unlike in a real browser).
 */
function installObserverPolyfills(
  domGlobal: Record<string, unknown>,
  window: Record<string, unknown>,
  setGlobal: (target: Record<string, unknown>, name: string, value: unknown) => void,
): void {
  class NoopResizeObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  }

  class NoopIntersectionObserver {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): unknown[] {
      return [];
    }
  }

  for (const target of [domGlobal, window]) {
    if (!target.ResizeObserver) setGlobal(target, 'ResizeObserver', NoopResizeObserver);
    if (!target.IntersectionObserver) setGlobal(target, 'IntersectionObserver', NoopIntersectionObserver);
  }
}

After(function cleanupRenderedTrees() {
  // Unmount anything a step definition rendered this scenario, so the next scenario starts clean
  // — mirrors Jest's automatic per-file isolation, which cucumber-js doesn't give us for free.
  // Required lazily — see the note at the top of this file for why.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { cleanup } = require('@testing-library/react');
  cleanup();
});

AfterAll(function closeGlobalDom() {
  dom.window.close();
});

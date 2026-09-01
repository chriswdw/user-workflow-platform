/**
 * Components import CSS directly (e.g. `import 'ag-grid-community/styles/ag-grid.css'` in
 * Blotter.tsx) — Vite handles that at build time, and Jest maps it via
 * moduleNameMapper (see jest.config.ts). cucumber-js has neither: ts-node's CommonJS loader
 * tries to `require()` the .css file as JavaScript and fails to parse it.
 *
 * This registers a no-op loader for .css under Node's require() so importing a stylesheet is a
 * harmless no-op here, the same as it effectively is for a component test that doesn't render
 * visual layout. Runs at module-load time (not inside a hook) so it's in effect before any step
 * definition file — which may transitively import a component that imports CSS — gets required.
 */
// eslint-disable-next-line @typescript-eslint/no-require-imports
const Module = require('node:module');
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(Module as any)._extensions['.css'] = () => undefined;

const path = require('node:path');

module.exports = {
  default: {
    paths: ['features/**/*.feature'],
    require: [
      'step-definitions/support/render.tsx',
      'step-definitions/support/mockClient.ts',
      'step-definitions/**/*.steps.{ts,tsx}',
    ],
    // dom-setup and css-stub must run before ANY component/library file is imported (ag-grid and
    // other browser-oriented libraries feature-detect window/HTMLElement/etc. at their own
    // module-load time) — requireModule entries load before the `require` glob above, in order,
    // right after ts-node/register enables .ts/.tsx compilation for the rest. Cucumber resolves
    // these relative to its own installed location, not this project, so absolute paths are
    // required here.
    requireModule: [
      'ts-node/register',
      path.join(__dirname, 'step-definitions/support/css-stub.ts'),
      path.join(__dirname, 'step-definitions/support/dom-setup.ts'),
    ],
    format: ['progress-bar'],
    publishQuiet: true,
  },
};

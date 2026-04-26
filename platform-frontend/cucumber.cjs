module.exports = {
  default: {
    paths: ['features/**/*.feature'],
    require: ['step-definitions/**/*.steps.ts'],
    requireModule: ['ts-node/register'],
    format: ['progress-bar'],
    publishQuiet: true,
  },
};

module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
  },
  extends: ['eslint:recommended', 'plugin:@typescript-eslint/recommended'],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  plugins: ['@typescript-eslint', 'react-hooks', 'react-refresh'],
  ignorePatterns: ['dist/', 'node_modules/', 'playwright-report/', 'test-results/'],
  overrides: [
    {
      files: ['src/**/*.{ts,tsx}', 'test/**/*.{ts,tsx}'],
      rules: {
        ...require('eslint-plugin-react-hooks').configs.recommended.rules,
        'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      },
    },
    {
      files: ['scripts/**/*.mjs', 'playwright.config.ts', 'vite.config.ts'],
      env: { node: true, browser: false },
      rules: { 'no-shadow': 'error' },
    },
    {
      files: ['scripts/validate-browser-scenario.mjs'],
      env: { node: true, browser: true },
    },
  ],
};

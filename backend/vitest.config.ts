import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    fileParallelism: false,
    globalSetup: ['test/globalSetup.ts'],
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});

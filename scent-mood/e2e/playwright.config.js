import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.SCENT_MOOD_URL || 'http://127.0.0.1:15173',
    channel: process.env.SCENT_MOOD_BROWSER || undefined,
    trace: 'retain-on-failure',
  },
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [['github'], ['junit', { outputFile: 'test-results/junit.xml' }], ['html', { open: 'never' }]]
    : 'list',
})

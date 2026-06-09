import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './src/test/js',
  webServer: {
    command: 'npx http-server target/generated/wasm -a 127.0.0.1 -p 9321 -c-1',
    url: 'http://127.0.0.1:9321',
    reuseExistingServer: !process.env.CI,
    timeout: 120000
  },
  use: {
    baseURL: 'http://127.0.0.1:9321',
    ...devices['Desktop Chrome'],
    browserName: 'chromium'
  }
});

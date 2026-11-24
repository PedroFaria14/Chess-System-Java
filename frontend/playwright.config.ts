import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://127.0.0.1:4200', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', testIgnore: '**/*.desktop.spec.ts', use: { ...devices['Pixel 7'] } }
  ],
  webServer: [
    { command: 'java -jar ../backend/target/chess-api-1.0.0.jar --spring.datasource.url=jdbc:h2:mem:chess-e2e --server.address=127.0.0.1', url: 'http://127.0.0.1:8080/v3/api-docs', reuseExistingServer: !process.env['CI'], timeout: 120000 },
    { command: 'npm start', url: 'http://127.0.0.1:4200', reuseExistingServer: !process.env['CI'], timeout: 120000 }
  ]
});

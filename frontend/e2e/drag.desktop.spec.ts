import { test, expect } from '@playwright/test';

test('drag and drop uses server validation', async ({ page }) => {
  await page.goto('/play');
  await page.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(page.locator('.match-status')).toContainText('Vez de Jogador 1');
  await page.locator('[data-square="g1"]').dragTo(page.locator('[data-square="f3"]'));
  await expect(page.locator('.move-list')).toContainText('Nf3');
});

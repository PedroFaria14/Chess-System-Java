import { test, expect, Page } from '@playwright/test';

async function configure(page: Page, color: 'WHITE' | 'BLACK') {
  await page.goto('/play');
  await page.getByRole('button', { name: 'Contra o computador', exact: true }).click();
  await expect(page.locator('.engine-note')).toContainText('pronto para jogar');
  await page.getByLabel('Seu nome', { exact: true }).fill('Ana');
  await page.getByLabel('Sua cor').selectOption(color);
  await page.getByLabel('Dificuldade').selectOption('EASY');
}

test('human plays white and Stockfish automatically replies with a legal move', async ({ page }, info) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await configure(page, 'WHITE');
  await page.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  await page.locator('[data-square="e2"]').click();
  await expect(page.locator('[data-square="e4"]')).toHaveClass(/legal/);
  await page.locator('[data-square="e4"]').click();
  await expect(page.locator('.move-list button')).toHaveCount(2);
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  await expect(page.locator('.match-status')).toContainText('Fácil');
  await page.reload();
  await expect(page.locator('.move-list button')).toHaveCount(2);
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  expect(errors).toEqual([]);
  await page.evaluate(() => { (document.activeElement as HTMLElement)?.blur(); window.scrollTo(0, 0); });
  await page.screenshot({ path: `test-results/${info.project.name}-stockfish.png`, fullPage: true });
  if (process.env['UPDATE_SCREENSHOTS']) await page.screenshot({ path: `../docs/screenshots/${info.project.name}-stockfish.png`, fullPage: true });
});

test('choosing black flips the board and computer makes the opening move', async ({ page }) => {
  await configure(page, 'BLACK');
  await page.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(page.locator('.move-list button')).toHaveCount(1);
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  await expect(page.locator('.square').first()).toHaveAttribute('data-square', 'h1');
  await expect(page.getByRole('button', { name: '½ Empate', exact: true })).toHaveCount(0);
});

test('engine outage preserves position and allows explicit retry', async ({ page }) => {
  let attempts = 0;
  await page.route('**/computer-move', async route => {
    attempts++;
    if (attempts === 1) {
      await route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ status: 503, detail: 'O computador demorou para responder.' }) });
    } else await route.continue();
  });
  await configure(page, 'BLACK');
  await page.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('demorou');
  await expect(page.locator('.move-list button')).toHaveCount(0);
  await page.getByRole('button', { name: 'Tentar jogada do computador novamente' }).click();
  await expect(page.locator('.move-list button')).toHaveCount(1);
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  expect(attempts).toBe(2);
});

test('missing Stockfish explains setup while local mode remains usable', async ({ page }) => {
  await page.route('**/api/engine', route => route.fulfill({ json: { available: false, name: 'Stockfish', message: 'Stockfish não está instalado no backend.' } }));
  await page.goto('/play');
  await page.getByRole('button', { name: 'Contra o computador', exact: true }).click();
  await expect(page.locator('.engine-note')).toContainText('não está instalado');
  await expect(page.getByRole('button', { name: 'Começar partida', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Com um amigo', exact: true }).click();
  await page.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(page.locator('.match-status')).toContainText('PARTIDA LOCAL');
});

test('new game dialog switches from local play to computer play', async ({ page }) => {
  await page.goto('/play');
  await page.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(page.locator('.match-status')).toContainText('PARTIDA LOCAL');
  await page.getByRole('button', { name: 'Nova partida' }).click();
  const dialog = page.locator('dialog[open]');
  await dialog.getByRole('button', { name: 'Contra o computador', exact: true }).click();
  await dialog.getByLabel('Seu nome', { exact: true }).fill('Ana');
  await dialog.getByLabel('Sua cor').selectOption('BLACK');
  await dialog.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator('.move-list button')).toHaveCount(1);
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
});

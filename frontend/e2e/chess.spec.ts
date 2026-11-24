import { test, expect, Page } from '@playwright/test';

async function start(page: Page) {
  await page.goto('/play');
  await page.getByRole('textbox', { name: '○ Jogador das brancas', exact: true }).fill('Ana');
  await page.getByRole('textbox', { name: '● Jogador das pretas', exact: true }).fill('Pedro');
  await page.getByRole('button', { name: 'Começar partida', exact: true }).click();
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
}
async function move(page: Page, from: string, to: string) {
  await page.locator(`[data-square="${from}"]`).click();
  await expect(page.locator(`[data-square="${to}"]`)).toHaveClass(/legal/);
  await page.locator(`[data-square="${to}"]`).click();
  await expect(page.locator(`[data-square="${to}"]`)).toHaveClass(/last/);
}

test('play, review, resume, export, history and preferences', async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await start(page);
  await move(page, 'e2', 'e4');
  await move(page, 'e7', 'e5');
  await expect(page.locator('.move-list')).toContainText('e4');
  await page.getByRole('button', { name: 'Posição inicial', exact: true }).click();
  await expect(page.locator('[data-square="e2"]')).toHaveAttribute('aria-label', /Peão branco/);
  await expect(page.getByRole('button', { name: 'Voltar à posição atual' })).toBeVisible();
  await page.getByRole('button', { name: 'Voltar à posição atual' }).click();
  await page.reload();
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  await expect(page.locator('[data-square="e4"]')).toHaveAttribute('aria-label', /Peão branco/);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar PGN' }).click();
  expect((await download).suggestedFilename()).toMatch(/\.pgn$/);
  await page.evaluate(() => { (document.activeElement as HTMLElement)?.blur(); window.scrollTo(0, 0); });
  await page.screenshot({ path: `test-results/${testInfo.project.name}-game.png`, fullPage: true });
  if (process.env['UPDATE_SCREENSHOTS']) {
    await page.screenshot({ path: `../docs/screenshots/${testInfo.project.name}.png`, fullPage: true });
  }
  await page.getByRole('link', { name: /Minhas partidas/ }).click();
  await expect(page.locator('.history-card')).toContainText('Ana');
  await page.getByRole('button', { name: 'Continuar' }).click();
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  await page.getByRole('link', { name: 'Preferências', exact: true }).first().click();
  await page.getByRole('button', { name: 'Madeira' }).click();
  await page.getByLabel('Movimentos guiados').uncheck();
  await page.reload();
  await expect(page.getByLabel('Movimentos guiados')).not.toBeChecked();
  await expect(page.locator('html')).toHaveAttribute('data-board-theme', 'wood');
  expect(errors).toEqual([]);
  const sizes = await page.evaluate(() => ({ viewport: innerWidth, content: document.documentElement.scrollWidth }));
  expect(sizes.content).toBeLessThanOrEqual(sizes.viewport);
});

test('checkmate ends the game and resignation requires confirmation', async ({ page }) => {
  await start(page);
  await page.getByRole('button', { name: 'Desistir', exact: false }).click();
  await expect(page.locator('dialog[open]')).toContainText('Desistir desta partida?');
  await page.getByRole('button', { name: 'Continuar jogando' }).click();
  await move(page, 'f2', 'f3'); await move(page, 'e7', 'e5');
  await move(page, 'g2', 'g4'); await move(page, 'd8', 'h4');
  await expect(page.locator('.match-status')).toContainText('Pedro venceu · Xeque-mate');
  await expect(page.locator('.move-list')).toContainText('Qh4#');
  await page.reload();
  await expect(page.locator('.match-status')).toContainText('Xeque-mate');
});

test('promotion offers all four pieces and applies underpromotion', async ({ page }) => {
  await start(page);
  const id = await page.evaluate(() => localStorage.getItem('chess-last-game'));
  let version = 0;
  for (const [from, to] of [['a2','a4'],['h7','h5'],['a4','a5'],['h5','h4'],['a5','a6'],['h4','h3'],['a6','b7'],['h3','g2']]) {
    const response = await page.request.post(`/api/games/${id}/moves`, { data: { from, to, promotion: 'Q', version } });
    expect(response.ok()).toBeTruthy(); version++;
  }
  await page.reload();
  await expect(page.locator('.match-status')).toContainText('Vez de Ana');
  await page.locator('[data-square="b7"]').click();
  await expect(page.locator('[data-square="a8"]')).toHaveClass(/legal/);
  await page.locator('[data-square="a8"]').click();
  await expect(page.getByRole('heading', { name: 'Promova seu peão.' })).toBeVisible();
  await page.getByRole('button', { name: 'Cavalo', exact: true }).click();
  await expect(page.locator('[data-square="a8"]')).toHaveAttribute('aria-label', /Cavalo branco/);
  await expect(page.locator('.move-list')).toContainText('bxa8=N');
});

test('keyboard navigation moves a piece', async ({ page }) => {
  await start(page);
  await page.locator('[data-square="e2"]').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('[data-square="e4"]')).toHaveClass(/legal/);
  await page.keyboard.press('ArrowUp'); await page.keyboard.press('ArrowUp'); await page.keyboard.press('Enter');
  await expect(page.locator('[data-square="e4"]')).toHaveAttribute('aria-label', /Peão branco/);
});

test('server outage shows an actionable message', async ({ page }) => {
  await page.route('**/api/games*', route => route.abort());
  await page.goto('/play');
  await expect(page.getByRole('alert')).toContainText('Confira se o backend está em execução');
  await expect(page.getByRole('button', { name: 'Começar partida', exact: true })).toBeVisible();
});

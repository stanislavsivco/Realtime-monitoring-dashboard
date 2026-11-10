import { expect, test } from '@playwright/test';

import { ADMIN_STATE, VIEWER_STATE } from '../env';
import { Navbar } from './support/pages';

test.describe('admin navigation', () => {
  test.use({ storageState: ADMIN_STATE });

  test('T7: admin sees Dashboard, Alerts and Devices and the links open the right pages', async ({ page }) => {
    const navbar = new Navbar(page);
    await page.goto('/dashboard');

    for (const name of ['Dashboard', 'Alerts', 'Devices']) {
      await expect(navbar.link(name)).toBeVisible();
    }

    await navbar.link('Alerts').click();
    await expect(page).toHaveURL(/\/alerts$/);

    await navbar.link('Devices').click();
    await expect(page).toHaveURL(/\/devices$/);

    await navbar.link('Dashboard').click();
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});

test.describe('viewer navigation', () => {
  test.use({ storageState: VIEWER_STATE });

  test('T8: viewer has no Devices link and /devices and /admin redirect to /dashboard', async ({ page }) => {
    const navbar = new Navbar(page);
    await page.goto('/dashboard');

    await expect(navbar.link('Dashboard')).toBeVisible();
    await expect(navbar.link('Alerts')).toBeVisible();
    await expect(navbar.link('Devices')).toHaveCount(0);

    await page.goto('/devices');
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.goto('/admin');
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});
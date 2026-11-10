import { expect, Page, test } from '@playwright/test';

import {
  ADMIN_PASSWORD,
  ADMIN_USERNAME,
  EMPTY_STATE,
  VIEWER_PASSWORD,
  VIEWER_USERNAME,
} from '../env';
import { LoginPage, Navbar } from './support/pages';

test.use({ storageState: EMPTY_STATE });

async function signInAsAdmin(page: Page): Promise<void> {
  const loginPage = new LoginPage(page);
  await loginPage.goto();
  await loginPage.login(ADMIN_USERNAME, ADMIN_PASSWORD);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(new Navbar(page).user(ADMIN_USERNAME, 'ADMIN')).toBeVisible();
}

test('T4: admin signs in with the button and with Enter', async ({ page }) => {
  const loginPage = new LoginPage(page);
  const navbar = new Navbar(page);
  await signInAsAdmin(page);

  await navbar.signOut.click();
  await expect(page).toHaveURL(/\/login$/);

  await loginPage.fill(ADMIN_USERNAME, ADMIN_PASSWORD);
  await loginPage.password.press('Enter');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(navbar.user(ADMIN_USERNAME, 'ADMIN')).toBeVisible();
});

test('T6: sign out leaves no navbar and Back does not show a protected page', async ({ page }) => {
  const navbar = new Navbar(page);
  await signInAsAdmin(page);

  await navbar.signOut.click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(navbar.navigation).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('token'))).toBeNull();

  await page.goBack();
  await expect(page).toHaveURL(/\/login$/);
  await expect(navbar.navigation).toHaveCount(0);
  await expect(new LoginPage(page).signInButton).toBeVisible();
});

test('T10: after admin signs out and viewer signs in there is no Devices link', async ({ page }) => {
  const navbar = new Navbar(page);
  await signInAsAdmin(page);
  await expect(navbar.link('Devices')).toBeVisible();

  await navbar.signOut.click();
  await expect(page).toHaveURL(/\/login$/);

  await new LoginPage(page).login(VIEWER_USERNAME, VIEWER_PASSWORD);

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(navbar.user(VIEWER_USERNAME, 'VIEWER')).toBeVisible();
  await expect(navbar.link('Alerts')).toBeVisible();
  await expect(navbar.link('Devices')).toHaveCount(0);
});
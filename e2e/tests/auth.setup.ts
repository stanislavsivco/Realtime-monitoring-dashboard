import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

import { expect, test as setup } from '@playwright/test';

import {
  ADMIN_PASSWORD,
  ADMIN_STATE,
  ADMIN_USERNAME,
  API_URL,
  VIEWER_PASSWORD,
  VIEWER_STATE,
  VIEWER_USERNAME,
} from '../env';
import { deleteE2eDevices } from './support/device-api';
import { LoginPage, Navbar } from './support/pages';
import { readStoredToken } from './support/storage';

setup.describe.configure({ mode: 'serial' });

setup('sign in as admin and store the session', async ({ page }) => {
  const loginPage = new LoginPage(page);
  await loginPage.goto();
  await loginPage.login(ADMIN_USERNAME, ADMIN_PASSWORD);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(new Navbar(page).user(ADMIN_USERNAME, 'ADMIN')).toBeVisible();
  mkdirSync(dirname(ADMIN_STATE), { recursive: true });
  await page.context().storageState({ path: ADMIN_STATE });
});

setup('sign in as viewer and store the session', async ({ page }) => {
  const loginPage = new LoginPage(page);
  await loginPage.goto();
  await loginPage.login(VIEWER_USERNAME, VIEWER_PASSWORD);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(new Navbar(page).user(VIEWER_USERNAME, 'VIEWER')).toBeVisible();
  mkdirSync(dirname(VIEWER_STATE), { recursive: true });
  await page.context().storageState({ path: VIEWER_STATE });
});

setup('remove devices left behind by earlier e2e runs', async ({ playwright }) => {
  const api = await playwright.request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: { Authorization: `Bearer ${readStoredToken(ADMIN_STATE)}` },
  });
  try {
    await deleteE2eDevices(api);
  } finally {
    await api.dispose();
  }
});
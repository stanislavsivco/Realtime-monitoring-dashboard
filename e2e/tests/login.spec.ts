import { expect, test } from '@playwright/test';

import { ADMIN_STATE, ADMIN_USERNAME, EMPTY_STATE } from '../env';
import { LoginPage, Navbar } from './support/pages';

test.describe('without a session', () => {
  test.use({ storageState: EMPTY_STATE });

  for (const path of ['/', '/dashboard', '/alerts', '/devices']) {
    test(`T1: unauthenticated user is redirected to /login from ${path}`, async ({ page }) => {
      await page.goto(path);

      await expect(page).toHaveURL(/\/login$/);
      await expect(new LoginPage(page).signInButton).toBeVisible();
      await expect(new Navbar(page).navigation).toHaveCount(0);
    });
  }

  test('T2: empty fields show an error and send no request to the server', async ({ page }) => {
    const loginPage = new LoginPage(page);
    const loginRequests: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/api/auth/login')) {
        loginRequests.push(request.url());
      }
    });
    await loginPage.goto();

    const attempts = [
      { username: '', password: '' },
      { username: ADMIN_USERNAME, password: '' },
      { username: '', password: 'some-password' },
    ];
    for (const attempt of attempts) {
      await loginPage.fill(attempt.username, attempt.password);
      await loginPage.signInButton.click();

      await expect(loginPage.error).toHaveText('Enter username and password');
      await expect(page).toHaveURL(/\/login$/);
    }
    expect(loginRequests).toEqual([]);
  });

  test('T3: wrong password and unknown user show the same error', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();

    const attempts = [
      { username: ADMIN_USERNAME, password: 'definitely-not-the-password' },
      { username: `no-such-user-${Date.now()}`, password: 'whatever-password-123' },
    ];
    for (const attempt of attempts) {
      await loginPage.login(attempt.username, attempt.password);

      await expect(loginPage.error).toHaveText('Wrong username or password');
      await expect(page).toHaveURL(/\/login$/);
    }
  });

  test('T9: an invalid token signs the user out', async ({ page }) => {
    await new LoginPage(page).goto();
    await page.evaluate((username) => {
      localStorage.setItem('token', 'abc');
      localStorage.setItem('username', username);
      localStorage.setItem('role', 'ADMIN');
    }, ADMIN_USERNAME);

    await page.goto('/dashboard');

    await expect(page).toHaveURL(/\/login$/);
    await expect(new LoginPage(page).signInButton).toBeVisible();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('token'))).toBeNull();
  });
});

test.describe('with an admin session', () => {
  test.use({ storageState: ADMIN_STATE });

  test('T5: session survives a reload and /login redirects to /dashboard', async ({ page }) => {
    const navbar = new Navbar(page);
    await page.goto('/dashboard');
    await expect(navbar.user(ADMIN_USERNAME, 'ADMIN')).toBeVisible();

    await page.reload();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(navbar.user(ADMIN_USERNAME, 'ADMIN')).toBeVisible();

    await page.goto('/login');
    await expect(page).toHaveURL(/\/dashboard$/);
  });
});
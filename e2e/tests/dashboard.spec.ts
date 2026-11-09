import { ADMIN_STATE, BASE_URL, VIEWER_STATE } from '../env';
import { expect, test } from './support/fixtures';
import { DashboardPage, DevicesPage } from './support/pages';

test('T17: dashboard groups devices by type and shows the selected device', async ({ browser, devices }) => {
  const fixtures = [
    { category: 'Computers' as const, name: devices.uniqueName('computer'), type: 'Laptop', location: 'E2E Computers' },
    { category: 'Servers' as const, name: devices.uniqueName('server'), type: 'Server', location: 'E2E Servers' },
    { category: 'Routers' as const, name: devices.uniqueName('router'), type: 'Router', location: 'E2E Routers' },
  ];
  for (const fixture of fixtures) {
    await devices.create({ name: fixture.name, type: fixture.type, location: fixture.location });
  }

  const context = await browser.newContext({ baseURL: BASE_URL, storageState: ADMIN_STATE });
  try {
    const page = await context.newPage();
    const dashboard = new DashboardPage(page);
    await dashboard.open();

    for (const fixture of fixtures) {
      await dashboard.categoryButton(fixture.category).click();

      await expect(dashboard.deviceRow(fixture.name)).toBeVisible();
      for (const other of fixtures.filter((candidate) => candidate !== fixture)) {
        await expect(dashboard.deviceRow(other.name)).toHaveCount(0);
      }

      await dashboard.deviceRow(fixture.name).click();
      await expect(dashboard.detailName).toHaveText(fixture.name);
      await expect(dashboard.detailSub).toContainText(`${fixture.location} \u00B7 ${fixture.type}`);
    }
  } finally {
    await context.close();
  }
});

test('T18: dashboard of a second session follows add, edit and delete of a device through the WebSocket', async ({ browser, devices }) => {
  test.setTimeout(120_000);
  const liveTimeout = { timeout: 15_000 };
  const name = devices.uniqueName('live');
  const adminContext = await browser.newContext({ baseURL: BASE_URL, storageState: ADMIN_STATE });
  const viewerContext = await browser.newContext({ baseURL: BASE_URL, storageState: VIEWER_STATE });
  try {
    const adminPage = await adminContext.newPage();
    const viewerPage = await viewerContext.newPage();
    const devicesPage = new DevicesPage(adminPage);
    const dashboard = new DashboardPage(viewerPage);

    const initialList = viewerPage.waitForResponse((response) =>
      response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/devices');
    dashboard.trackWebSocket();
    await dashboard.open();
    await initialList;
    await dashboard.waitForWebSocket();
    await dashboard.blockDeviceListReloads();
    await dashboard.categoryButton('Servers').click();
    await expect(dashboard.deviceRow(name)).toHaveCount(0);

    await devicesPage.open();
    await devicesPage.addButton.click();
    await devicesPage.fillForm({ name, type: 'Server', location: 'E2E Live A' });
    expect(await devicesPage.save()).toBe(201);
    await expect(dashboard.deviceRow(name)).toBeVisible(liveTimeout);
    await expect(dashboard.deviceRow(name)).toContainText('E2E Live A');

    await devicesPage.editButton(name).click();
    await devicesPage.locationInput.fill('E2E Live B');
    expect(await devicesPage.save()).toBe(200);
    await expect(dashboard.deviceRow(name)).toContainText('E2E Live B', liveTimeout);

    await devicesPage.deleteButton(name).click();
    expect(await devicesPage.confirmDelete(name)).toBe(204);
    await expect(dashboard.deviceRow(name)).toHaveCount(0, liveTimeout);
  } finally {
    await adminContext.close();
    await viewerContext.close();
  }
});
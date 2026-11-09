import { ADMIN_STATE } from '../env';
import { expect, test } from './support/fixtures';
import { DevicesPage } from './support/pages';

test.use({ storageState: ADMIN_STATE });

test('T11: devices page has Add device and rows with Edit and Delete', async ({ page, devices }) => {
  const name = devices.uniqueName('list');
  await devices.create({ name, type: 'Server', location: 'E2E Lab' });
  const devicesPage = new DevicesPage(page);

  await devicesPage.open();

  await expect(devicesPage.addButton).toBeVisible();
  await expect(devicesPage.row(name)).toBeVisible();
  await expect(devicesPage.editButton(name)).toBeVisible();
  await expect(devicesPage.deleteButton(name)).toBeVisible();
});

test('T12: form opens and cancels, rejects empty fields and limits the name to 255 characters', async ({ page }) => {
  const devicesPage = new DevicesPage(page);
  const writes: string[] = [];
  page.on('request', (request) => {
    if (request.method() !== 'GET' && request.url().includes('/api/devices')) {
      writes.push(`${request.method()} ${request.url()}`);
    }
  });
  await devicesPage.open();

  await devicesPage.addButton.click();
  await expect(devicesPage.form).toBeVisible();
  await expect(devicesPage.formTitle).toHaveText('Add device');
  await expect(devicesPage.addButton).toHaveCount(0);

  await devicesPage.cancelButton.click();
  await expect(devicesPage.form).toHaveCount(0);
  await expect(devicesPage.addButton).toBeVisible();

  await devicesPage.addButton.click();
  await devicesPage.saveButton.click();
  await expect(devicesPage.form.getByText('Name is required')).toBeVisible();
  await expect(devicesPage.form.getByText('Type is required')).toBeVisible();
  await expect(devicesPage.form.getByText('Location is required')).toBeVisible();

  await devicesPage.nameInput.fill('x'.repeat(256));
  await expect(devicesPage.nameInput).toHaveValue('x'.repeat(255));

  expect(writes).toEqual([]);
});

test('T13: add a device through the form, then delete it with confirmation', async ({ page, devices }) => {
  const name = devices.uniqueName('lifecycle');
  const devicesPage = new DevicesPage(page);
  await devicesPage.open();

  await devicesPage.addButton.click();
  await devicesPage.fillForm({ name, type: 'Server', location: 'E2E Lab' });
  expect(await devicesPage.save()).toBe(201);

  await expect(devicesPage.form).toHaveCount(0);
  const row = devicesPage.row(name);
  await expect(row).toBeVisible();
  await expect(row).toContainText('Server');
  await expect(row).toContainText('E2E Lab');

  await devicesPage.deleteButton(name).click();
  await expect(row.getByText('Delete this device?')).toBeVisible();
  expect(await devicesPage.confirmDelete(name)).toBe(204);

  await expect(row).toHaveCount(0);
});

test('T14: duplicate name shows the server message and keeps the form filled', async ({ page, devices }) => {
  const name = devices.uniqueName('duplicate');
  await devices.create({ name, type: 'Server', location: 'E2E Lab' });
  const devicesPage = new DevicesPage(page);
  await devicesPage.open();

  await devicesPage.addButton.click();
  await devicesPage.fillForm({ name, type: 'Router', location: 'Other Lab' });
  expect(await devicesPage.save()).toBe(409);

  await expect(devicesPage.formError).toContainText('already exists');
  await expect(devicesPage.formError).toContainText(name);
  await expect(devicesPage.form).toBeVisible();
  await expect(devicesPage.nameInput).toHaveValue(name);
  await expect(devicesPage.typeSelect).toHaveValue('Router');
  await expect(devicesPage.locationInput).toHaveValue('Other Lab');
  await expect(devicesPage.row(name)).toHaveCount(1);
});

test('T15: edit form is prefilled and saving a new location updates the row', async ({ page, devices }) => {
  const name = devices.uniqueName('edit');
  await devices.create({ name, type: 'Server', location: 'E2E Lab A' });
  const devicesPage = new DevicesPage(page);
  await devicesPage.open();

  await devicesPage.editButton(name).click();
  await expect(devicesPage.formTitle).toHaveText('Edit device');
  await expect(devicesPage.nameInput).toHaveValue(name);
  await expect(devicesPage.typeSelect).toHaveValue('Server');
  await expect(devicesPage.locationInput).toHaveValue('E2E Lab A');

  await devicesPage.locationInput.fill('E2E Lab B');
  expect(await devicesPage.save()).toBe(200);

  await expect(devicesPage.form).toHaveCount(0);
  await expect(devicesPage.row(name)).toContainText('E2E Lab B');
  await expect(devicesPage.row(name)).not.toContainText('E2E Lab A');
});

test('T16: edit rejects a taken or empty name, Cancel keeps the row and Add device opens empty', async ({ page, devices }) => {
  const first = devices.uniqueName('conflict-a');
  const second = devices.uniqueName('conflict-b');
  await devices.create({ name: first, type: 'Server', location: 'E2E Lab A' });
  await devices.create({ name: second, type: 'Router', location: 'E2E Lab B' });
  const devicesPage = new DevicesPage(page);
  const updates: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'PUT' && request.url().includes('/api/devices/')) {
      updates.push(request.url());
    }
  });
  await devicesPage.open();

  await devicesPage.editButton(second).click();
  await devicesPage.nameInput.fill(first);
  expect(await devicesPage.save()).toBe(409);
  await expect(devicesPage.formError).toContainText('already exists');
  await expect(devicesPage.form).toBeVisible();
  await expect(devicesPage.nameInput).toHaveValue(first);

  await devicesPage.nameInput.fill('');
  await devicesPage.saveButton.click();
  await expect(devicesPage.form.getByText('Name is required')).toBeVisible();
  expect(updates).toHaveLength(1);

  await devicesPage.cancelButton.click();
  await expect(devicesPage.form).toHaveCount(0);
  await expect(devicesPage.row(second)).toContainText('E2E Lab B');

  await devicesPage.addButton.click();
  await expect(devicesPage.formTitle).toHaveText('Add device');
  await expect(devicesPage.nameInput).toHaveValue('');
  await expect(devicesPage.typeSelect).toHaveValue('');
  await expect(devicesPage.locationInput).toHaveValue('');
});
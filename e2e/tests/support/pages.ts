import { expect, Locator, Page } from '@playwright/test';

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function containing(value: string): RegExp {
  return new RegExp(escapeRegExp(value));
}

export class LoginPage {
  readonly username: Locator;
  readonly password: Locator;
  readonly signInButton: Locator;
  readonly error: Locator;

  constructor(private readonly page: Page) {
    this.username = page.getByLabel('Username');
    this.password = page.getByLabel('Password');
    this.signInButton = page.getByRole('button', { name: 'Sign in' });
    this.error = page.locator('.login-error');
  }

  async goto(): Promise<void> {
    await this.page.goto('/login');
    await expect(this.signInButton).toBeVisible();
  }

  async fill(username: string, password: string): Promise<void> {
    await this.username.fill(username);
    await this.password.fill(password);
  }

  async login(username: string, password: string): Promise<void> {
    await this.fill(username, password);
    await this.signInButton.click();
  }
}

export class Navbar {
  readonly navigation: Locator;
  readonly signOut: Locator;

  constructor(page: Page) {
    this.navigation = page.getByRole('navigation');
    this.signOut = this.navigation.getByRole('button', { name: 'Sign out' });
  }

  link(name: string): Locator {
    return this.navigation.getByRole('link', { name, exact: true });
  }

  user(username: string, role: string): Locator {
    return this.navigation.getByText(`${username} (${role})`, { exact: true });
  }
}

export interface DeviceFormValues {
  name: string;
  type: string;
  location: string;
}

export class DevicesPage {
  readonly heading: Locator;
  readonly addButton: Locator;
  readonly form: Locator;
  readonly formTitle: Locator;
  readonly nameInput: Locator;
  readonly typeSelect: Locator;
  readonly locationInput: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;
  readonly formError: Locator;

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'Devices', exact: true });
    this.addButton = page.getByRole('button', { name: 'Add device', exact: true });
    this.form = page.locator('form.device-form');
    this.formTitle = this.form.getByRole('heading');
    this.nameInput = this.form.getByLabel('Name', { exact: true });
    this.typeSelect = this.form.getByLabel('Type', { exact: true });
    this.locationInput = this.form.getByLabel('Location', { exact: true });
    this.saveButton = this.form.getByRole('button', { name: 'Save' });
    this.cancelButton = this.form.getByRole('button', { name: 'Cancel' });
    this.formError = this.form.locator('.form-error');
  }

  async open(): Promise<void> {
    await this.page.goto('/devices');
    await expect(this.heading).toBeVisible();
  }

  row(name: string): Locator {
    return this.page.locator('.device-row').filter({ has: this.page.getByText(name, { exact: true }) });
  }

  editButton(name: string): Locator {
    return this.row(name).getByRole('button', { name: 'Edit', exact: true });
  }

  deleteButton(name: string): Locator {
    return this.row(name).getByRole('button', { name: 'Delete', exact: true });
  }

  async fillForm(values: DeviceFormValues): Promise<void> {
    await this.nameInput.fill(values.name);
    await this.typeSelect.selectOption(values.type);
    await this.locationInput.fill(values.location);
  }

  async save(): Promise<number> {
    const [response] = await Promise.all([
      this.page.waitForResponse((candidate) => {
        const method = candidate.request().method();
        return (method === 'POST' || method === 'PUT') && /^\/api\/devices(\/\d+)?$/.test(new URL(candidate.url()).pathname);
      }),
      this.saveButton.click(),
    ]);
    return response.status();
  }

  async confirmDelete(name: string): Promise<number> {
    const [response] = await Promise.all([
      this.page.waitForResponse((candidate) =>
        candidate.request().method() === 'DELETE' && /^\/api\/devices\/\d+$/.test(new URL(candidate.url()).pathname)),
      this.row(name).getByRole('button', { name: 'Yes, delete' }).click(),
    ]);
    return response.status();
  }
}

export class DashboardPage {
  readonly detailName: Locator;
  readonly detailSub: Locator;
  private subscribed: Promise<void> | null = null;

  constructor(private readonly page: Page) {
    this.detailName = page.locator('.detail-name');
    this.detailSub = page.locator('.detail-sub');
  }

  trackWebSocket(): void {
    this.subscribed = new Promise<void>((resolve) => {
      this.page.on('websocket', (socket) => {
        socket.on('framesent', (frame) => {
          if (String(frame.payload).includes('/topic/devices/delete')) {
            resolve();
          }
        });
      });
    });
  }

  async waitForWebSocket(): Promise<void> {
    if (this.subscribed === null) {
      throw new Error('Call trackWebSocket() before opening the dashboard');
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error('The dashboard did not subscribe to the WebSocket topics within 15 seconds')), 15_000);
    });
    try {
      await Promise.race([this.subscribed, timeout]);
    } finally {
      clearTimeout(timer);
    }
  }

  async blockDeviceListReloads(): Promise<void> {
    await this.page.route('**/api/devices', (route) =>
      route.request().method() === 'GET' ? route.abort() : route.continue());
  }

  async open(): Promise<void> {
    await this.page.goto('/dashboard');
    await expect(this.categoryButton('Computers')).toBeVisible();
  }

  categoryButton(name: 'Computers' | 'Servers' | 'Routers'): Locator {
    return this.page.getByRole('button', { name, exact: true });
  }

  deviceRow(name: string): Locator {
    return this.page.getByRole('button', { name: containing(name) });
  }
}
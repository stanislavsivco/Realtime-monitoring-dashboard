import { APIRequestContext, expect } from '@playwright/test';

import { E2E_PREFIX } from '../../env';

export interface ApiDevice {
  id: number;
  name: string;
  type: string;
  location: string;
  status: string;
  simulated: boolean;
}

export interface NewDevice {
  name: string;
  type: string;
  location: string;
}

export async function deleteE2eDevices(api: APIRequestContext, only?: ReadonlySet<string>): Promise<void> {
  const response = await api.get('/api/devices');
  expect(response.ok(), 'GET /api/devices while cleaning up').toBeTruthy();
  const devices = (await response.json()) as ApiDevice[];
  for (const device of devices) {
    const isOwned = only === undefined ? device.name.startsWith(E2E_PREFIX) : only.has(device.name);
    if (isOwned && device.name.startsWith(E2E_PREFIX) && !device.simulated) {
      const deleted = await api.delete(`/api/devices/${device.id}`);
      expect(deleted.ok(), `DELETE /api/devices/${device.id} while cleaning up`).toBeTruthy();
    }
  }
}

export class DeviceApi {
  private readonly tracked = new Set<string>();

  constructor(private readonly api: APIRequestContext) {}

  uniqueName(label: string): string {
    const stamp = Date.now().toString(36);
    const random = Math.random().toString(36).slice(2, 7);
    const name = `${E2E_PREFIX}${stamp}-${random}-${label}`;
    this.tracked.add(name);
    return name;
  }

  async create(device: NewDevice): Promise<ApiDevice> {
    this.tracked.add(device.name);
    const response = await this.api.post('/api/devices', { data: device });
    expect(response.status(), `POST /api/devices for ${device.name}`).toBe(201);
    return (await response.json()) as ApiDevice;
  }

  async cleanup(): Promise<void> {
    await deleteE2eDevices(this.api, this.tracked);
  }
}
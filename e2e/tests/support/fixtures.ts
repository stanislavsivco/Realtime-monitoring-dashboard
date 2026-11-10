import { test as base, expect } from '@playwright/test';

import { ADMIN_STATE, API_URL } from '../../env';
import { DeviceApi } from './device-api';
import { readStoredToken } from './storage';

interface TestFixtures {
  devices: DeviceApi;
}

interface WorkerFixtures {
  adminToken: string;
}

export const test = base.extend<TestFixtures, WorkerFixtures>({
  adminToken: [
    async ({}, use) => {
      await use(readStoredToken(ADMIN_STATE));
    },
    { scope: 'worker' },
  ],

  devices: async ({ playwright, adminToken }, use) => {
    const api = await playwright.request.newContext({
      baseURL: API_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` },
    });
    const devices = new DeviceApi(api);
    try {
      await use(devices);
    } finally {
      try {
        await devices.cleanup();
      } finally {
        await api.dispose();
      }
    }
  },
});

export { expect };
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

const envFile = resolve(__dirname, '.env');
if (existsSync(envFile) && typeof process.loadEnvFile === 'function') {
  process.loadEnvFile(envFile);
}

function required(name: string): string {
  const value = process.env[name];
  if (value === undefined || value.trim() === '') {
    throw new Error(`Environment variable ${name} is not set. See e2e/README.md.`);
  }
  return value;
}

function optional(name: string, fallback: string): string {
  const value = process.env[name];
  if (value === undefined || value.trim() === '') {
    return fallback;
  }
  return value;
}

function withoutTrailingSlash(url: string): string {
  return url.replace(/\/+$/, '');
}

export const BASE_URL = withoutTrailingSlash(optional('RMD_BASE_URL', 'http://localhost:4200'));
export const API_URL = withoutTrailingSlash(optional('RMD_API_URL', 'http://localhost:8080'));

export const ADMIN_USERNAME = optional('RMD_ADMIN_USERNAME', 'admin');
export const ADMIN_PASSWORD = required('RMD_ADMIN_PASSWORD');
export const VIEWER_USERNAME = required('RMD_VIEWER_USERNAME');
export const VIEWER_PASSWORD = required('RMD_VIEWER_PASSWORD');

export const ADMIN_STATE = resolve(__dirname, '.auth', 'admin.json');
export const VIEWER_STATE = resolve(__dirname, '.auth', 'viewer.json');
export const EMPTY_STATE = { cookies: [], origins: [] };

export const E2E_PREFIX = 'e2e-';
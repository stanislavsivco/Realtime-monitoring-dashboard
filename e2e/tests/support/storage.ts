import { readFileSync } from 'node:fs';

interface StoredOrigin {
  origin: string;
  localStorage: { name: string; value: string }[];
}

interface StoredState {
  origins: StoredOrigin[];
}

export function readStoredToken(stateFile: string): string {
  const state = JSON.parse(readFileSync(stateFile, 'utf-8')) as StoredState;
  for (const origin of state.origins) {
    for (const item of origin.localStorage) {
      if (item.name === 'token') {
        return item.value;
      }
    }
  }
  throw new Error(`No token found in ${stateFile}. Run the setup project first.`);
}
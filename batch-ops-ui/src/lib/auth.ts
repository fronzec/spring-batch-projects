import { writable, get } from 'svelte/store';

export const auth = writable<{ username: string; basicHeader: string } | null>(null);

/** Store credentials in-memory as a Basic auth header. Not persisted to any storage. */
export function login(username: string, password: string): void {
  auth.set({
    username,
    basicHeader: 'Basic ' + btoa(`${username}:${password}`),
  });
}

/** Clear credentials from memory. */
export function logout(): void {
  auth.set(null);
}

/** Return Authorization header object, or empty object when not authenticated. */
export function authHeader(): Record<string, string> {
  const a = get(auth);
  return a ? { Authorization: a.basicHeader } : {};
}

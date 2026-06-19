import { describe, it, expect, vi, beforeEach } from 'vitest';

/*
 * App-boot smoke test.
 *
 * Imports the REAL entrypoint (main.ts) and asserts it mounts. This is the one
 * test that exercises the entrypoint's top-level `mount(App, …)` call.
 *
 * Why it exists: the Svelte 5 `new App()` boot bug (component_api_invalid_new)
 * passed svelte-check, vitest and vite build — none of which actually boot the
 * app — and only surfaced when running the dev server in a browser. This guards
 * that gap.
 *
 * The API layer is mocked so the boot is deterministic and never touches the
 * network (the default 'plugins' view fetches on mount).
 */

const okEmpty = () => Promise.resolve({ kind: 'data', value: [] });

vi.mock('./lib/api', () => ({
  getPlugins: vi.fn(okEmpty),
  getDefinitions: vi.fn(okEmpty),
  getDefinition: vi.fn(() => Promise.resolve({ kind: 'not-found' })),
  getRunningJobs: vi.fn(okEmpty),
}));

describe('app boot', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';
  });

  it('mounts the entrypoint without throwing and renders the shell', async () => {
    // Dynamic import so #app exists before main.ts runs its top-level mount().
    await import('./main');

    const app = document.getElementById('app');
    expect(app).not.toBeNull();
    expect(app!.querySelector('header')).not.toBeNull();
    expect(app!.textContent).toContain('Batch Ops UI');
  });
});

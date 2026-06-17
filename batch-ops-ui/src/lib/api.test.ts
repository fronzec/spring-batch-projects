import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { login, logout } from './auth';

// Mock fetch globally before importing api.ts
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

// Import api functions after stubbing fetch
import { getPlugins, getDefinitions, getDefinition, getRunningJobs } from './api';

function makeResponse(status: number, body: unknown = {}): Response {
  return {
    status,
    json: () => Promise.resolve(body),
    ok: status >= 200 && status < 300,
  } as unknown as Response;
}

beforeEach(() => {
  vi.clearAllMocks();
  logout();
});

afterEach(() => {
  logout();
});

describe('request() error normalization', () => {
  it('returns unreachable when fetch throws', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network error'));
    const result = await getPlugins();
    expect(result).toEqual({ kind: 'unreachable' });
  });

  it('returns auth-failed on 401', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(401));
    const result = await getDefinitions();
    expect(result).toEqual({ kind: 'auth-failed' });
  });

  it('returns auth-failed on 403', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(403));
    const result = await getRunningJobs();
    expect(result).toEqual({ kind: 'auth-failed' });
  });

  it('returns not-found on 404', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(404));
    const result = await getDefinition(999);
    expect(result).toEqual({ kind: 'not-found' });
  });

  it('returns server-error on 500 with the status code', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(500));
    const result = await getPlugins();
    expect(result).toEqual({ kind: 'server-error', status: 500 });
  });

  it('returns server-error on 503 with the correct status', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(503));
    const result = await getPlugins();
    expect(result).toEqual({ kind: 'server-error', status: 503 });
  });

  it('returns data with parsed body on 200', async () => {
    const plugins = [{ job_name: 'my-job', version: '1.0' }];
    mockFetch.mockResolvedValueOnce(makeResponse(200, plugins));
    const result = await getPlugins();
    expect(result).toEqual({ kind: 'data', value: plugins });
  });
});

describe('auth header behavior', () => {
  it('getPlugins() sends no Authorization header when auth store is null', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(200, []));
    await getPlugins();
    const [, options] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect((options.headers as Record<string, string>)['Authorization']).toBeUndefined();
  });

  it('getDefinitions() sends Authorization header when credentials are set', async () => {
    login('viewer', 'viewer123');
    mockFetch.mockResolvedValueOnce(makeResponse(200, []));
    await getDefinitions();
    const [, options] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect((options.headers as Record<string, string>)['Authorization']).toBe(
      'Basic ' + btoa('viewer:viewer123')
    );
  });
});

describe('endpoint paths', () => {
  it('getPlugins() calls /api/batch-service/jobs/plugins', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(200, []));
    await getPlugins();
    expect(mockFetch.mock.calls[0][0]).toBe('/api/batch-service/jobs/plugins');
  });

  it('getDefinitions() calls /api/batch-service/jobs/definitions', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(200, []));
    await getDefinitions();
    expect(mockFetch.mock.calls[0][0]).toBe('/api/batch-service/jobs/definitions');
  });

  it('getDefinition(id) calls /api/batch-service/jobs/definitions/{id}', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(200, {}));
    await getDefinition(42);
    expect(mockFetch.mock.calls[0][0]).toBe('/api/batch-service/jobs/definitions/42');
  });

  it('getRunningJobs() calls /api/batch-service/jobs/running-all', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(200, {}));
    await getRunningJobs();
    expect(mockFetch.mock.calls[0][0]).toBe('/api/batch-service/jobs/running-all');
  });

  it('all requests use GET method', async () => {
    mockFetch.mockResolvedValueOnce(makeResponse(200, []));
    await getPlugins();
    const [, options] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(options.method).toBe('GET');
  });
});

import type { PluginSummary, JobDefinition, RunningJobs } from './dto';
import { authHeader } from './auth';

const BASE = '/api/batch-service';

// Discriminated union covering every response scenario.
// NOTE: 'empty' is NOT returned by api.ts — it is derived by view components from
// zero-length data arrays or empty objects. api.ts returns { kind: 'data', value: [] }
// for empty lists; view components map that to { kind: 'empty' } before passing to
// AsyncState. The type includes 'empty' so AsyncState can render it.
export type ApiResult<T> =
  | { kind: 'data'; value: T }
  | { kind: 'empty' }                            // derived by views, never returned by api.ts
  | { kind: 'auth-failed' }                      // 401 or 403
  | { kind: 'not-found' }                        // 404
  | { kind: 'server-error'; status: number }     // 5xx
  | { kind: 'unreachable' };                     // fetch threw (network / proxy down)

async function request<T>(path: string): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(BASE + path, {
      method: 'GET',                              // hardcoded — no-mutation invariant
      headers: { Accept: 'application/json', ...authHeader() },
    });
  } catch {
    return { kind: 'unreachable' };
  }

  if (res.status === 401 || res.status === 403) return { kind: 'auth-failed' };
  if (res.status === 404) return { kind: 'not-found' };
  if (res.status >= 500) return { kind: 'server-error', status: res.status };

  const value = (await res.json()) as T;
  return { kind: 'data', value };
}

// Only read methods are exported — structural enforcement of the read-only invariant.
export const getPlugins = (): Promise<ApiResult<PluginSummary[]>> =>
  request<PluginSummary[]>('/jobs/plugins');

export const getDefinitions = (): Promise<ApiResult<JobDefinition[]>> =>
  request<JobDefinition[]>('/jobs/definitions');

export const getDefinition = (id: number): Promise<ApiResult<JobDefinition>> =>
  request<JobDefinition>(`/jobs/definitions/${id}`);

export const getRunningJobs = (): Promise<ApiResult<RunningJobs>> =>
  request<RunningJobs>('/jobs/running-all');

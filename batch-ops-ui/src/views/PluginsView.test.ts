import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/svelte';
import type { ApiResult } from '../lib/api';
import type { PluginSummary } from '../lib/dto';

// Use vi.hoisted to declare mocks before the vi.mock factory runs (hoisting)
const { mockGetPlugins } = vi.hoisted(() => ({
  mockGetPlugins: vi.fn<() => Promise<ApiResult<PluginSummary[]>>>(),
}));

vi.mock('../lib/api', () => ({
  getPlugins: mockGetPlugins,
  getDefinitions: vi.fn(),
  getDefinition: vi.fn(),
  getRunningJobs: vi.fn(),
}));

import PluginsView from './PluginsView.svelte';

const samplePlugins: PluginSummary[] = [
  {
    job_name: 'my-batch-job',
    version: '1.0.0',
    display_name: 'My Batch Job',
    description: 'Processes data',
    author: 'admin',
    tags: ['etl', 'batch'],
    estimated_runtime_seconds: 120,
    default_parameters: { input: 'file.csv' },
    source: 'CLASSPATH',
    status: 'ACTIVE',
  },
];

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PluginsView', () => {
  it('renders table rows with correct field values when API returns data', async () => {
    const result: ApiResult<PluginSummary[]> = { kind: 'data', value: samplePlugins };
    mockGetPlugins.mockResolvedValue(result);

    render(PluginsView);

    await waitFor(() => {
      expect(screen.getByText('my-batch-job')).toBeInTheDocument();
    });
    expect(screen.getByText('1.0.0')).toBeInTheDocument();
    expect(screen.getByText('My Batch Job')).toBeInTheDocument();
    expect(screen.getByText('CLASSPATH')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });

  it('renders loading indicator while fetch is pending', () => {
    // Promise that never resolves — simulates in-flight fetch
    mockGetPlugins.mockReturnValue(new Promise(() => {}));

    render(PluginsView);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('renders empty-state message when API returns empty array', async () => {
    const result: ApiResult<PluginSummary[]> = { kind: 'data', value: [] };
    mockGetPlugins.mockResolvedValue(result);

    render(PluginsView);

    await waitFor(() => {
      expect(screen.getByText(/no plugins registered/i)).toBeInTheDocument();
    });
  });

  it('renders error message when API returns unreachable', async () => {
    const result: ApiResult<PluginSummary[]> = { kind: 'unreachable' };
    mockGetPlugins.mockResolvedValue(result);

    render(PluginsView);

    await waitFor(() => {
      expect(screen.getByText(/backend unavailable/i)).toBeInTheDocument();
    });
  });

  it('renders error message when API returns server-error', async () => {
    const result: ApiResult<PluginSummary[]> = { kind: 'server-error', status: 500 };
    mockGetPlugins.mockResolvedValue(result);

    render(PluginsView);

    await waitFor(() => {
      expect(screen.getByText(/server error/i)).toBeInTheDocument();
    });
  });
});

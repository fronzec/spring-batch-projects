import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/svelte';
import type { ApiResult } from '../lib/api';
import type { RunningJobs } from '../lib/dto';

const { mockGetRunningJobs } = vi.hoisted(() => ({
  mockGetRunningJobs: vi.fn<() => Promise<ApiResult<RunningJobs>>>(),
}));

vi.mock('../lib/api', () => ({
  getPlugins: vi.fn(),
  getDefinitions: vi.fn(),
  getDefinition: vi.fn(),
  getRunningJobs: mockGetRunningJobs,
}));

import RunningView from './RunningView.svelte';

const sampleJobs: RunningJobs = {
  'my-batch-job': {
    '1001': 'input=file.csv',
    '1002': 'input=file2.csv',
  },
  'other-job': {
    '2001': 'param=value',
  },
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('RunningView', () => {
  it('renders grouped job sections with execution parameters', async () => {
    mockGetRunningJobs.mockResolvedValue({ kind: 'data', value: sampleJobs });

    render(RunningView);

    await waitFor(() => {
      expect(screen.getByText('my-batch-job')).toBeInTheDocument();
    });
    expect(screen.getByText('other-job')).toBeInTheDocument();
    expect(screen.getByText('1001')).toBeInTheDocument();
    expect(screen.getByText('input=file.csv')).toBeInTheDocument();
  });

  it('renders empty state when map is empty', async () => {
    mockGetRunningJobs.mockResolvedValue({ kind: 'data', value: {} });

    render(RunningView);

    await waitFor(() => {
      expect(screen.getByText(/no jobs currently running/i)).toBeInTheDocument();
    });
  });

  it('renders loading indicator while fetch is pending', () => {
    mockGetRunningJobs.mockReturnValue(new Promise(() => {}));

    render(RunningView);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('renders auth-failed message when API returns 401', async () => {
    mockGetRunningJobs.mockResolvedValue({ kind: 'auth-failed' });

    render(RunningView);

    await waitFor(() => {
      expect(screen.getByText(/authentication failed/i)).toBeInTheDocument();
    });
  });

  it('re-fetches data when refresh button is clicked', async () => {
    mockGetRunningJobs.mockResolvedValue({ kind: 'data', value: sampleJobs });

    render(RunningView);

    await waitFor(() => {
      expect(screen.getByText('my-batch-job')).toBeInTheDocument();
    });

    const refreshBtn = screen.getByRole('button', { name: /refresh/i });
    await fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(mockGetRunningJobs).toHaveBeenCalledTimes(2);
    });
  });
});

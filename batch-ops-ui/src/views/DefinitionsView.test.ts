import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/svelte';
import type { ApiResult } from '../lib/api';
import type { JobDefinition } from '../lib/dto';

const { mockGetDefinitions, mockGetDefinition } = vi.hoisted(() => ({
  mockGetDefinitions: vi.fn<() => Promise<ApiResult<JobDefinition[]>>>(),
  mockGetDefinition: vi.fn<(id: number) => Promise<ApiResult<JobDefinition>>>(),
}));

vi.mock('../lib/api', () => ({
  getPlugins: vi.fn(),
  getDefinitions: mockGetDefinitions,
  getDefinition: mockGetDefinition,
  getRunningJobs: vi.fn(),
}));

import DefinitionsView from './DefinitionsView.svelte';
import { selectedDefinitionId } from '../stores/view';

const sampleDefinitions: JobDefinition[] = [
  {
    id: 1,
    job_name: 'my-job',
    display_name: 'My Job',
    version: '1.0.0',
    enabled: true,
    load_status: 'LOADED',
    jar_file_name: 'my-job.jar',
    main_class_name: 'com.example.MyJob',
    created_at: '2024-01-01T00:00:00',
    approval_status: 'APPROVED',
    approved_by: 'admin',
    approved_at: '2024-01-01T01:00:00',
  },
];

beforeEach(() => {
  vi.clearAllMocks();
  // Reset view state between tests so detail panel doesn't bleed across
  selectedDefinitionId.set(null);
});

describe('DefinitionsView — list', () => {
  it('renders list rows with correct fields', async () => {
    mockGetDefinitions.mockResolvedValue({ kind: 'data', value: sampleDefinitions });

    render(DefinitionsView);

    await waitFor(() => {
      expect(screen.getByText('my-job')).toBeInTheDocument();
    });
    expect(screen.getByText('1')).toBeInTheDocument(); // id
    expect(screen.getByText('My Job')).toBeInTheDocument();
    expect(screen.getByText('1.0.0')).toBeInTheDocument();
  });

  it('renders empty-state when list is empty', async () => {
    mockGetDefinitions.mockResolvedValue({ kind: 'data', value: [] });

    render(DefinitionsView);

    await waitFor(() => {
      expect(screen.getByText(/no definitions found/i)).toBeInTheDocument();
    });
  });

  it('renders loading for list fetch', () => {
    mockGetDefinitions.mockReturnValue(new Promise(() => {}));

    render(DefinitionsView);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('renders auth-failed state on 401', async () => {
    mockGetDefinitions.mockResolvedValue({ kind: 'auth-failed' });

    render(DefinitionsView);

    await waitFor(() => {
      expect(screen.getByText(/authentication failed/i)).toBeInTheDocument();
    });
  });
});

describe('DefinitionsView — detail', () => {
  it('clicking a row triggers getDefinition(id) and renders detail panel', async () => {
    mockGetDefinitions.mockResolvedValue({ kind: 'data', value: sampleDefinitions });
    mockGetDefinition.mockResolvedValue({ kind: 'data', value: sampleDefinitions[0] });

    render(DefinitionsView);

    await waitFor(() => {
      expect(screen.getByText('my-job')).toBeInTheDocument();
    });

    // Click the row
    await fireEvent.click(screen.getByText('my-job'));

    await waitFor(() => {
      expect(mockGetDefinition).toHaveBeenCalledWith(1);
    });
    // Detail panel should show more fields
    await waitFor(() => {
      expect(screen.getByText('com.example.MyJob')).toBeInTheDocument();
    });
  });

  it('detail panel shows not-found state when API returns not-found', async () => {
    mockGetDefinitions.mockResolvedValue({ kind: 'data', value: sampleDefinitions });
    mockGetDefinition.mockResolvedValue({ kind: 'not-found' });

    render(DefinitionsView);

    await waitFor(() => {
      expect(screen.getByText('my-job')).toBeInTheDocument();
    });

    await fireEvent.click(screen.getByText('my-job'));

    await waitFor(() => {
      expect(screen.getByText(/not found/i)).toBeInTheDocument();
    });
  });
});

<script lang="ts">
  import { onMount } from 'svelte';
  import { getRunningJobs } from '../lib/api';
  import type { ApiResult } from '../lib/api';
  import type { RunningJobs } from '../lib/dto';
  import AsyncState from '../components/AsyncState.svelte';

  let loading = $state(true);
  let result = $state<ApiResult<RunningJobs> | null>(null);
  const viewResult = $derived(
    result?.kind === 'data' && Object.keys(result.value).length === 0
      ? ({ kind: 'empty' } as const)
      : result,
  );

  async function fetchJobs() {
    loading = true;
    result = await getRunningJobs();
    loading = false;
  }

  onMount(fetchJobs);
</script>

<section class="running-view">
  <div class="running-header">
    <h2>Running Jobs</h2>
    <button class="refresh-btn" onclick={fetchJobs} disabled={loading}>
      {loading ? 'Refreshing…' : 'Refresh'}
    </button>
  </div>

  <AsyncState result={viewResult} {loading}>
    {#snippet data(jobs)}
      {#each Object.entries(jobs) as [jobName, executions]}
        <div class="job-section">
          <h3 class="job-name">{jobName}</h3>
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Execution ID</th>
                  <th>Parameters</th>
                </tr>
              </thead>
              <tbody>
                {#each Object.entries(executions) as [execId, params]}
                  <tr>
                    <td>{execId}</td>
                    <td>{params}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        </div>
      {/each}
    {/snippet}
    {#snippet empty()}
      <p class="empty-state">No jobs currently running.</p>
    {/snippet}
  </AsyncState>
</section>

<style>
  .running-view {
    padding: var(--space-4);
  }

  .running-header {
    display: flex;
    align-items: center;
    gap: var(--space-4);
    margin-bottom: var(--space-4);
  }

  .refresh-btn {
    padding: var(--space-2) var(--space-3);
    border: 1px solid var(--color-brand);
    border-radius: var(--radius-md);
    background: var(--surface-base);
    color: var(--color-brand);
    cursor: pointer;
    font-size: var(--text-sm);
    transition: background 0.12s ease, color 0.12s ease;
  }

  .refresh-btn:hover:not(:disabled) {
    background: var(--color-brand);
    color: var(--text-on-brand);
  }

  .refresh-btn:disabled {
    opacity: 0.5;
    cursor: default;
  }

  .job-section {
    margin-bottom: var(--space-5);
  }

  .job-name {
    font-size: var(--text-md);
    font-weight: var(--weight-semibold);
    margin-bottom: var(--space-2);
    color: var(--text-secondary);
  }

  .table-wrapper {
    overflow-x: auto;
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-sm);
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: var(--text-sm);
  }

  th, td {
    text-align: left;
    padding: var(--space-2) var(--space-3);
    border-bottom: 1px solid var(--border-subtle);
  }

  tr:last-child td {
    border-bottom: none;
  }

  th {
    background: var(--surface-muted);
    color: var(--text-secondary);
    font-weight: var(--weight-semibold);
  }

  .empty-state {
    color: var(--text-muted);
    font-style: italic;
  }
</style>

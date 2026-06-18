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
    padding: 1rem;
  }

  .running-header {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1rem;
  }

  .refresh-btn {
    padding: 0.3rem 0.8rem;
    border: 1px solid #0066cc;
    border-radius: 4px;
    background: white;
    color: #0066cc;
    cursor: pointer;
    font-size: 0.875rem;
  }

  .refresh-btn:disabled {
    opacity: 0.5;
    cursor: default;
  }

  .job-section {
    margin-bottom: 1.5rem;
  }

  .job-name {
    font-size: 1rem;
    font-weight: 600;
    margin-bottom: 0.5rem;
    color: #333;
  }

  .table-wrapper {
    overflow-x: auto;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.875rem;
  }

  th, td {
    text-align: left;
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid #ddd;
  }

  th {
    background: #f8f9fa;
    font-weight: 600;
  }

  .empty-state {
    color: #666;
    font-style: italic;
  }
</style>

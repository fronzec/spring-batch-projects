<script lang="ts">
  import { onMount } from 'svelte';
  import { getDefinitions, getDefinition } from '../lib/api';
  import type { ApiResult } from '../lib/api';
  import type { JobDefinition } from '../lib/dto';
  import AsyncState from '../components/AsyncState.svelte';
  import { selectedDefinitionId } from '../stores/view';

  let listLoading = $state(true);
  let listResult = $state<ApiResult<JobDefinition[]> | null>(null);
  const listViewResult = $derived(
    listResult?.kind === 'data' && listResult.value.length === 0
      ? ({ kind: 'empty' } as const)
      : listResult,
  );

  let detailLoading = $state(false);
  let detailResult = $state<ApiResult<JobDefinition> | null>(null);

  onMount(async () => {
    listLoading = true;
    listResult = await getDefinitions();
    listLoading = false;
  });

  async function selectDefinition(id: number) {
    selectedDefinitionId.set(id);
    detailLoading = true;
    detailResult = null;
    detailResult = await getDefinition(id);
    detailLoading = false;
  }

  function clearSelection() {
    selectedDefinitionId.set(null);
    detailResult = null;
    detailLoading = false;
  }
</script>

<section class="definitions-view">
  {#if $selectedDefinitionId === null}
    <h2>Job Definitions</h2>
    <AsyncState result={listViewResult} loading={listLoading}>
      {#snippet data(definitions)}
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Job Name</th>
                <th>Display Name</th>
                <th>Version</th>
                <th>Enabled</th>
                <th>Load Status</th>
                <th>Approval Status</th>
              </tr>
            </thead>
            <tbody>
              {#each definitions as def (def.id)}
                <tr class="clickable-row" onclick={() => selectDefinition(def.id)}>
                  <td>{def.id}</td>
                  <td>{def.job_name}</td>
                  <td>{def.display_name ?? '—'}</td>
                  <td>{def.version}</td>
                  <td>{def.enabled === null ? '—' : def.enabled ? 'Yes' : 'No'}</td>
                  <td>{def.load_status ?? '—'}</td>
                  <td>{def.approval_status ?? '—'}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/snippet}
      {#snippet empty()}
        <p class="empty-state">No definitions found.</p>
      {/snippet}
    </AsyncState>
  {:else}
    <div class="detail-header">
      <h2>Definition Detail</h2>
      <button class="back-btn" onclick={clearSelection}>← Back to list</button>
    </div>
    <AsyncState result={detailResult} loading={detailLoading}>
      {#snippet data(def)}
        <div class="detail-panel">
          <dl>
            <dt>ID</dt><dd>{def.id}</dd>
            <dt>Job Name</dt><dd>{def.job_name}</dd>
            <dt>Display Name</dt><dd>{def.display_name ?? '—'}</dd>
            <dt>Version</dt><dd>{def.version}</dd>
            <dt>Enabled</dt><dd>{def.enabled === null ? '—' : def.enabled ? 'Yes' : 'No'}</dd>
            <dt>Load Status</dt><dd>{def.load_status ?? '—'}</dd>
            <dt>JAR File</dt><dd>{def.jar_file_name}</dd>
            <dt>Main Class</dt><dd>{def.main_class_name}</dd>
            <dt>Created At</dt><dd>{def.created_at ?? '—'}</dd>
            <dt>Approval Status</dt><dd>{def.approval_status ?? '—'}</dd>
            <dt>Approved By</dt><dd>{def.approved_by ?? '—'}</dd>
            <dt>Approved At</dt><dd>{def.approved_at ?? '—'}</dd>
          </dl>
        </div>
      {/snippet}
    </AsyncState>
  {/if}
</section>

<style>
  .definitions-view {
    padding: 1rem;
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

  .clickable-row {
    cursor: pointer;
  }

  .clickable-row:hover td {
    background: #f0f4ff;
  }

  .empty-state {
    color: #666;
    font-style: italic;
  }

  .detail-header {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1rem;
  }

  .back-btn {
    padding: 0.3rem 0.8rem;
    border: 1px solid #0066cc;
    border-radius: 4px;
    background: white;
    color: #0066cc;
    cursor: pointer;
    font-size: 0.875rem;
  }

  .detail-panel dl {
    display: grid;
    grid-template-columns: 200px 1fr;
    gap: 0.4rem 1rem;
    font-size: 0.875rem;
  }

  .detail-panel dt {
    font-weight: 600;
    color: #555;
  }
</style>

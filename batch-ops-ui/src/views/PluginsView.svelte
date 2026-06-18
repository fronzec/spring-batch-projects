<script lang="ts">
  import { onMount } from 'svelte';
  import { getPlugins } from '../lib/api';
  import type { ApiResult } from '../lib/api';
  import type { PluginSummary } from '../lib/dto';
  import AsyncState from '../components/AsyncState.svelte';

  let loading = $state(true);
  let result = $state<ApiResult<PluginSummary[]> | null>(null);
  // Derived view state — maps empty data array to 'empty' kind for AsyncState
  const viewResult = $derived(
    result?.kind === 'data' && result.value.length === 0
      ? ({ kind: 'empty' } as const)
      : result,
  );

  onMount(async () => {
    loading = true;
    result = await getPlugins();
    loading = false;
  });

  function joinTags(tags: string[] | null): string {
    return tags?.join(', ') ?? '—';
  }
</script>

<section class="plugins-view">
  <h2>Plugins</h2>
  <AsyncState result={viewResult} {loading}>
    {#snippet data(plugins)}
      <div class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>Job Name</th>
              <th>Version</th>
              <th>Display Name</th>
              <th>Description</th>
              <th>Tags</th>
              <th>Source</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {#each plugins as plugin (plugin.job_name + plugin.version)}
              <tr>
                <td>{plugin.job_name}</td>
                <td>{plugin.version}</td>
                <td>{plugin.display_name ?? '—'}</td>
                <td>{plugin.description ?? '—'}</td>
                <td>{joinTags(plugin.tags)}</td>
                <td>{plugin.source}</td>
                <td>{plugin.status}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/snippet}
    {#snippet empty()}
      <p class="empty-state">No plugins registered.</p>
    {/snippet}
  </AsyncState>
</section>

<style>
  .plugins-view {
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

  tr:hover td {
    background: #f0f4ff;
  }

  .empty-state {
    color: #666;
    font-style: italic;
  }
</style>

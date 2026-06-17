<script lang="ts">
  import type { ApiResult } from '../lib/api';

  // Props
  export let result: ApiResult<unknown> | null;
  export let loading: boolean;
</script>

{#if loading}
  <p class="state-loading" aria-live="polite">Loading…</p>
{:else if result === null}
  <!-- No fetch initiated yet — render nothing -->
{:else if result.kind === 'data'}
  <slot name="data" value={result.value} />
{:else if result.kind === 'empty'}
  <div data-slot="empty">
    <slot name="empty">
      <p class="state-empty">No results found.</p>
    </slot>
  </div>
{:else if result.kind === 'auth-failed'}
  <p class="state-error state-auth-failed">
    Authentication failed — check credentials.
  </p>
{:else if result.kind === 'unreachable'}
  <p class="state-error state-unreachable">Backend unavailable — is the server running?</p>
{:else if result.kind === 'server-error'}
  <p class="state-error state-server-error">
    Server error (HTTP {result.status}) — check backend logs.
  </p>
{:else if result.kind === 'not-found'}
  <p class="state-error state-not-found">Not found.</p>
{/if}

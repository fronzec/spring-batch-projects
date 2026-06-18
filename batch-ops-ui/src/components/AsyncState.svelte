<script lang="ts" generics="T">
  import type { ApiResult } from '../lib/api';
  import type { Snippet } from 'svelte';

  // Inline prop type: a named interface leaks as a "private name" in the
  // generated generic component signature, which svelte-check rejects.
  let {
    result,
    loading,
    data,
    empty,
  }: {
    result: ApiResult<T> | null;
    loading: boolean;
    /** Rendered for the 'data' kind, receiving the typed value. */
    data?: Snippet<[T]>;
    /** Rendered for the 'empty' kind; falls back to a default message. */
    empty?: Snippet;
  } = $props();
</script>

{#if loading}
  <p class="state-loading" aria-live="polite">Loading…</p>
{:else if result === null}
  <!-- No fetch initiated yet — render nothing -->
{:else if result.kind === 'data'}
  {@render data?.(result.value)}
{:else if result.kind === 'empty'}
  <div data-slot="empty">
    {#if empty}
      {@render empty()}
    {:else}
      <p class="state-empty">No results found.</p>
    {/if}
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

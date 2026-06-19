<script lang="ts">
  import { currentView, selectedDefinitionId } from '../stores/view';
  import type { ViewName } from '../stores/view';

  function navigate(view: ViewName) {
    currentView.set(view);
    if (view === 'definitions') {
      // Clear detail selection when returning to definitions tab
      selectedDefinitionId.set(null);
    }
  }
</script>

<nav class="tab-bar" aria-label="Main navigation">
  <button
    class:active={$currentView === 'plugins'}
    onclick={() => navigate('plugins')}
    aria-current={$currentView === 'plugins' ? 'page' : undefined}
  >
    Plugins
  </button>
  <button
    class:active={$currentView === 'definitions'}
    onclick={() => navigate('definitions')}
    aria-current={$currentView === 'definitions' ? 'page' : undefined}
  >
    Definitions
  </button>
  <button
    class:active={$currentView === 'running'}
    onclick={() => navigate('running')}
    aria-current={$currentView === 'running' ? 'page' : undefined}
  >
    Running Jobs
  </button>
</nav>

<style>
  .tab-bar {
    display: flex;
    gap: var(--space-2);
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--border-subtle);
    background: var(--surface-muted);
  }

  button {
    padding: var(--space-2) var(--space-3);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-md);
    background: var(--surface-base);
    color: var(--text-secondary);
    cursor: pointer;
    font-size: var(--text-sm);
    transition: background 0.12s ease, color 0.12s ease, border-color 0.12s ease;
  }

  button.active {
    background: var(--color-brand);
    color: var(--text-on-brand);
    border-color: var(--color-brand);
  }

  button:hover:not(.active) {
    background: var(--surface-accent);
    border-color: var(--border-strong);
  }
</style>

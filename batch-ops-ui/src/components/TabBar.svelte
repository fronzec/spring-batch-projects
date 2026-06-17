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
    on:click={() => navigate('plugins')}
    aria-current={$currentView === 'plugins' ? 'page' : undefined}
  >
    Plugins
  </button>
  <button
    class:active={$currentView === 'definitions'}
    on:click={() => navigate('definitions')}
    aria-current={$currentView === 'definitions' ? 'page' : undefined}
  >
    Definitions
  </button>
  <button
    class:active={$currentView === 'running'}
    on:click={() => navigate('running')}
    aria-current={$currentView === 'running' ? 'page' : undefined}
  >
    Running Jobs
  </button>
</nav>

<style>
  .tab-bar {
    display: flex;
    gap: 0.5rem;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid #ddd;
    background: #f8f9fa;
  }

  button {
    padding: 0.4rem 0.9rem;
    border: 1px solid #ccc;
    border-radius: 4px;
    background: white;
    cursor: pointer;
    font-size: 0.9rem;
  }

  button.active {
    background: #0066cc;
    color: white;
    border-color: #0066cc;
  }

  button:hover:not(.active) {
    background: #e9ecef;
  }
</style>

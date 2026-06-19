<script lang="ts">
  import TabBar from './components/TabBar.svelte';
  import LoginForm from './components/LoginForm.svelte';
  import PluginsView from './views/PluginsView.svelte';
  import DefinitionsView from './views/DefinitionsView.svelte';
  import RunningView from './views/RunningView.svelte';
  import { currentView } from './stores/view';
</script>

<div class="app">
  <header class="app-header">
    <div class="app-title">
      <h1>Batch Ops UI</h1>
      <span class="subtitle">fr-batch-service dashboard — read-only</span>
    </div>
  </header>

  <LoginForm />

  <TabBar />

  <main class="app-main">
    {#if $currentView === 'plugins'}
      <PluginsView />
    {:else if $currentView === 'definitions'}
      <DefinitionsView />
    {:else if $currentView === 'running'}
      <RunningView />
    {/if}
  </main>
</div>

<style>
  .app {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
  }

  .app-header {
    padding: var(--space-3) var(--space-4);
    background: var(--surface-inverse);
    color: var(--text-on-inverse);
    display: flex;
    align-items: center;
    gap: var(--space-4);
    box-shadow: var(--shadow-md);
  }

  .app-title {
    display: flex;
    align-items: baseline;
    gap: var(--space-4);
  }

  .app-title h1 {
    margin: 0;
    font-size: var(--text-xl);
    font-weight: var(--weight-bold);
  }

  .subtitle {
    font-size: var(--text-xs);
    opacity: 0.7;
  }

  .app-main {
    flex: 1;
    overflow: auto;
  }
</style>

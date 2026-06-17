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
  :global(*, *::before, *::after) {
    box-sizing: border-box;
  }

  :global(body) {
    margin: 0;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    font-size: 16px;
    color: #212529;
    background: #fff;
  }

  :global(h2) {
    margin: 0 0 1rem;
    font-size: 1.25rem;
  }

  :global(h3) {
    margin: 0 0 0.5rem;
  }

  .app {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
  }

  .app-header {
    padding: 0.75rem 1rem;
    background: #1a1a2e;
    color: white;
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .app-title {
    display: flex;
    align-items: baseline;
    gap: 1rem;
  }

  .app-title h1 {
    margin: 0;
    font-size: 1.2rem;
    font-weight: 700;
  }

  .subtitle {
    font-size: 0.8rem;
    opacity: 0.7;
  }

  .app-main {
    flex: 1;
    overflow: auto;
  }
</style>

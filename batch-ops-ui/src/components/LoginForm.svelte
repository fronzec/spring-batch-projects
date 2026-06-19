<script lang="ts">
  import { auth, login, logout } from '../lib/auth';

  let username = $state('');
  let password = $state('');

  function handleSubmit(event: SubmitEvent) {
    event.preventDefault();
    if (username && password) {
      login(username, password);
      password = ''; // Clear password field after login
    }
  }
</script>

<div class="login-form">
  {#if $auth}
    <div class="logged-in">
      <span class="username">Logged in as <strong>{$auth.username}</strong></span>
      <button class="logout-btn" onclick={logout}>Logout</button>
    </div>
  {:else}
    <form onsubmit={handleSubmit} class="credentials-form">
      <label>
        Username
        <input
          type="text"
          bind:value={username}
          autocomplete="username"
          required
          placeholder="viewer"
        />
      </label>
      <label>
        Password
        <input
          type="password"
          bind:value={password}
          autocomplete="current-password"
          required
        />
      </label>
      <button type="submit">Login</button>
    </form>
    <p class="caveat" role="note">
      Credentials are stored in-memory only and cleared on page reload.
      For internal/non-production use.
    </p>
  {/if}
</div>

<style>
  .login-form {
    padding: var(--space-2) var(--space-4);
    background: var(--banner-bg);
    border-bottom: 1px solid var(--banner-border);
    font-size: var(--text-sm);
    color: var(--banner-text);
  }

  .credentials-form {
    display: flex;
    align-items: flex-end;
    gap: var(--space-3);
    flex-wrap: wrap;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
    font-weight: var(--weight-medium);
  }

  input {
    padding: var(--space-1) var(--space-2);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-md);
    background: var(--surface-base);
    color: var(--text-primary);
    font-size: var(--text-sm);
  }

  button {
    padding: var(--space-2) var(--space-3);
    border-radius: var(--radius-md);
    border: 1px solid var(--color-brand);
    background: var(--color-brand);
    color: var(--text-on-brand);
    cursor: pointer;
    font-size: var(--text-sm);
    transition: background 0.12s ease;
  }

  button:hover {
    background: var(--color-brand-hover);
  }

  .caveat {
    margin: var(--space-2) 0 0;
    color: var(--banner-text);
    font-size: var(--text-xs);
  }

  .logged-in {
    display: flex;
    align-items: center;
    gap: var(--space-4);
  }

  .logout-btn {
    background: var(--surface-base);
    color: var(--color-danger);
    border-color: var(--color-danger);
  }

  .logout-btn:hover {
    background: var(--color-danger);
    color: var(--text-on-brand);
  }
</style>

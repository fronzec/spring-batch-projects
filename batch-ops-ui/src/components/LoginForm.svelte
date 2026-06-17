<script lang="ts">
  import { auth, login, logout } from '../lib/auth';

  let username = '';
  let password = '';

  function handleSubmit() {
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
      <button class="logout-btn" on:click={logout}>Logout</button>
    </div>
  {:else}
    <form on:submit|preventDefault={handleSubmit} class="credentials-form">
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
    padding: 0.5rem 1rem;
    background: #fff3cd;
    border-bottom: 1px solid #ffc107;
    font-size: 0.875rem;
  }

  .credentials-form {
    display: flex;
    align-items: flex-end;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  label {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-weight: 500;
  }

  input {
    padding: 0.3rem 0.5rem;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 0.875rem;
  }

  button {
    padding: 0.35rem 0.8rem;
    border-radius: 4px;
    border: 1px solid #0066cc;
    background: #0066cc;
    color: white;
    cursor: pointer;
    font-size: 0.875rem;
  }

  .caveat {
    margin: 0.4rem 0 0;
    color: #664d03;
    font-size: 0.8rem;
  }

  .logged-in {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .logout-btn {
    background: white;
    color: #cc0000;
    border-color: #cc0000;
  }
</style>

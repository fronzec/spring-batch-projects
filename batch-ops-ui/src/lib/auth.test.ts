import { describe, it, expect, beforeEach } from 'vitest';
import { get } from 'svelte/store';
import { auth, login, logout, authHeader } from './auth';

beforeEach(() => {
  // Reset store state between tests
  logout();
});

describe('login()', () => {
  it('sets the correct basicHeader for known user:pass', () => {
    login('viewer', 'viewer123');
    const state = get(auth);
    expect(state).not.toBeNull();
    // base64('viewer:viewer123') = 'dmlld2VyOnZpZXdlcjEyMw=='
    expect(state?.basicHeader).toBe('Basic ' + btoa('viewer:viewer123'));
  });

  it('stores the username', () => {
    login('testuser', 'testpass');
    const state = get(auth);
    expect(state?.username).toBe('testuser');
  });

  it('updates the header when called again with new credentials', () => {
    login('user1', 'pass1');
    login('user2', 'pass2');
    const state = get(auth);
    expect(state?.basicHeader).toBe('Basic ' + btoa('user2:pass2'));
  });
});

describe('logout()', () => {
  it('clears the store to null', () => {
    login('viewer', 'viewer123');
    logout();
    expect(get(auth)).toBeNull();
  });
});

describe('authHeader()', () => {
  it('returns empty object when not logged in', () => {
    expect(authHeader()).toEqual({});
  });

  it('returns Authorization header when logged in', () => {
    login('viewer', 'viewer123');
    expect(authHeader()).toEqual({
      Authorization: 'Basic ' + btoa('viewer:viewer123'),
    });
  });
});

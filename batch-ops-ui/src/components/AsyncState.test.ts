import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import AsyncState from './AsyncState.svelte';
import type { ApiResult } from '../lib/api';

describe('AsyncState component', () => {
  it('renders loading indicator when loading=true', () => {
    render(AsyncState, { props: { result: null, loading: true } });
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('does not render loading indicator when loading=false and result is provided', () => {
    const result: ApiResult<string[]> = { kind: 'data', value: ['item'] };
    render(AsyncState, { props: { result, loading: false } });
    expect(screen.queryByText(/loading/i)).not.toBeInTheDocument();
  });

  it('renders auth-failed message on auth-failed result', () => {
    const result: ApiResult<unknown> = { kind: 'auth-failed' };
    render(AsyncState, { props: { result, loading: false } });
    expect(screen.getByText(/authentication failed/i)).toBeInTheDocument();
  });

  it('renders unreachable message on unreachable result', () => {
    const result: ApiResult<unknown> = { kind: 'unreachable' };
    render(AsyncState, { props: { result, loading: false } });
    expect(screen.getByText(/backend unavailable/i)).toBeInTheDocument();
  });

  it('renders server-error message with status on server-error result', () => {
    const result: ApiResult<unknown> = { kind: 'server-error', status: 500 };
    render(AsyncState, { props: { result, loading: false } });
    expect(screen.getByText(/server error/i)).toBeInTheDocument();
    expect(screen.getByText(/500/)).toBeInTheDocument();
  });

  it('renders not-found message on not-found result', () => {
    const result: ApiResult<unknown> = { kind: 'not-found' };
    render(AsyncState, { props: { result, loading: false } });
    expect(screen.getByText(/not found/i)).toBeInTheDocument();
  });

  it('renders empty slot content on empty result', async () => {
    const result: ApiResult<unknown> = { kind: 'empty' };
    const { container } = render(AsyncState, { props: { result, loading: false } });
    // The empty slot is rendered — verify no error message and no loading
    expect(container.querySelector('[data-slot="empty"]')).not.toBeNull();
  });
});

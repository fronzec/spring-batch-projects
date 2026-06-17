import { writable } from 'svelte/store';

export type ViewName = 'plugins' | 'definitions' | 'running';

/** Currently active top-level view. */
export const currentView = writable<ViewName>('plugins');

/** Selected definition ID for the Definitions detail panel. Null means list view. */
export const selectedDefinitionId = writable<number | null>(null);

import { QueryClient } from '@tanstack/react-query';

/** Shared browser cache. Authentication lifecycle code clears it synchronously. */
export const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
});

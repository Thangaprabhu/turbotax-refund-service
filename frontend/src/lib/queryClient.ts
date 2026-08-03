import { QueryClient } from "@tanstack/react-query";

// Shared singleton so it can be cleared from outside React (e.g. the auth store) --
// without this, switching accounts in the same tab serves the previous user's cached
// query results until staleTime elapses, since query keys aren't scoped by user.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 1000 * 60 * 5 },
  },
});

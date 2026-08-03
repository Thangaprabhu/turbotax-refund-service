import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AccountType } from "@/types";
import { queryClient } from "@/lib/queryClient";

interface AuthState {
  token: string | null;
  accountType: AccountType | null;
  login: (token: string, accountType: AccountType) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      accountType: null,
      // Every auth transition clears the query cache -- query keys (e.g. ["taxpayers", page])
      // aren't scoped by user, so without this, switching accounts in the same tab would
      // serve the previous user's still-"fresh" cached results instead of refetching.
      login: (token, accountType) => {
        queryClient.clear();
        set({ token, accountType });
      },
      logout: () => {
        queryClient.clear();
        set({ token: null, accountType: null });
      },
    }),
    { name: "turbotax-auth" }
  )
);

import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AccountType } from "@/types";

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
      login: (token, accountType) => set({ token, accountType }),
      logout: () => set({ token: null, accountType: null }),
    }),
    { name: "turbotax-auth" }
  )
);

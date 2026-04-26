import { create } from 'zustand';

interface AuthState {
  token: string | null;
  userId: string | null;
  role: string | null;
  tenantId: string | null;
  setAuth: (token: string, userId: string, role: string, tenantId: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>(set => ({
  token: null,
  userId: null,
  role: null,
  tenantId: null,
  setAuth: (token, userId, role, tenantId) => set({ token, userId, role, tenantId }),
  clearAuth: () => set({ token: null, userId: null, role: null, tenantId: null }),
}));

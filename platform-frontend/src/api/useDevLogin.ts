import { useMutation } from '@tanstack/react-query';
import axios from 'axios';
import { useAuthStore } from '../store/authStore';

interface LoginParams {
  userId: string;
  role: string;
  tenantId: string;
}

export function useDevLogin() {
  const setAuth = useAuthStore(s => s.setAuth);

  return useMutation({
    mutationFn: async (params: LoginParams) => {
      const { data } = await axios.post('/api/dev/token', params);
      return data as { token: string };
    },
    onSuccess: ({ token }, params) => {
      setAuth(token, params.userId, params.role, params.tenantId);
    },
  });
}

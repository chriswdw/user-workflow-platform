import { useMutation } from '@tanstack/react-query';
import axios from 'axios';
import { z } from 'zod';
import { useAuthStore } from '../store/authStore';

interface LoginParams {
  readonly userId: string;
  readonly role: string;
  readonly tenantId: string;
}

const DevTokenResponseSchema = z.object({ token: z.string() });

export function useDevLogin() {
  const setAuth = useAuthStore(s => s.setAuth);

  return useMutation({
    mutationFn: async (params: LoginParams) => {
      const { data } = await axios.post('/api/dev/token', params);
      return DevTokenResponseSchema.parse(data);
    },
    onSuccess: ({ token }, params) => {
      setAuth(token, params.userId, params.role, params.tenantId);
    },
  });
}

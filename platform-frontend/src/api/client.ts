import axios from 'axios';
import { useAuthStore } from '../store/authStore';

export const client = axios.create({ baseURL: '/api/v1' });

client.interceptors.request.use(config => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

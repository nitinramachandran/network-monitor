import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { TokenResponse } from './types';

const BASE = import.meta.env.VITE_API_BASE ?? '/api/v1';
const ACCESS = 'nwm.access';
const REFRESH = 'nwm.refresh';

export const tokens = {
  get access() { return localStorage.getItem(ACCESS); },
  get refresh() { return localStorage.getItem(REFRESH); },
  set(t: Pick<TokenResponse, 'accessToken' | 'refreshToken'>) {
    localStorage.setItem(ACCESS, t.accessToken);
    localStorage.setItem(REFRESH, t.refreshToken);
  },
  clear() { localStorage.removeItem(ACCESS); localStorage.removeItem(REFRESH); },
};

export const http = axios.create({ baseURL: BASE });

let onSessionExpired: () => void = () => {};
export const setSessionExpiredHandler = (fn: () => void) => { onSessionExpired = fn; };

http.interceptors.request.use((cfg) => {
  if (tokens.access) cfg.headers.Authorization = `Bearer ${tokens.access}`;
  return cfg;
});

// Single-flight refresh: concurrent 401s share one refresh call.
let refreshing: Promise<string> | null = null;
async function refreshAccess(): Promise<string> {
  const refreshToken = tokens.refresh;
  if (!refreshToken) throw new Error('no refresh token');
  const { data } = await axios.post<TokenResponse>(`${BASE}/auth/refresh`, { refreshToken });
  tokens.set(data);
  return data.accessToken;
}

http.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const cfg = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthCall = cfg?.url?.includes('/auth/');
    if (error.response?.status === 401 && cfg && !cfg._retried && !isAuthCall && tokens.refresh) {
      cfg._retried = true;
      try {
        refreshing ??= refreshAccess().finally(() => { refreshing = null; });
        const access = await refreshing;
        cfg.headers.Authorization = `Bearer ${access}`;
        return http(cfg);
      } catch {
        tokens.clear();
        onSessionExpired();
      }
    }
    return Promise.reject(error);
  },
);

/** Extracts the server's problem+json detail for display. */
export function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const d = e.response?.data as { detail?: string; fields?: Record<string, string> } | undefined;
    if (d?.fields) return Object.entries(d.fields).map(([k, v]) => `${k}: ${v}`).join('; ');
    if (d?.detail) return d.detail;
    if (!e.response) return 'Cannot reach the monitor service';
  }
  return e instanceof Error ? e.message : 'Something went wrong';
}

import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api } from '../api/api';
import { setSessionExpiredHandler, tokens } from '../api/client';
import type { User } from '../api/types';

interface AuthState {
  user: User | null;
  loading: boolean;
  isRoot: boolean;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(!!tokens.access);

  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null));
    if (!tokens.access) return;
    api.me().then(setUser).catch(() => tokens.clear()).finally(() => setLoading(false));
  }, []);

  const signIn = useCallback(async (username: string, password: string) => {
    const res = await api.login(username, password);
    tokens.set(res);
    setUser(res.user);
  }, []);

  const signOut = useCallback(async () => {
    const refresh = tokens.refresh;
    if (refresh) await api.logout(refresh).catch(() => undefined);
    tokens.clear();
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, loading, isRoot: user?.role === 'ROOT', signIn, signOut }), [user, loading, signIn, signOut]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth outside AuthProvider');
  return v;
}

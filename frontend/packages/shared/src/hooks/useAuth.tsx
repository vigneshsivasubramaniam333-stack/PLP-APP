import { useState, useCallback, createContext, useContext } from 'react';
import { authApi } from '../api/client';
import type { AuthUser } from '../types';

interface AuthContextType {
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
  loading: boolean;
  error: string | null;
  isAuthenticated: boolean;
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const stored = localStorage.getItem(window.__PLP_USER_KEY__ ?? 'plp_user');
    return stored ? JSON.parse(stored) : null;
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const login = useCallback(async (email: string, password: string) => {
    setLoading(true);
    setError(null);
    try {
      const response = await authApi.login(email, password);
      const data = response.data;
      const authUser: AuthUser = {
        userId: data.userId,
        email: data.email,
        fullName: data.fullName,
        role: data.role,
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        linkedEntityId: data.linkedEntityId ?? null,
        linkedEntityType: data.linkedEntityType ?? null,
      };
      localStorage.setItem((window.__PLP_TOKEN_KEY__ ?? 'plp_access_token'), data.accessToken);
      localStorage.setItem((window.__PLP_REFRESH_KEY__ ?? 'plp_refresh_token'), data.refreshToken);
      localStorage.setItem((window.__PLP_USER_KEY__ ?? 'plp_user'), JSON.stringify(authUser));
      setUser(authUser);
      return authUser;
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      const message = axiosErr?.response?.data?.message || (err instanceof Error ? err.message : 'Login failed');
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(window.__PLP_TOKEN_KEY__ ?? 'plp_access_token');
    localStorage.removeItem(window.__PLP_REFRESH_KEY__ ?? 'plp_refresh_token');
    localStorage.removeItem(window.__PLP_USER_KEY__ ?? 'plp_user');
    setUser(null);
  }, []);

  const value: AuthContextType = { user, login, logout, loading, error, isAuthenticated: !!user };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
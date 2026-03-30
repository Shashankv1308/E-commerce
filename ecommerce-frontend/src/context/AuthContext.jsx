import { createContext, useState, useCallback, useMemo } from 'react';
import api, { setAuthToken, clearAuthToken } from '../api/axios';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null); // { email, role }
  const [token, setToken] = useState(null);

  const login = useCallback(async (email, password) => {
    const res = await api.post('/auth/login', { email, password });
    const { token: jwt, role } = res.data;

    setAuthToken(jwt);
    setToken(jwt);
    setUser({ email, role });

    return { role };
  }, []);

  const register = useCallback(async (email, password) => {
    const res = await api.post('/auth/register', { email, password });
    return res.data;
  }, []);

  const logout = useCallback(() => {
    clearAuthToken();
    setToken(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      user,
      token,
      isAuthenticated: !!token,
      isAdmin: user?.role === 'ROLE_ADMIN',
      login,
      register,
      logout,
    }),
    [user, token, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

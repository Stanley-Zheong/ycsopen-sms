import { type ReactNode, useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { isPlatformRole, isSessionActive, useAuthStore } from '@/store/authStore';

type RouteAudience = 'platform' | 'tenant';

export default function ProtectedRoute({ audience, children }: { audience: RouteAudience; children: ReactNode }) {
  const session = useAuthStore((state) => ({
    accessToken: state.accessToken,
    userType: state.userType,
    tenantId: state.tenantId,
    expiresAt: state.expiresAt,
  }));
  const logout = useAuthStore((state) => state.logout);
  const active = isSessionActive(session);
  const matchingRole = audience === 'platform'
    ? isPlatformRole(session.userType)
    : Boolean(session.userType && !isPlatformRole(session.userType));

  useEffect(() => {
    if (session.accessToken && !active) {
      logout();
    }
  }, [active, logout, session.accessToken]);

  if (!active || !matchingRole) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

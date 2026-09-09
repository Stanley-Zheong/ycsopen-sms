import { useQuery } from '@tanstack/react-query';
import { getAccountOverview } from '@/api/identity';
import { protectedQueryKey, useAuthStore } from '@/store/authStore';

/** UI visibility helper only. Every API remains protected by current server-side RBAC. */
export function useIdentityAccess(enabled = true) {
  const sessionUserType = useAuthStore((state) => state.userType);
  const overviewQuery = useQuery({
    queryKey: protectedQueryKey('account-overview'),
    queryFn: getAccountOverview,
    enabled,
  });
  const administrator = sessionUserType === 'ADMIN' || overviewQuery.data?.userType === 'ADMIN';

  return {
    ...overviewQuery,
    can: (permission: string) => administrator
      || Boolean(overviewQuery.data?.permissions.some((item) => item.code === permission)),
  };
}

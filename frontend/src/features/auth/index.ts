import type { Router } from 'vue-router';

import { setUnauthorizedHandler } from '@/api/http';
import { safeRedirect } from '@/router';
import type { useAuthStore } from '@/stores/auth';

type AuthStore = ReturnType<typeof useAuthStore>;

/** 401 统一入口：清空登录态、跳转登录页并带回跳参数 */
export function setupUnauthorizedHandler(router: Router, auth: AuthStore): void {
  setUnauthorizedHandler((redirectPath) => {
    auth.logout();
    const current = router.currentRoute.value;
    if (current.name === 'login') {
      return;
    }
    void router.push({ name: 'login', query: { redirect: safeRedirect(redirectPath, current.fullPath) } });
  });
}

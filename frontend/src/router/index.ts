import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

import { useAuthStore } from '@/stores/auth';
import type { AuthUser } from '@/stores/auth';

declare module 'vue-router' {
  interface RouteMeta {
    /** public 公开；auth 需登录；admin 需 ADMIN */
    permission?: 'public' | 'auth' | 'admin';
    title?: string;
  }
}

/** 参数为 ID 的路由统一校验：必须是正整数 */
const POSITIVE_INT = /^[1-9]\d*$/;

/** 需要校验正整数参数的路由名 → 参数名 */
const ID_ROUTE_PARAMS: Record<string, string> = {
  'product-detail': 'id',
  'seckill-detail': 'activityId',
  'seckill-result': 'activityId',
  'admin-product-edit': 'id',
  'admin-seckill-edit': 'id',
};

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/SiteLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/pages/HomePage.vue'),
        meta: { permission: 'public', title: '首页' },
      },
      {
        path: 'products',
        name: 'products',
        component: () => import('@/pages/ProductListPage.vue'),
        meta: { permission: 'public', title: '商品列表' },
      },
      {
        path: 'products/:id',
        name: 'product-detail',
        component: () => import('@/pages/ProductDetailPage.vue'),
        props: true,
        meta: { permission: 'public', title: '商品详情' },
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('@/pages/search/SearchPage.vue'),
        meta: { permission: 'public', title: '搜索' },
      },
      {
        path: 'seckill',
        name: 'seckill-list',
        component: () => import('@/pages/seckill/SeckillListPage.vue'),
        meta: { permission: 'public', title: '秒杀活动' },
      },
      {
        path: 'seckill/:activityId',
        name: 'seckill-detail',
        component: () => import('@/pages/seckill/SeckillDetailPage.vue'),
        props: true,
        meta: { permission: 'public', title: '秒杀活动详情' },
      },
      {
        path: 'seckill/:activityId/result',
        name: 'seckill-result',
        component: () => import('@/pages/seckill/SeckillResultPage.vue'),
        props: true,
        meta: { permission: 'auth', title: '抢购结果' },
      },
      {
        path: 'orders/:orderNo',
        name: 'order-detail',
        component: () => import('@/pages/order/OrderDetailPage.vue'),
        props: true,
        meta: { permission: 'auth', title: '订单详情' },
      },
      {
        path: 'login',
        name: 'login',
        component: () => import('@/pages/LoginPage.vue'),
        meta: { permission: 'public', title: '登录' },
      },
      {
        path: 'me',
        name: 'me',
        component: () => import('@/pages/MePage.vue'),
        meta: { permission: 'auth', title: '当前用户' },
      },
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/pages/ForbiddenPage.vue'),
        meta: { permission: 'public', title: '无访问权限' },
      },
      {
        path: '404',
        name: 'not-found',
        component: () => import('@/pages/NotFoundPage.vue'),
        meta: { permission: 'public', title: '页面不存在' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'catch-all',
        component: () => import('@/pages/NotFoundPage.vue'),
        meta: { permission: 'public', title: '页面不存在' },
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { permission: 'admin' },
    children: [
      {
        path: '',
        name: 'admin-home',
        component: () => import('@/pages/admin/AdminHomePage.vue'),
        meta: { permission: 'admin', title: '管理端' },
      },
      {
        path: 'products',
        name: 'admin-products',
        component: () => import('@/pages/admin/AdminProductListPage.vue'),
        meta: { permission: 'admin', title: '商品管理' },
      },
      {
        path: 'products/new',
        name: 'admin-product-create',
        component: () => import('@/pages/admin/AdminProductCreatePage.vue'),
        meta: { permission: 'admin', title: '新增商品' },
      },
      {
        path: 'products/:id/edit',
        name: 'admin-product-edit',
        component: () => import('@/pages/admin/AdminProductEditPage.vue'),
        props: true,
        meta: { permission: 'admin', title: '编辑商品' },
      },
      {
        path: 'seckill',
        name: 'admin-seckill',
        component: () => import('@/pages/admin/AdminSeckillListPage.vue'),
        meta: { permission: 'admin', title: '秒杀活动管理' },
      },
      {
        path: 'seckill/new',
        name: 'admin-seckill-create',
        component: () => import('@/pages/admin/AdminSeckillCreatePage.vue'),
        meta: { permission: 'admin', title: '创建活动' },
      },
      {
        path: 'seckill/:id/edit',
        name: 'admin-seckill-edit',
        component: () => import('@/pages/admin/AdminSeckillEditPage.vue'),
        props: true,
        meta: { permission: 'admin', title: '编辑活动' },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    if (to.hash) {
      return { el: to.hash, top: 88, behavior: 'smooth' };
    }
    return { top: 0 };
  },
});

/** 只接受站内路径，避免开放重定向 */
export function safeRedirect(raw: unknown, fallback = '/'): string {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//')) {
    return fallback;
  }
  if (raw.startsWith('/login')) {
    return fallback;
  }
  return raw;
}

async function ensureUser(auth: ReturnType<typeof useAuthStore>): Promise<AuthUser | null> {
  try {
    const { fetchMe } = await import('@/api/auth');
    const info = await fetchMe();
    auth.setUser(info);
    return info;
  } catch {
    auth.logout();
    return null;
  }
}

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  auth.restore();

  const idParam = typeof to.name === 'string' ? ID_ROUTE_PARAMS[to.name] : undefined;
  if (idParam) {
    const value = to.params[idParam];
    if (typeof value !== 'string' || !POSITIVE_INT.test(value)) {
      return { name: 'not-found', replace: true };
    }
  }

  const permission = to.meta.permission ?? 'public';

  if (permission === 'public') {
    return true;
  }

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }

  // 令牌存在但用户信息缺失时先补齐，期间不闪无权限页
  if (!auth.user) {
    const user = await ensureUser(auth);
    if (!user) {
      return { name: 'login', query: { redirect: to.fullPath } };
    }
  }

  if (permission === 'admin' && auth.user?.role !== 'ADMIN') {
    return { name: 'forbidden' };
  }

  return true;
});

const APP_TITLE = '序集 XUJI';

router.afterEach((to) => {
  const title = to.meta.title;
  document.title = title ? `${title} · ${APP_TITLE}` : `${APP_TITLE} · 把喜欢的，过成日常`;
});

export default router;

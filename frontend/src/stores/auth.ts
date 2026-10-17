import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

export interface AuthUser {
  id: number;
  username: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
}

const TOKEN_KEY = 'mall.token';
const USER_KEY = 'mall.user';
const EXPIRES_KEY = 'mall.expiresAt';

function readUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as AuthUser;
    if (typeof parsed?.id !== 'number' || typeof parsed?.role !== 'string') {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function readExpiresAt(): number | null {
  const raw = localStorage.getItem(EXPIRES_KEY);
  if (!raw) return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

function clearStorage(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(EXPIRES_KEY);
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null);
  const user = ref<AuthUser | null>(null);
  const expiresAt = ref<number | null>(null);
  /** 登录态是否已从持久化数据恢复，用于守卫避免闪回登录页 */
  const restored = ref(false);

  const isLoggedIn = computed(() => Boolean(token.value) && (!expiresAt.value || expiresAt.value > Date.now()));
  const isAdmin = computed(() => user.value?.role === 'ADMIN');

  function persist(): void {
    if (token.value) {
      localStorage.setItem(TOKEN_KEY, token.value);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }

    if (user.value) {
      localStorage.setItem(USER_KEY, JSON.stringify(user.value));
    } else {
      localStorage.removeItem(USER_KEY);
    }

    if (typeof expiresAt.value === 'number') {
      localStorage.setItem(EXPIRES_KEY, String(expiresAt.value));
    } else {
      localStorage.removeItem(EXPIRES_KEY);
    }
  }

  /** 应用启动时恢复登录态；令牌过期或数据损坏时保持游客态 */
  function restore(): void {
    const storedToken = localStorage.getItem(TOKEN_KEY);
    const storedExpires = readExpiresAt();

    if (!storedToken) {
      clearStorage();
      restored.value = true;
      return;
    }

    if (storedExpires !== null && storedExpires <= Date.now()) {
      token.value = null;
      user.value = null;
      expiresAt.value = null;
      clearStorage();
      restored.value = true;
      return;
    }

    token.value = storedToken;
    user.value = readUser();
    expiresAt.value = storedExpires;
    restored.value = true;
  }

  function setSession(payload: { token: string; expiresIn: number; user: AuthUser }): void {
    token.value = payload.token;
    user.value = payload.user;
    expiresAt.value = Date.now() + payload.expiresIn * 1000;
    persist();
  }

  /** 令牌在但用户信息缺失时补齐 */
  function setUser(nextUser: AuthUser): void {
    user.value = nextUser;
    persist();
  }

  function logout(): void {
    token.value = null;
    user.value = null;
    expiresAt.value = null;
    clearStorage();
  }

  return { token, user, expiresAt, restored, isLoggedIn, isAdmin, restore, setSession, setUser, logout };
});

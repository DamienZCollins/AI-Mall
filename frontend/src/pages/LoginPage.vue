<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { login } from '@/api/auth';
import { BIZ_CODE, isApiError } from '@/api/types';
import { safeRedirect } from '@/router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const form = reactive({ username: '', password: '' });
const errors = reactive<{ username: string; password: string }>({ username: '', password: '' });
const formError = ref('');
const submitting = ref(false);

const redirectTarget = computed(() => safeRedirect(route.query.redirect, '/'));

const demoTip = '本地测试账号：admin / admin123（管理员），user / user123（普通用户）';

function validate(): boolean {
  errors.username = '';
  errors.password = '';

  const username = form.username.trim();
  if (!username) {
    errors.username = '请输入用户名';
  } else if (username.length > 32) {
    errors.username = '用户名长度不能超过 32';
  }

  if (!form.password) {
    errors.password = '请输入密码';
  } else if (form.password.length > 64) {
    errors.password = '密码长度不能超过 64';
  }

  return !errors.username && !errors.password;
}

async function submit(): Promise<void> {
  if (submitting.value) return;
  formError.value = '';
  if (!validate()) return;

  submitting.value = true;
  try {
    const result = await login({ username: form.username.trim(), password: form.password });
    auth.setSession(result);
    await router.replace(redirectTarget.value);
  } catch (caught) {
    if (!isApiError(caught)) {
      formError.value = '登录失败，请稍后重试';
      return;
    }
    switch (caught.code) {
      case BIZ_CODE.LOGIN_FAILED:
        // 用户名或密码错误：保留已输入的用户名
        formError.value = '用户名或密码错误';
        form.password = '';
        break;
      case BIZ_CODE.USER_DISABLED:
        formError.value = '账号已被禁用，请联系管理员';
        break;
      case BIZ_CODE.BAD_REQUEST:
        formError.value = caught.message || '请求参数不合法';
        break;
      default:
        formError.value =
          caught.kind === 'timeout'
            ? '登录超时，请重试'
            : caught.kind === 'network'
              ? '网络异常，请检查网络后重试'
              : caught.message || '登录失败，请稍后重试';
    }
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  // 已登录访问 /login 直接回到目标页
  if (auth.isLoggedIn) {
    void router.replace(redirectTarget.value);
  }
});
</script>

<template>
  <div class="page login-page">
    <div class="container">
      <div class="login-grid">
        <section class="login-intro" aria-labelledby="login-intro-title">
          <p class="eyebrow">XUJI ACCOUNT</p>
          <h1 id="login-intro-title" class="login-intro__title">登录，继续你的<br /><span>日常挑选</span></h1>
          <p class="login-intro__text">
            登录后可以参与限时秒杀、查看抢购结果与订单，并在支付截止时间内完成模拟支付。
          </p>
          <ul class="login-intro__list">
            <li>抢占秒杀名额并实时查看排队结果</li>
            <li>订单状态与倒计时同步更新</li>
            <li>管理员可进入商品与活动维护</li>
          </ul>
        </section>

        <section class="login-panel" aria-labelledby="login-title">
          <h2 id="login-title" class="login-panel__title">账号登录</h2>
          <p class="login-panel__subtitle">请输入用户名与密码</p>

          <form class="login-form" novalidate @submit.prevent="submit">
            <div class="field">
              <label for="login-username">用户名</label>
              <input
                id="login-username"
                v-model="form.username"
                class="input"
                name="username"
                type="text"
                autocomplete="username"
                maxlength="32"
                :aria-invalid="Boolean(errors.username)"
                :aria-describedby="errors.username ? 'login-username-error' : undefined"
                @blur="validate"
              />
              <p v-if="errors.username" id="login-username-error" class="field__error" role="alert">
                {{ errors.username }}
              </p>
            </div>

            <div class="field">
              <label for="login-password">密码</label>
              <input
                id="login-password"
                v-model="form.password"
                class="input"
                name="password"
                type="password"
                autocomplete="current-password"
                maxlength="64"
                :aria-invalid="Boolean(errors.password)"
                :aria-describedby="errors.password ? 'login-password-error' : undefined"
                @blur="validate"
              />
              <p v-if="errors.password" id="login-password-error" class="field__error" role="alert">
                {{ errors.password }}
              </p>
            </div>

            <p v-if="formError" class="login-form__error" role="alert">{{ formError }}</p>

            <button class="button button--primary button--block" type="submit" :disabled="submitting">
              {{ submitting ? '登录中…' : '登录' }}
            </button>

            <p class="login-form__hint">{{ demoTip }}</p>
          </form>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  padding-top: 48px;
  padding-bottom: 96px;
}

.login-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: start;
  gap: 64px;
}

.login-intro__title {
  margin-top: 16px;
  font-size: clamp(34px, 4vw, 52px);
  font-weight: 900;
  letter-spacing: -0.075em;
  line-height: 1.08;
}

.login-intro__title span {
  color: var(--persimmon);
}

.login-intro__text {
  max-width: 420px;
  margin-top: 20px;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.85;
}

.login-intro__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 26px;
}

.login-intro__list li {
  position: relative;
  padding-left: 20px;
  color: var(--ink-soft);
  font-size: 13px;
}

.login-intro__list li::before {
  position: absolute;
  top: 8px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--persimmon);
  content: '';
}

.login-panel {
  padding: 34px 32px 30px;
  border: 1px solid var(--line);
  border-radius: var(--radius-l);
  background: var(--paper-light);
  box-shadow: 0 12px 36px rgba(29, 27, 23, 0.06);
}

.login-panel__title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.login-panel__subtitle {
  margin-top: 6px;
  color: var(--ink-muted);
  font-size: 12px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 22px;
}

.login-form__error {
  padding: 10px 12px;
  border: 1px solid rgba(189, 67, 40, 0.32);
  border-radius: var(--radius-s);
  color: var(--persimmon-dark);
  background: rgba(189, 67, 40, 0.07);
  font-size: 12px;
}

.login-form__hint {
  color: var(--ink-muted);
  font-size: 11px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .login-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .login-page {
    padding-top: 28px;
    padding-bottom: 64px;
  }
}

@media (max-width: 600px) {
  .login-panel {
    padding: 24px 18px 22px;
  }

  .login-intro__text {
    font-size: 13px;
  }
}
</style>

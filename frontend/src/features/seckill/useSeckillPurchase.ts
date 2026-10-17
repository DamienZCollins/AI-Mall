import { useAuthStore } from '@/stores/auth';
import { BIZ_CODE, isApiError } from '@/api/types';
import { placeOrder } from '@/api/seckill';
import { useRouter } from 'vue-router';
import type { Router } from 'vue-router';
import { ref } from 'vue';

export interface PurchaseErrorInfo {
  code: number | null;
  title: string;
  description: string;
  /** 是否允许立即重新发起抢购 */
  canRetry: boolean;
  /** 是否转入结果页查询 */
  goToResult: boolean;
}

export function describePurchaseError(error: unknown): PurchaseErrorInfo {
  if (!isApiError(error)) {
    return {
      code: null,
      title: '抢购请求失败',
      description: '发生未知错误，请刷新活动信息后重试。',
      canRetry: true,
      goToResult: false,
    };
  }

  switch (error.code) {
    case BIZ_CODE.ACTIVITY_NOT_STARTED:
      return {
        code: error.code,
        title: '活动尚未开始',
        description: '本场秒杀还未开始，请等待开始时间后重试。',
        canRetry: false,
        goToResult: false,
      };
    case BIZ_CODE.ACTIVITY_ENDED:
      return {
        code: error.code,
        title: '活动已结束',
        description: '本场秒杀已经结束，可以看看其他进行中的活动。',
        canRetry: false,
        goToResult: false,
      };
    case BIZ_CODE.SOLD_OUT:
      return {
        code: error.code,
        title: '商品已售罄',
        description: '本场库存已被抢完，可关注下一场活动。',
        canRetry: false,
        goToResult: false,
      };
    case BIZ_CODE.PER_USER_LIMIT:
      return {
        code: error.code,
        title: '已达限购数量',
        description: '同一活动每人限购，你已经下过单了。',
        canRetry: false,
        goToResult: false,
      };
    case BIZ_CODE.QUEUE_UNAVAILABLE:
      return {
        code: error.code,
        title: '排队服务暂不可用',
        description: '排队服务暂时不可用，请稍后重新抢购。',
        canRetry: true,
        goToResult: false,
      };
    case BIZ_CODE.PURCHASE_PROCESSING:
      return {
        code: error.code,
        title: '抢购请求处理中',
        description: '上一次抢购请求仍在处理中，正在为你查询结果。',
        canRetry: false,
        goToResult: true,
      };
    case BIZ_CODE.ACTIVITY_NOT_FOUND:
      return {
        code: error.code,
        title: '秒杀活动不存在',
        description: '活动可能已下线，请返回活动列表查看。',
        canRetry: false,
        goToResult: false,
      };
    default:
      if (error.kind === 'timeout') {
        return {
          code: error.code,
          title: '抢购超时',
          description: '抢购请求超时，请稍后在结果页确认是否已受理。',
          canRetry: true,
          goToResult: false,
        };
      }
      if (error.kind === 'network') {
        return {
          code: error.code,
          title: '网络异常',
          description: '抢购请求没有到达服务器，请检查网络后重试。',
          canRetry: true,
          goToResult: false,
        };
      }
      return {
        code: error.code,
        title: '抢购未完成',
        description: error.message || '抢购请求处理失败，请稍后重试。',
        canRetry: true,
        goToResult: false,
      };
  }
}

/**
 * 抢购入口：登录校验、错误分支与跳转结果页集中在此，页面不直接调 api。
 * code === 0 仅代表已受理，必须跳到结果页轮询，不能显示「下单成功」。
 */
export function useSeckillPurchase(activityId: number) {
  const auth = useAuthStore();
  const router = useRouter();
  const submitting = ref(false);
  const error = ref<PurchaseErrorInfo | null>(null);

  async function purchase(targetRouter: Router = router): Promise<boolean> {
    if (submitting.value) return false;
    error.value = null;

    if (!auth.isLoggedIn) {
      // 未登录先跳登录，登录成功后回到当前活动详情
      await targetRouter.push({
        name: 'login',
        query: { redirect: `/seckill/${activityId}` },
      });
      return false;
    }

    submitting.value = true;
    try {
      await placeOrder(activityId);
      // 只跳结果页并带受理标记，结果由轮询决定
      await targetRouter.push({
        name: 'seckill-result',
        params: { activityId: String(activityId) },
        query: { accepted: '1' },
      });
      return true;
    } catch (caught) {
      const info = describePurchaseError(caught);
      error.value = info;
      if (info.goToResult) {
        await targetRouter.push({
          name: 'seckill-result',
          params: { activityId: String(activityId) },
          query: { accepted: '1' },
        });
      }
      return false;
    } finally {
      submitting.value = false;
    }
  }

  return { submitting, error, purchase };
}

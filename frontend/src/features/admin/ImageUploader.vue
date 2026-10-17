<script setup lang="ts">
import { computed, ref } from 'vue';

import { uploadProductImage } from '@/api/admin';
import { BIZ_CODE, isApiError } from '@/api/types';
import { resolveImageUrl } from '@/utils/image';

const props = defineProps<{
  modelValue: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];
const ALLOWED_EXT = /\.(png|jpe?g|gif|webp)$/i;
const MAX_BYTES = 5 * 1024 * 1024;

const inputRef = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const progress = ref(0);
const errorMessage = ref('');
const localPreview = ref('');
const uploadingName = ref('');

const previewSrc = computed(() => localPreview.value || resolveImageUrl(props.modelValue));
const hasImage = computed(() => Boolean(props.modelValue) || Boolean(localPreview.value));

function revokeLocal(): void {
  if (localPreview.value) {
    URL.revokeObjectURL(localPreview.value);
    localPreview.value = '';
  }
}

function pick(): void {
  inputRef.value?.click();
}

async function onChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  errorMessage.value = '';

  if (!ALLOWED_TYPES.includes(file.type) || !ALLOWED_EXT.test(file.name)) {
    errorMessage.value = '只支持 png、jpg、gif、webp 图片';
    input.value = '';
    return;
  }

  if (file.size > MAX_BYTES) {
    errorMessage.value = '图片大小不能超过 5MB';
    input.value = '';
    return;
  }

  revokeLocal();
  localPreview.value = URL.createObjectURL(file);
  uploadingName.value = file.name;
  uploading.value = true;
  progress.value = 0;

  try {
    const url = await uploadProductImage(file, (percent) => {
      progress.value = percent;
    });
    if (typeof url === 'string' && url) {
      emit('update:modelValue', url);
      revokeLocal();
    } else {
      errorMessage.value = '图片上传失败，请重试';
    }
  } catch (caught) {
    if (isApiError(caught)) {
      if (caught.code === BIZ_CODE.IMAGE_UPLOAD_FAILED) {
        const isGeneric = !caught.message || caught.message === '图片上传失败';
        errorMessage.value = isGeneric
          ? '图片存储服务不可用（RustFS 未启动或 bucket 不可用），请确认服务后重试'
          : caught.message;
      } else if (caught.kind === 'network') {
        errorMessage.value = '网络异常，上传没有到达服务器，请检查网络后重试';
      } else if (caught.kind === 'timeout') {
        errorMessage.value = '上传超时，请稍后重试';
      } else {
        errorMessage.value = caught.message || '上传失败，请稍后重试';
      }
    } else {
      errorMessage.value = '上传失败，请稍后重试';
    }
  } finally {
    uploading.value = false;
    input.value = '';
  }
}

function removeImage(): void {
  revokeLocal();
  emit('update:modelValue', '');
  errorMessage.value = '';
}

function retry(): void {
  errorMessage.value = '';
  pick();
}
</script>

<template>
  <div class="uploader">
    <div class="uploader__preview">
      <img v-if="hasImage" :src="previewSrc" alt="商品图片预览" width="220" height="165" />
      <span v-else class="uploader__placeholder">未选择图片</span>
      <span v-if="uploading" class="uploader__progress">上传中 {{ progress }}%</span>
    </div>

    <div class="uploader__actions">
      <input
        ref="inputRef"
        class="sr-only"
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        :disabled="disabled || uploading"
        @change="onChange"
      />
      <button class="button button--outline button--small" type="button" :disabled="disabled || uploading" @click="pick">
        {{ hasImage ? '重新选择' : '选择文件' }}
      </button>
      <button v-if="hasImage" class="button button--quiet button--small" type="button" :disabled="disabled || uploading" @click="removeImage">
        删除图片
      </button>
      <button v-if="errorMessage" class="button button--quiet button--small" type="button" :disabled="uploading" @click="retry">
        重试上传
      </button>
    </div>

    <p class="uploader__hint">支持 png、jpg、gif、webp，最大 5 MB；上传成功后写入图片 URL。</p>
    <p v-if="uploadingName && uploading" class="uploader__hint">正在上传：{{ uploadingName }}</p>
    <p v-if="errorMessage" class="field__error" role="alert">{{ errorMessage }}</p>
    <p v-if="modelValue" class="uploader__url">{{ modelValue }}</p>
  </div>
</template>

<style scoped>
.uploader {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.uploader__preview {
  position: relative;
  display: grid;
  width: min(100%, 220px);
  height: 165px;
  place-items: center;
  overflow: hidden;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-s);
  background: var(--paper-deep);
}

.uploader__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.uploader__placeholder {
  color: var(--ink-muted);
  font-size: 11px;
}

.uploader__progress {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 4px 8px;
  color: var(--white);
  background: rgba(20, 18, 15, 0.62);
  font-family: var(--font-latin);
  font-size: 10px;
  text-align: center;
}

.uploader__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.uploader__hint {
  color: var(--ink-muted);
  font-size: 11px;
  line-height: 1.6;
}

.uploader__url {
  overflow: hidden;
  color: var(--ink-muted);
  font-family: var(--font-latin);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

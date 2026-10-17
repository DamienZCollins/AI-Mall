/**
 * 图片兜底：商品图统一使用后端 imageUrl，加载失败时回退到内联 SVG 占位，
 * 避免离线或图片服务不可用时出现破图。
 */
const PLACEHOLDER_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 540" role="img" aria-label="商品图片暂不可用">
<rect width="720" height="540" fill="#ebe3d3"/>
<g fill="none" stroke="#8a8174" stroke-width="6" stroke-linecap="round" stroke-linejoin="round">
<rect x="252" y="196" width="216" height="148" rx="14"/>
<circle cx="316" cy="252" r="20"/>
<path d="m276 322 62-66 48 48 34-30 46 48"/>
</g>
<text x="360" y="404" text-anchor="middle" fill="#8a8174" font-family="sans-serif" font-size="22">图片暂不可用</text>
</svg>`;

export const IMAGE_PLACEHOLDER = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(PLACEHOLDER_SVG)}`;

/** 绑到 <img> 的 @error 上：只兜底一次，避免占位图再次失败时死循环 */
export function handleImageError(event: Event): void {
  const target = event.target as HTMLImageElement | null;
  if (!target || target.dataset.fallback === 'true') return;
  target.dataset.fallback = 'true';
  target.src = IMAGE_PLACEHOLDER;
}

/** 空地址不发起请求，直接返回占位图 */
export function resolveImageUrl(url: string | null | undefined): string {
  return url && url.trim() ? url : IMAGE_PLACEHOLDER;
}

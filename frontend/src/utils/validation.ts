/** 前端校验规则与后端 @Size / @DecimalMin / @Digits 口径一致，长度按字符数计 */
export interface ValidationResult {
  valid: boolean;
  message: string;
}

const OK: ValidationResult = { valid: true, message: '' };

function fail(message: string): ValidationResult {
  return { valid: false, message };
}

export function requiredText(value: string | null | undefined, label: string, max: number): ValidationResult {
  const trimmed = (value ?? '').trim();
  if (!trimmed) return fail(`${label}不能为空`);
  if ([...trimmed].length > max) return fail(`${label}长度不能超过 ${max}`);
  return OK;
}

export function optionalText(value: string | null | undefined, label: string, max: number): ValidationResult {
  const trimmed = (value ?? '').trim();
  if (!trimmed) return OK;
  if ([...trimmed].length > max) return fail(`${label}长度不能超过 ${max}`);
  return OK;
}

export function positiveInteger(value: unknown, label: string, min = 1): ValidationResult {
  const raw = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(raw)) return fail(`${label}不能为空`);
  if (!Number.isInteger(raw)) return fail(`${label}必须是整数`);
  if (raw < min) return fail(min <= 1 ? `${label}必须大于 0` : `${label}不能小于 ${min}`);
  return OK;
}

export function nonNegativeInteger(value: unknown, label: string): ValidationResult {
  const raw = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(raw)) return fail(`${label}不能为空`);
  if (!Number.isInteger(raw)) return fail(`${label}必须是整数`);
  if (raw < 0) return fail(`${label}不能为负数`);
  return OK;
}

export function positiveAmount(value: unknown, label: string): ValidationResult {
  const raw = typeof value === 'number' ? value : Number(String(value).trim());
  if (!Number.isFinite(raw)) return fail(`${label}必须大于 0`);
  if (raw < 0.01) return fail(`${label}必须大于 0`);
  const match = /^\d{1,8}(\.\d{1,2})?$/.exec(String(raw));
  if (!match) return fail(`${label}格式不正确`);
  return OK;
}

/** 售价 / 秒杀价：两位数小数 + 最多 8 位整数 */
export function normalizedAmount(input: string): number | null {
  const trimmed = input.trim();
  if (!trimmed) return null;
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(trimmed)) return null;
  return Number(trimmed);
}

export function validateProductForm(form: {
  name: string;
  brand: string;
  categoryId: string;
  description: string;
  sellingPoint: string;
  price: string;
  stock: string;
  imageUrl: string;
}): Record<string, string> {
  const errors: Record<string, string> = {};

  const name = requiredText(form.name, '商品名称', 128);
  if (!name.valid) errors.name = name.message;

  const brand = optionalText(form.brand, '品牌', 64);
  if (!brand.valid) errors.brand = brand.message;

  const category = positiveInteger(form.categoryId, '分类');
  if (!category.valid) errors.categoryId = category.message;

  const description = optionalText(form.description, '商品描述', 2000);
  if (!description.valid) errors.description = description.message;

  const sellingPoint = optionalText(form.sellingPoint, '卖点', 255);
  if (!sellingPoint.valid) errors.sellingPoint = sellingPoint.message;

  const price = positiveAmount(form.price, '售价');
  if (!price.valid) errors.price = price.message === '售价必须大于 0' ? '售价必须大于 0' : '售价格式不正确';

  const stock = nonNegativeInteger(form.stock, '库存');
  if (!stock.valid) errors.stock = stock.message;

  if ([...form.imageUrl].length > 512) {
    errors.imageUrl = '图片 URL 长度不能超过 512';
  }

  return errors;
}

export function validateActivityForm(form: {
  productId: string;
  title: string;
  seckillPrice: string;
  totalStock: string;
  perUserLimit: string;
  startTime: string;
  endTime: string;
  payTimeoutMinutes: string;
  originalPrice?: number | null;
}): Record<string, string> {
  const errors: Record<string, string> = {};

  const product = positiveInteger(form.productId, '秒杀商品');
  if (!product.valid) errors.productId = '请选择上架商品';

  const title = requiredText(form.title, '活动标题', 128);
  if (!title.valid) errors.title = title.message;

  const price = positiveAmount(form.seckillPrice, '秒杀价');
  if (!price.valid) {
    errors.seckillPrice = price.message === '秒杀价必须大于 0' ? '秒杀价必须大于 0' : '秒杀价格式不正确';
  } else if (typeof form.originalPrice === 'number' && Number(form.seckillPrice) >= form.originalPrice) {
    errors.seckillPrice = '秒杀价必须低于商品原价';
  }

  const stock = positiveInteger(form.totalStock, '活动总量');
  if (!stock.valid) errors.totalStock = '活动总量必须大于 0';

  if (form.perUserLimit.trim()) {
    const limit = positiveInteger(form.perUserLimit, '限购数量');
    if (!limit.valid) errors.perUserLimit = '限购数量必须大于 0';
  }

  if (!form.startTime) {
    errors.startTime = '开始时间不能为空';
  }

  if (!form.endTime) {
    errors.endTime = '结束时间不能为空';
  } else if (form.startTime && form.endTime <= form.startTime) {
    errors.endTime = '结束时间必须晚于开始时间';
  }

  if (form.payTimeoutMinutes.trim()) {
    const timeout = positiveInteger(form.payTimeoutMinutes, '超时分钟数');
    if (!timeout.valid) errors.payTimeoutMinutes = '超时分钟数必须大于 0';
  }

  return errors;
}

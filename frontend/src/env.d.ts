/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  /** `local` 时商品数据取自仓库 素材/ 生成的本地目录，用于商品图入库前验收 */
  readonly VITE_CATALOG_MODE?: 'backend' | 'local';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

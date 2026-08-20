import noBrandColorToken from './no-brand-color-token.mjs'
import preferFaComponent from './prefer-fa-component.mjs'

/**
 * YuDream 本地 eslint 规则集，经根 eslint.config.js 以 yudream/* 命名空间注册。
 */
export default {
  meta: {
    name: 'eslint-plugin-yudream',
  },
  rules: {
    'prefer-fa-component': preferFaComponent,
    'no-brand-color-token': noBrandColorToken,
  },
}

import assert from 'node:assert/strict'
import test from 'node:test'
import { getMenuNodeKey } from './menu-key'

test('uses the persisted menu code for layout branches with the same route path', () => {
  const first = {
    path: '/platform/plugins/yudream-wallet/system',
    meta: { menuCode: 'plugin:yudream-wallet:parent:yudreamWallet:system' },
    children: [{ path: '/platform/plugins/yudream-wallet/system/settings', meta: {} }],
  }
  const second = {
    path: '/platform/plugins/yudream-wallet/system',
    meta: { menuCode: 'plugin:yudream-alipay:parent:yudreamAlipay:system' },
    children: [{ path: '/platform/plugins/yudream-alipay/system/settings', meta: {} }],
  }

  assert.notEqual(getMenuNodeKey(first as any, () => 'first'), getMenuNodeKey(second as any, () => 'second'))
})

test('keeps route paths as keys for navigable leaf menus', () => {
  const leaf = { path: '/platform/plugins/yudream-wallet', meta: {} }

  assert.equal(getMenuNodeKey(leaf as any, () => 'fallback'), leaf.path)
})

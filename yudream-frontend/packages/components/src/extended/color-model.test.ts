/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import test from 'node:test'
import { formatYdColor, hslToRgb, oklchToRgb, parseYdColor, rgbToHsl, rgbToOklch } from './color-model'

test('HEX 短写与 alpha 可往返', () => {
  assert.deepEqual(parseYdColor('#f08c'), { r: 255, g: 0, b: 136, a: 0.8 })
  const color = parseYdColor('#33669980')!
  assert.deepEqual(parseYdColor(formatYdColor(color, 'hex', true)), color)
})

test('rgb、hsl 与 OKLCH 格式可以解析和转换', () => {
  const rgb = parseYdColor('rgb(255 0 0)')!
  assert.deepEqual(rgb, { r: 255, g: 0, b: 0, a: 1 })
  assert.deepEqual(hslToRgb(rgbToHsl(rgb)), rgb)
  const oklch = rgbToOklch(rgb)
  const roundTrip = oklchToRgb(oklch)
  assert.ok(Math.abs(roundTrip.r - rgb.r) <= 1)
  assert.ok(Math.abs(roundTrip.g - rgb.g) <= 1)
  assert.ok(Math.abs(roundTrip.b - rgb.b) <= 1)
})

test('百分比 RGB 与透明度按 CSS 语义解析', () => {
  assert.deepEqual(parseYdColor('rgb(100% 0% 50% / 25%)'), { r: 255, g: 0, b: 128, a: 0.25 })
})

test('拒绝不完整颜色，保留 transparent 语义', () => {
  assert.equal(parseYdColor('not-a-color'), null)
  assert.deepEqual(parseYdColor('transparent'), { r: 0, g: 0, b: 0, a: 0 })
})

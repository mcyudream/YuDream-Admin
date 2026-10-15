/**
 * 角色/部门数据色的展示助手：把接口返回的 `#RRGGBB` 代表色转成
 * 「低透明度底色 + 数据色前景」的内联样式，供图标色块与彩色标签共用。
 *
 * 只消费中性语义变量作未设色兜底，不引入业务色板与品牌色令牌；
 * 内联样式只承载接口数据色本身。
 */
import type { CSSProperties } from 'vue'
import { normalizeHexColor } from '@fantastic-admin/themes/runtime'

/** 未设置代表色时的中性强调色（中性语义变量）。 */
export const ENTITY_NEUTRAL_COLOR = 'var(--color-text-3)'

/** 归一化数据色：`#RGB` / `#RRGGBB` → 小写 `#rrggbb`；非法或空值返回 undefined。 */
export function normalizeEntityColor(value?: string | null): string | undefined {
  return normalizeHexColor(value || '') || undefined
}

/** 强调色回退：有效数据色优先，否则使用中性语义变量。 */
export function entityAccentColor(value?: string | null): string {
  return normalizeEntityColor(value) ?? ENTITY_NEUTRAL_COLOR
}

/** 图标/色块样式：数据色低透明度铺底，图标与圆点取数据色本身。 */
export function entityTintStyle(value?: string | null, strength = 14): CSSProperties {
  const accent = entityAccentColor(value)
  return {
    color: accent,
    backgroundColor: `color-mix(in oklab, ${accent} ${strength}%, transparent)`,
  }
}

/** 标签样式：与色块同一套取色，另加同色描边保持边界可读。 */
export function entityTagStyle(value?: string | null): CSSProperties {
  const accent = entityAccentColor(value)
  return {
    color: accent,
    backgroundColor: `color-mix(in oklab, ${accent} 14%, transparent)`,
    borderColor: `color-mix(in oklab, ${accent} 32%, transparent)`,
  }
}

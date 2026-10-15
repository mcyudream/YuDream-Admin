/**
 * 部门视觉兜底：部门没有上传头像时，按名称关键词映射 Morphicons 图标。
 * 列表、表单与用户部门的部门 chip 共用这一套映射，避免各页面自造图标表。
 */

/** 未命中任何关键词时的兜底图标。 */
export const DEPT_FALLBACK_ICON = 'morph:building-2'

/** 名称关键词 → 图标（按声明顺序匹配，先命中先用）。 */
const DEPT_ICON_RULES: Array<{ keywords: string[], icon: string }> = [
  { keywords: ['技术', '研发', '开发', '运维', '工程'], icon: 'morph:code' },
  { keywords: ['财务', '审计'], icon: 'morph:wallet' },
  { keywords: ['人事', '行政', '人力'], icon: 'morph:users' },
  { keywords: ['市场', '运营', '宣传'], icon: 'morph:megaphone' },
  { keywords: ['教学', '教务', '学生'], icon: 'morph:graduation-cap' },
  { keywords: ['仓库', '采购', '物资'], icon: 'morph:package' },
]

/** 按部门名称给出兜底图标；空名称或未知关键词统一返回 `morph:building-2`。 */
export function deptFallbackIcon(name?: string | null): string {
  const normalized = (name || '').trim()
  if (!normalized) {
    return DEPT_FALLBACK_ICON
  }
  return DEPT_ICON_RULES.find(rule => rule.keywords.some(keyword => normalized.includes(keyword)))?.icon
    ?? DEPT_FALLBACK_ICON
}

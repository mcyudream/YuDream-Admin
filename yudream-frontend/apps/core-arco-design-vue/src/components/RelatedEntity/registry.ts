import type { Component } from 'vue'

/**
 * 关联实体引用：列表行里能拿到的稳定标识 + 展示回退；细节由提供者在卡片打开后再补全。
 */
export interface RelatedEntityRef {
  /** 实体类型标识，如 user / plugin / dept */
  entityType: string
  /** 实体主键（Long 语义，一律字符串） */
  entityId: string
  /** 列表里已有的名称，卡片加载完成前先渲染，避免闪烁 */
  label?: string
  /** 列表里的次要信息（邮箱、登录 ID 等），显示在名称下方 */
  secondary?: string
  avatar?: string
  /** 仅渲染头像触发（表格身份列内使用） */
  avatarOnly?: boolean
}

/**
 * 实体预览卡提供者：接收 {@link RelatedEntityRef}，自行负责加载细节、就地查看与编辑，并在改动后 emit changed。
 * 宿主内置类型在 `register.ts` 注册；插件后续经 SDK 注册自己的实体类型，未注册类型由宿主优雅降级为纯文本。
 */
export type RelatedEntityProvider = Component

const providers = new Map<string, RelatedEntityProvider>()

export function registerRelatedEntity(type: string, provider: RelatedEntityProvider) {
  providers.set(type, provider)
}

export function resolveRelatedEntity(type?: string) {
  return type ? providers.get(type) : undefined
}

/** 已注册的实体类型，供调度/调试与插件冲突排查 */
export function registeredRelatedEntityTypes() {
  return [...providers.keys()]
}

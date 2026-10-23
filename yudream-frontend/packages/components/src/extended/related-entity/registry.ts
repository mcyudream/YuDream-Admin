import type { Component } from 'vue'

/**
 * 关联实体引用：列表行里能拿到的稳定标识 + 展示回退；细节由提供者在卡片打开后再补全。
 */
export interface RelatedEntityRef {
  /** 实体类型标识：宿主内置 user / dept / role，插件类型登记为 `${pluginCode}:${type}` */
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
 * 宿主内置类型在应用启动时注册；插件经 SDK `sdk.relatedEntity.register` 注册自己的实体类型，
 * 插件卸载时由宿主按插件码兜底注销，未注册类型由调度组件优雅降级为纯文本。
 */
export type RelatedEntityProvider = Component

interface RegisteredProvider {
  provider: RelatedEntityProvider
  owner: string
}

const providers = new Map<string, RegisteredProvider>()

/**
 * 注册实体预览卡提供者：同 owner 重复注册按覆盖处理（热重载语义），
 * 跨 owner 冲突拒绝并告警（内置类型不允许被插件遮蔽）。返回注销函数。
 */
export function registerRelatedEntity(type: string, provider: RelatedEntityProvider, owner = 'host'): () => void {
  const key = type.trim()
  if (!key || key.length > 120) {
    throw new Error('关联实体类型标识非法')
  }
  const existing = providers.get(key)
  if (existing && existing.owner !== owner) {
    console.warn(`[yudream] 关联实体类型「${key}」已由「${existing.owner}」注册，忽略「${owner}」的重复注册`)
    return () => {}
  }
  providers.set(key, { provider, owner })
  return () => {
    // 热重载时旧提供者的注销不能清掉新注册：仅在自己仍持有该类型时移除
    if (providers.get(key)?.provider === provider) {
      providers.delete(key)
    }
  }
}

/** 按 owner（宿主内置为 host，插件为插件码）批量注销，供插件卸载兜底回收 */
export function unregisterRelatedEntitiesByOwner(owner: string) {
  for (const [key, registered] of providers) {
    if (registered.owner === owner) {
      providers.delete(key)
    }
  }
}

export function resolveRelatedEntity(type?: string) {
  return type ? providers.get(type)?.provider : undefined
}

/** 已注册的实体类型，供调度/调试与插件冲突排查 */
export function registeredRelatedEntityTypes() {
  return [...providers.keys()]
}

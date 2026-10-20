import type { SiteDocument, StudioCommand, StudioContainer, StudioNode } from '../types/site-studio'
import { findStudioNode } from './site-studio'

/**
 * 把 AI 提交的命令批次转成人可读的变更清单，供“应用前预览”使用。
 * 只描述、不执行；节点名称按当前文档解析，找不到时按 ID 显示，不猜测。
 */
export interface StudioChangeLine {
  kind: 'text' | 'style' | 'insert' | 'remove' | 'move' | 'duplicate' | 'node' | 'theme' | 'page' | 'assets'
  label: string
  detail?: string
}

const MAX_TEXT = 40

function clip(value: string) {
  const single = value.replace(/\s+/g, ' ').trim()
  return single.length > MAX_TEXT ? `${single.slice(0, MAX_TEXT)}…` : single
}

export function describeStudioNode(node: StudioNode | undefined, fallbackId: string) {
  if (!node) {
    return `元素 ${fallbackId.slice(0, 8)}`
  }
  return node.text ? `${node.type}「${clip(node.text)}」` : `${node.type} · ${node.tag}`
}

function countNodes(nodes: StudioNode[]): number {
  return nodes.reduce((total, node) => total + 1 + countNodes(node.children), 0)
}

function describeTarget(document: SiteDocument, target: StudioContainer) {
  if ('pageId' in target) {
    return `页面「${document.pages.find(page => page.id === target.pageId)?.name ?? target.pageId}」`
  }
  if ('region' in target) {
    return target.region === 'header' ? '页头' : '页脚'
  }
  return describeStudioNode(findStudioNode(document, target.parentId), target.parentId)
}

export function describeStudioCommands(document: SiteDocument, command: StudioCommand): StudioChangeLine[] {
  const commands = command.type === 'batch' ? command.commands : [command]
  const node = (id: string) => describeStudioNode(findStudioNode(document, id), id)
  return commands.map((item): StudioChangeLine => {
    switch (item.type) {
      case 'set-text': {
        const before = findStudioNode(document, item.nodeId)?.text
        return { kind: 'text', label: `改写文本：${node(item.nodeId)}`, detail: `${before === undefined ? '' : `${clip(before)} → `}${clip(item.text)}` }
      }
      case 'set-style': {
        const entries = Object.entries(item.styles).map(([key, value]) => value === null ? `移除 ${key}` : `${key}: ${value}`)
        const breakpoint = item.breakpoint && item.breakpoint !== 'desktop' ? `（${item.breakpoint === 'tablet' ? '平板' : '手机'}）` : ''
        return { kind: 'style', label: `调整样式${breakpoint}：${node(item.nodeId)}`, detail: entries.join('；') }
      }
      case 'update-node':
        return { kind: 'node', label: `修改元素：${node(item.nodeId)}`, detail: Object.keys(item.patch).join('、') }
      case 'insert': {
        const size = countNodes([item.node])
        return { kind: 'insert', label: `插入 ${describeStudioNode(item.node, item.node.id)} 到 ${describeTarget(document, item.target)}`, detail: size > 1 ? `含 ${size} 个元素` : undefined }
      }
      case 'remove':
        return { kind: 'remove', label: `删除：${node(item.nodeId)}` }
      case 'move':
        return { kind: 'move', label: `移动：${node(item.nodeId)} → ${describeTarget(document, item.target)}` }
      case 'duplicate':
        return { kind: 'duplicate', label: `复制：${node(item.nodeId)}` }
      case 'set-theme': {
        const parts = [item.tokens ? `主题变量 ${Object.keys(item.tokens).length} 项` : '', item.header ? '页头' : '', item.footer ? '页脚' : ''].filter(Boolean)
        return { kind: 'theme', label: '修改主题', detail: parts.join('、') }
      }
      case 'add-page':
        return { kind: 'page', label: `新增页面「${item.page.name}」`, detail: item.page.path }
      case 'update-page':
        return { kind: 'page', label: `修改页面「${document.pages.find(page => page.id === item.pageId)?.name ?? item.pageId}」`, detail: Object.keys(item.patch).join('、') }
      case 'remove-page':
        return { kind: 'page', label: `删除页面「${document.pages.find(page => page.id === item.pageId)?.name ?? item.pageId}」` }
      case 'set-assets':
        return { kind: 'assets', label: '更新资源库', detail: `${document.assets.length} → ${item.assets.length} 项` }
      default:
        return { kind: 'node', label: '未知变更' }
    }
  })
}

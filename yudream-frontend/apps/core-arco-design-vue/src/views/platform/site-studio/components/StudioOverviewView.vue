<script setup lang="ts">
// 概览视图：无文档时引导从模板创建（只进本地工作台、不自动保存）；有文档时展示
// 页面/媒体/主题变量的真实概览（全部由当前文档计算，不伪造统计），并给「进入设计」。
// 模板是纯结构化 StudioNode + 主题变量，不依赖外部脚本、样式表或图片资源。
import type { SiteDocument, StudioNode } from '@/types/site-studio'
import { computed } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { createStudioDocument, createStudioNode } from '@/utils/site-studio'

defineOptions({ name: 'StudioOverviewView' })

const emit = defineEmits<{ startDesign: [] }>()

const store = useSiteStudioStore()
const toast = useYdToast()

// ===== 首次引导：模板构建 =====

/** 在 createStudioNode 基础上按需覆盖文本/属性/样式，保证 id 唯一与校验通过 */
function nodeOf(type: string, patch: Partial<Omit<StudioNode, 'id' | 'children'>> = {}, children: StudioNode[] = []): StudioNode {
  const node = createStudioNode(type)
  Object.assign(node, patch)
  if (children.length) {
    node.children = children
  }
  return node
}

function navLink(label: string, href: string): StudioNode {
  return nodeOf('link', { text: label, attributes: { href }, styles: { 'color': 'var(--muted)', 'text-decoration': 'none' } })
}

function navBar(brand: string, links: { label: string, href: string }[]): StudioNode[] {
  return [nodeOf('container', {
    tag: 'nav',
    styles: {
      'display': 'flex',
      'justify-content': 'space-between',
      'align-items': 'center',
      'flex-wrap': 'wrap',
      'gap': '12px',
      'padding': '14px 24px',
      'border-bottom': '1px solid var(--border)',
    },
  }, [
    nodeOf('text', { tag: 'span', text: brand, styles: { 'font-weight': '700', 'color': 'var(--foreground)' } }),
    nodeOf('container', { styles: { 'display': 'flex', 'gap': '20px', 'align-items': 'center' } }, links.map(item => navLink(item.label, item.href))),
  ])]
}

function portfolioDocument(): SiteDocument {
  const doc = createStudioDocument('我的作品集')
  doc.theme.tokens = {
    accent: '#4f46e5',
    background: '#ffffff',
    surface: '#f6f7fb',
    foreground: '#1b1d22',
    muted: '#6b7280',
    border: '#e5e7eb',
    radius: '14px',
    space: '24px',
  }
  doc.theme.header = navBar('我的作品集', [
    { label: '作品', href: '#works' },
    { label: '关于', href: '#about' },
    { label: '联系', href: '#contact' },
  ])
  doc.theme.footer = [nodeOf('text', {
    text: '© 2026 我的作品集 · 由网站工作台搭建',
    styles: {
      'color': 'var(--muted)',
      'font-size': '0.8rem',
      'text-align': 'center',
      'padding': '20px 16px',
      'border-top': '1px solid var(--border)',
    },
  })]

  function projectCard(title: string, description: string, tint: string): StudioNode {
    return nodeOf('container', {
      styles: {
        'background': 'var(--background)',
        'border': '1px solid var(--border)',
        'border-radius': 'var(--radius)',
        'overflow': 'hidden',
      },
    }, [
      nodeOf('container', { styles: { height: '140px', background: `linear-gradient(135deg, var(--accent) 0%, ${tint} 100%)` } }),
      nodeOf('heading', { tag: 'h3', text: title, styles: { 'color': 'var(--foreground)', 'font-size': '1.05rem', 'margin': '16px 16px 6px' } }),
      nodeOf('text', { tag: 'p', text: description, styles: { 'color': 'var(--muted)', 'margin': '0 16px 16px', 'line-height': '1.6' } }),
    ])
  }

  const hero = nodeOf('section', {
    styles: { background: 'var(--surface)', padding: 'calc(var(--space) * 3) var(--space)' },
  }, [
    nodeOf('heading', {
      tag: 'h1',
      text: '用作品讲述故事',
      styles: { 'color': 'var(--foreground)', 'font-size': '2.2rem', 'font-weight': '700', 'margin': '0 0 12px' },
    }),
    nodeOf('text', {
      tag: 'p',
      text: '这是网站工作台内置的作品集模板：整页由结构化节点与主题变量组成，不依赖任何外部脚本、样式或图片，可以直接替换成你的内容。',
      styles: { 'color': 'var(--muted)', 'max-width': '560px', 'margin': '0 0 24px', 'line-height': '1.7' },
    }),
    nodeOf('link', {
      text: '查看作品',
      attributes: { href: '#works' },
      styles: {
        'display': 'inline-block',
        'padding': '10px 22px',
        'border-radius': 'var(--radius)',
        'background': 'var(--accent)',
        'color': '#ffffff',
        'text-decoration': 'none',
        'font-weight': '600',
      },
    }),
  ])

  const works = nodeOf('section', {
    styles: { padding: 'calc(var(--space) * 3) var(--space)' },
  }, [
    nodeOf('heading', {
      text: '精选作品',
      attributes: { id: 'works' },
      styles: { color: 'var(--foreground)', margin: '0 0 20px' },
    }),
    nodeOf('container', {
      styles: { 'display': 'grid', 'grid-template-columns': 'repeat(3, 1fr)', 'gap': 'var(--space)' },
      responsive: { mobile: { 'grid-template-columns': '1fr' } },
    }, [
      projectCard('作品一', '项目简介：一句话说明这个作品做了什么、用了什么。', '#1e293b'),
      projectCard('作品二', '项目简介：一句话说明这个作品做了什么、用了什么。', '#334155'),
      projectCard('作品三', '项目简介：一句话说明这个作品做了什么、用了什么。', '#0f172a'),
    ]),
  ])

  const about = nodeOf('section', {
    styles: { 'padding': 'calc(var(--space) * 2) var(--space)', 'border-top': '1px solid var(--border)' },
  }, [
    nodeOf('heading', {
      text: '关于我',
      attributes: { id: 'about' },
      styles: { color: 'var(--foreground)', margin: '0 0 12px' },
    }),
    nodeOf('text', {
      tag: 'p',
      text: '在这里介绍你自己或团队：关注的方向、合作方式与最近的动态。',
      styles: { 'color': 'var(--muted)', 'max-width': '640px', 'line-height': '1.7' },
    }),
  ])

  const contact = nodeOf('section', {
    styles: { 'padding': 'calc(var(--space) * 2) var(--space)', 'border-top': '1px solid var(--border)' },
  }, [
    nodeOf('heading', {
      text: '联系',
      attributes: { id: 'contact' },
      styles: { color: 'var(--foreground)', margin: '0 0 12px' },
    }),
    nodeOf('text', {
      tag: 'p',
      text: '在这里留下你的联系方式（如站点留言表单、社交主页链接）。',
      styles: { 'color': 'var(--muted)', 'line-height': '1.7' },
    }),
  ])

  doc.pages[0]!.root = [hero, works, about, contact]
  return doc
}

function introDocument(): SiteDocument {
  const doc = createStudioDocument('介绍页站点')
  doc.theme.tokens = {
    accent: '#0f766e',
    background: '#ffffff',
    surface: '#f1f5f4',
    foreground: '#1c2b2a',
    muted: '#5f6f6d',
    border: '#e2e8e6',
    radius: '18px',
    space: '24px',
  }
  doc.theme.header = navBar('关于我们', [
    { label: '亮点', href: '#highlights' },
    { label: '联系', href: '#contact' },
  ])
  doc.theme.footer = [nodeOf('text', {
    text: '© 2026 介绍页站点 · 由网站工作台搭建',
    styles: {
      'color': 'var(--muted)',
      'font-size': '0.8rem',
      'text-align': 'center',
      'padding': '20px 16px',
      'border-top': '1px solid var(--border)',
    },
  })]

  function highlightCard(title: string, description: string): StudioNode {
    return nodeOf('container', {
      styles: {
        'background': 'var(--surface)',
        'border': '1px solid var(--border)',
        'border-radius': 'var(--radius)',
        'padding': '20px',
      },
    }, [
      nodeOf('heading', { tag: 'h3', text: title, styles: { color: 'var(--foreground)', margin: '0 0 8px' } }),
      nodeOf('text', { tag: 'p', text: description, styles: { 'color': 'var(--muted)', 'line-height': '1.6', 'margin': '0' } }),
    ])
  }

  const hero = nodeOf('section', {
    styles: { 'padding': 'calc(var(--space) * 4) var(--space)', 'text-align': 'center' },
  }, [
    nodeOf('heading', {
      tag: 'h1',
      text: '你好，欢迎了解我们',
      styles: { 'color': 'var(--foreground)', 'font-size': '2.2rem', 'font-weight': '700', 'margin': '0 0 14px' },
    }),
    nodeOf('text', {
      tag: 'p',
      text: '这是网站工作台内置的介绍页模板：页面全部由结构化节点与主题变量组成，不依赖外部资源，适合作为团队或产品的第一张名片。',
      styles: { 'color': 'var(--muted)', 'max-width': '640px', 'margin': '0 auto 24px', 'line-height': '1.7' },
    }),
    nodeOf('link', {
      text: '联系我们',
      attributes: { href: '#contact' },
      styles: {
        'display': 'inline-block',
        'padding': '10px 22px',
        'border-radius': 'var(--radius)',
        'background': 'var(--accent)',
        'color': '#ffffff',
        'text-decoration': 'none',
        'font-weight': '600',
      },
    }),
  ])

  const highlights = nodeOf('section', {
    styles: { padding: 'calc(var(--space) * 3) var(--space)' },
  }, [
    nodeOf('heading', {
      text: '我们的亮点',
      attributes: { id: 'highlights' },
      styles: { 'color': 'var(--foreground)', 'text-align': 'center', 'margin': '0 0 20px' },
    }),
    nodeOf('container', {
      styles: { 'display': 'grid', 'grid-template-columns': 'repeat(3, 1fr)', 'gap': 'var(--space)' },
      responsive: { mobile: { 'grid-template-columns': '1fr' } },
    }, [
      highlightCard('快速响应', '说明你的服务或产品在响应速度上的优势。'),
      highlightCard('专注质量', '说明团队如何保证交付质量与稳定性。'),
      highlightCard('长期陪伴', '说明后续维护、升级与支持的方式。'),
    ]),
  ])

  const contact = nodeOf('section', {
    styles: { 'padding': 'calc(var(--space) * 2) var(--space)', 'border-top': '1px solid var(--border)', 'text-align': 'center' },
  }, [
    nodeOf('heading', {
      text: '联系我们',
      attributes: { id: 'contact' },
      styles: { color: 'var(--foreground)', margin: '0 0 12px' },
    }),
    nodeOf('text', {
      tag: 'p',
      text: '在这里留下联系入口：站内留言、表单或公开主页链接均可。',
      styles: { 'color': 'var(--muted)', 'line-height': '1.7' },
    }),
  ])

  doc.pages[0]!.root = [hero, highlights, contact]
  return doc
}

interface TemplateOption {
  value: string
  label: string
  description: string
  icon: string
}

const templateOptions: TemplateOption[] = [
  { value: 'blank', label: '空白站点', description: '从零开始：一个空的首页，结构与内容全部自己搭建。', icon: 'morph:file' },
  { value: 'portfolio', label: '作品集', description: '导航 + 首屏横幅 + 精选作品卡片 + 页脚，适合展示个人或团队作品。', icon: 'morph:layout-template' },
  { value: 'intro', label: '介绍页', description: '居中横幅 + 亮点卡片 + 联系区块，适合作为团队或产品的第一张名片。', icon: 'morph:presentation' },
]

function createFromTemplate(value: string) {
  try {
    const doc = value === 'portfolio'
      ? portfolioDocument()
      : value === 'intro'
        ? introDocument()
        : createStudioDocument('我的站点')
    store.loadDocumentLocally(doc)
    toast.success('已在工作台创建站点草稿（仅本地，保存前不会写入服务端）')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '模板创建失败')
  }
}

// ===== 文档概览：全部真实统计，不伪造 =====

function countNodes(nodes: StudioNode[]): number {
  let count = 0
  const visit = (list: StudioNode[]) => list.forEach((item) => {
    count++
    visit(item.children)
  })
  visit(nodes)
  return count
}

const pageStats = computed(() => (store.doc?.pages || []).map(page => ({
  id: page.id,
  name: page.name,
  path: page.path,
  nodeCount: countNodes(page.root),
})))

const totalNodeCount = computed(() => {
  const doc = store.doc
  if (!doc) {
    return 0
  }
  return doc.pages.reduce((sum, page) => sum + countNodes(page.root), 0)
    + countNodes(doc.theme.header) + countNodes(doc.theme.footer)
})

const imageAssets = computed(() => (store.doc?.assets || []).filter(asset => asset.kind === 'image'))
const assetCount = computed(() => store.doc?.assets.length ?? 0)
const tokenEntries = computed(() => Object.entries(store.doc?.theme.tokens || {}))

/** 颜色形态的变量值给一块内容预览色块（类似图片缩略的内容预览，非界面装饰） */
function isColorValue(value: string) {
  return /^#|^rgb|^hsl|^oklch|^lab|^lch|^color-mix/.test(value.trim()) || value.includes('var(')
}

const published = computed(() => Boolean(store.state?.publishedReleaseId))

function openPage(pageId: string) {
  store.activePageId = pageId
  store.editRegion = 'page'
  store.selectedNodeId = ''
  emit('startDesign')
}
</script>

<template>
  <div class="h-full min-h-0 overflow-auto">
    <!-- 首文档引导：只有本地草稿，保存前不写服务端 -->
    <template v-if="!store.doc">
      <div class="mx-auto p-6 flex flex-col gap-6 max-w-3xl">
        <div class="flex flex-col gap-2">
          <h2 class="text-lg font-semibold m-0">
            创建你的第一个站点文档
          </h2>
          <p class="text-sm text-muted-foreground m-0">
            选择一个模板进入网站工作台。草稿只存在于当前工作台，确认内容后记得点右上角「保存草稿」写入服务端。
          </p>
        </div>
        <div class="gap-3 grid lg:grid-cols-3 sm:grid-cols-2">
          <YdAction
            v-for="option in templateOptions"
            :key="option.value"
            class="studio-template-card"
            :aria-label="`使用${option.label}模板创建`"
            @click="createFromTemplate(option.value)"
          >
            <YdIcon :name="option.icon" class="text-xl" />
            <strong>{{ option.label }}</strong>
            <span class="text-xs text-muted-foreground leading-relaxed">{{ option.description }}</span>
          </YdAction>
        </div>
        <YdAlert>
          模板内容全部由结构化节点与站点主题变量组成，不包含外部脚本、样式表或图片；创建后可随时在「主题」里修改变量。
        </YdAlert>
      </div>
    </template>

    <!-- 已有文档：真实概览 -->
    <template v-else>
      <div class="mx-auto p-4 flex flex-col gap-5 max-w-4xl sm:p-6">
        <div class="flex flex-wrap gap-3 items-center">
          <div class="flex-1 min-w-0">
            <h2 class="text-lg font-semibold m-0 truncate">
              {{ store.doc.name }}
            </h2>
            <p class="text-xs text-muted-foreground m-0">
              编辑主题：{{ store.themeCode }} · 草稿版本号：{{ store.state?.revision || '—' }}
            </p>
          </div>
          <span class="yd-status-tone" :data-tone="published ? 'success' : 'neutral'">
            {{ published ? '已有线上版本' : '尚未发布' }}
          </span>
          <YdButton v-auth="'platform:cms:edit'" variant="outline" @click="emit('startDesign')">
            <YdIcon name="morph:brush" /> 进入设计
          </YdButton>
        </div>

        <YdStats>
          <YdStat title="页面" :value="pageStats.length" description="真实页面数" />
          <YdStat title="内容节点" :value="totalNodeCount" description="页面与主题区域合计" />
          <YdStat title="媒体资源" :value="assetCount" description="文档资产库条目" />
          <YdStat title="主题变量" :value="tokenEntries.length" description="站点输出变量" />
        </YdStats>

        <section class="flex flex-col gap-2" aria-label="页面概览">
          <h3 class="text-xs text-muted-foreground font-medium m-0">
            页面
          </h3>
          <div class="gap-2 grid sm:grid-cols-2">
            <YdAction
              v-for="page in pageStats"
              :key="page.id"
              type="button"
              class="studio-overview-row"
              :aria-label="`在设计中打开页面${page.name}`"
              @click="openPage(page.id)"
            >
              <span class="flex flex-col gap-0.5 min-w-0">
                <strong class="truncate">{{ page.name }}</strong>
                <span class="text-xs text-muted-foreground truncate">{{ page.path }} · {{ page.nodeCount }} 个节点</span>
              </span>
              <YdIcon name="morph:chevron-right" class="text-muted-foreground shrink-0" />
            </YdAction>
          </div>
        </section>

        <section class="flex flex-col gap-2" aria-label="媒体资源概览">
          <h3 class="text-xs text-muted-foreground font-medium m-0">
            媒体资源
          </h3>
          <template v-if="assetCount > 0">
            <div v-if="imageAssets.length" class="flex flex-wrap gap-2">
              <span v-for="asset in imageAssets.slice(0, 8)" :key="asset.id" class="studio-media-thumb" :title="asset.name">
                <img
                  :src="toBackendAssetUrl(asset.url)"
                  :alt="asset.name"
                  class="size-full pointer-events-none object-cover"
                  loading="lazy"
                >
              </span>
              <span v-if="imageAssets.length > 8" class="text-xs text-muted-foreground self-center">
                仅展示前 8 张图片，共 {{ imageAssets.length }} 张
              </span>
            </div>
            <p class="text-xs text-muted-foreground m-0">
              共 {{ assetCount }} 项资源（图片 {{ imageAssets.length }} 项），可在「资源」页上传与管理。
            </p>
          </template>
          <YdEmpty v-else description="还没有上传任何媒体资源；可在「资源」页上传或内嵌小资源" />
        </section>

        <section class="flex flex-col gap-2" aria-label="主题变量概览">
          <h3 class="text-xs text-muted-foreground font-medium m-0">
            主题变量
          </h3>
          <template v-if="tokenEntries.length">
            <ul class="m-0 p-0 list-none flex flex-wrap gap-2">
              <li
                v-for="[key, value] in tokenEntries"
                :key="key"
                class="text-xs px-2.5 py-1 border rounded-full flex gap-1.5 items-center"
              >
                <span
                  v-if="isColorValue(value)"
                  class="border rounded-full size-3.5 inline-block"
                  :style="{ background: value }"
                  aria-hidden="true"
                />
                <code class="font-mono">{{ key }}</code>
                <span class="text-muted-foreground font-mono max-w-40 truncate">{{ value }}</span>
              </li>
            </ul>
          </template>
          <YdEmpty v-else description="尚未定义站点主题变量；可在「主题」页添加 accent、background 等" />
        </section>

        <section class="flex flex-col gap-2" aria-label="发布状态">
          <h3 class="text-xs text-muted-foreground font-medium m-0">
            发布与公开站
          </h3>
          <YdAlert v-if="published">
            <div class="flex flex-wrap gap-2 items-center">
              <span>该主题已有发布版本。</span>
              <RouterLink to="/site" class="studio-inline-link">
                打开公开站 /site
              </RouterLink>
            </div>
            <p class="text-xs text-muted-foreground m-0 mt-1">
              公开站 /site 只在「{{ store.themeCode }}」是当前激活的公开站主题时展示发布内容；主题激活请到「主题中心」操作，当前编辑的主题未激活时发布内容暂不可见。停止发布请到「发布版本」页操作。
            </p>
          </YdAlert>
          <YdAlert v-else>
            <span>当前草稿尚未发布。完成编辑后可在右上角「发布」，发布后公开站 /site 才会展示该主题的站点文档。</span>
          </YdAlert>
          <p v-if="totalNodeCount === 0" class="text-xs text-muted-foreground m-0">
            站点还没有内容节点，点「进入设计」开始搭建页面。
          </p>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.studio-template-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 16px;
  text-align: left;
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius, 10px);
  background: oklch(var(--card));
  color: inherit;
}
.studio-template-card:hover {
  border-color: color-mix(in oklab, oklch(var(--foreground)) 25%, oklch(var(--border)));
}
.studio-overview-row {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  text-align: left;
  border: 1px solid oklch(var(--border));
  border-radius: var(--yd-control-radius, 10px);
  background: oklch(var(--card));
  color: inherit;
}
.studio-overview-row:hover {
  background: oklch(var(--muted) / 0.5);
}
.studio-media-thumb {
  display: block;
  width: 72px;
  height: 72px;
  overflow: hidden;
  border: 1px solid oklch(var(--border));
  border-radius: calc(var(--yd-control-radius, 10px) * 0.75);
  background: oklch(var(--muted) / 0.35);
}
.studio-inline-link {
  color: var(--color-text-2);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.studio-inline-link:hover {
  color: var(--color-text-1);
}
</style>

<script setup lang="ts">
import { getPageBuilder, usePageBuilderModal } from '@myissue/vue-website-page-builder'

interface CmsBlock {
  title: string
  group: string
  description: string
  html: string
}

const toast = useFaToast()
const { closeAddComponentModal } = usePageBuilderModal()
const keyword = ref('')
const activeGroup = ref('全部')
const inserting = ref(false)

const blocks: CmsBlock[] = [
  {
    title: '沉浸式首页首屏',
    group: '首页',
    description: '品牌标题、动态用户欢迎语、双按钮行动入口。',
    html: `<section data-component-title="沉浸式首页首屏" class="yb-hero yb-hero--immersive" style="padding: 88px 48px; border-radius: 24px; background: linear-gradient(135deg, #0f172a, #14532d); color: #fff;">
  <p style="margin: 0 0 18px; color: rgba(255,255,255,.72); font-weight: 700;">{{site.name}} · {{auth.welcome}}</p>
  <h1 style="max-width: 820px; margin: 0; font-size: 64px; line-height: .98;">把内容、数据和业务能力组合成真正可发布的网站</h1>
  <p style="max-width: 680px; margin: 22px 0 0; color: rgba(255,255,255,.82); font-size: 18px;">使用可视化区块、媒体库和动态变量搭建页面。当前用户：{{user.nickname}}</p>
  <div style="display:flex; gap:12px; flex-wrap:wrap; margin-top: 32px;">
    <a href="/site" style="padding: 12px 18px; border-radius: 10px; background: #fff; color: #0f172a; font-weight: 800; text-decoration:none;">查看站点</a>
    <a href="/login" data-visible-when="guest" style="padding: 12px 18px; border-radius: 10px; border: 1px solid rgba(255,255,255,.35); color:#fff; font-weight: 800; text-decoration:none;">登录体验</a>
  </div>
</section>`,
  },
  {
    title: 'WordPress式文章流',
    group: '内容',
    description: '三列文章卡片，可替换为真实内容列表。',
    html: `<section data-component-title="WordPress式文章流" class="yb-post-grid" style="padding: 56px 0;">
  <div style="display:grid; gap: 10px; margin-bottom: 24px;">
    <span style="color:#0f766e; font-weight:800;">Latest Posts</span>
    <h2 style="margin:0; font-size: 40px;">精选内容</h2>
    <p style="margin:0; color:#64748b;">用页面、分类和标签组织内容，像 WordPress 一样搭建栏目。</p>
  </div>
  <div style="display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px;">
    <article style="padding:22px; border:1px solid #e5e7eb; border-radius:16px; background:#fff;"><small>公告</small><h3>CMS 发布流程</h3><p>保存草稿、发布上线、公开渲染。</p></article>
    <article style="padding:22px; border:1px solid #e5e7eb; border-radius:16px; background:#fff;"><small>教程</small><h3>媒体库和区块</h3><p>上传素材后直接插入页面。</p></article>
    <article style="padding:22px; border:1px solid #e5e7eb; border-radius:16px; background:#fff;"><small>动态</small><h3>变量渲染</h3><p>支持用户、登录态、站点变量。</p></article>
  </div>
</section>`,
  },
  {
    title: '功能矩阵',
    group: '营销',
    description: '适合产品介绍和平台能力展示。',
    html: `<section data-component-title="功能矩阵" class="yb-feature-matrix" style="padding: 56px 0;">
  <h2 style="font-size: 38px; margin: 0 0 18px;">平台能力</h2>
  <div style="display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px;">
    <div style="padding:20px; border-radius:14px; background:#ecfeff;"><strong>可视化构建</strong><p>低代码组合页面与首页。</p></div>
    <div style="padding:20px; border-radius:14px; background:#f0fdf4;"><strong>动态变量</strong><p>按登录态和用户信息渲染。</p></div>
    <div style="padding:20px; border-radius:14px; background:#fff7ed;"><strong>S3 媒体库</strong><p>使用 RustFS 管理素材。</p></div>
  </div>
</section>`,
  },
  {
    title: '登录态行动区',
    group: '动态',
    description: '同时包含游客和已登录用户可见内容。',
    html: `<section data-component-title="登录态行动区" class="yb-auth-cta" style="display:grid; gap:16px; padding: 38px; border-radius:18px; background:#111827; color:#fff;">
  <div data-visible-when="guest"><h2>加入 YuDream</h2><p>登录后可看到你的头像、昵称和个人入口。</p><a href="/login" style="color:#fff; font-weight:800;">立即登录</a></div>
  <div data-visible-when="logged-in"><h2>欢迎回来，{{user.nickname}}</h2><p>你的账号 {{user.username}} 已登录，可以继续管理内容。</p><img src="{{user.avatar}}" alt="{{user.nickname}}" style="width:56px; height:56px; border-radius:50%; object-fit:cover;"></div>
</section>`,
  },
  {
    title: '导航头像列表',
    group: '动态',
    description: '用于展示导航栏用户、协作者或推荐成员。',
    html: `<section data-component-title="导航头像列表" class="yb-avatar-strip" style="padding: 28px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
  <h2 style="margin-top:0;">在线协作入口</h2>
  <div data-yb-repeat="navUsers" style="display:flex; gap:10px; flex-wrap:wrap;">
    <a href="{{item.url}}" style="display:flex; align-items:center; gap:8px; padding:8px 12px; border-radius:999px; background:#f8fafc; color:#0f172a; text-decoration:none;">
      <img src="{{item.avatar}}" alt="{{item.name}}" style="width:32px; height:32px; border-radius:50%; object-fit:cover;">
      <span>{{item.name}}</span>
    </a>
  </div>
</section>`,
  },
  {
    title: '价格表',
    group: '营销',
    description: '三档套餐或服务报价。',
    html: `<section data-component-title="价格表" class="yb-pricing" style="padding:56px 0;">
  <h2 style="font-size:38px; margin:0 0 20px;">选择适合的方案</h2>
  <div style="display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap:16px;">
    <article style="padding:24px; border:1px solid #e5e7eb; border-radius:18px;"><h3>Starter</h3><strong style="font-size:34px;">免费</strong><p>适合内容试运行。</p></article>
    <article style="padding:24px; border:2px solid #0f766e; border-radius:18px;"><h3>Pro</h3><strong style="font-size:34px;">¥99</strong><p>适合业务站点。</p></article>
    <article style="padding:24px; border:1px solid #e5e7eb; border-radius:18px;"><h3>Team</h3><strong style="font-size:34px;">定制</strong><p>适合团队协作。</p></article>
  </div>
</section>`,
  },
  {
    title: 'FAQ 折叠区',
    group: '内容',
    description: '常见问题与答案。',
    html: `<section data-component-title="FAQ 折叠区" class="yb-faq" style="padding: 48px 0;">
  <h2 style="font-size:36px;">常见问题</h2>
  <details open style="padding:16px; border-bottom:1px solid #e5e7eb;"><summary>CMS 能发布首页吗？</summary><p>可以，首页使用 homeHtml 保存构建器完整 HTML。</p></details>
  <details style="padding:16px; border-bottom:1px solid #e5e7eb;"><summary>能使用 HTML 吗？</summary><p>可以，HTML 模式支持动态变量。</p></details>
  <details style="padding:16px; border-bottom:1px solid #e5e7eb;"><summary>媒体库在哪里？</summary><p>在构建器选择图片时直接打开 CMS 媒体库。</p></details>
</section>`,
  },
  {
    title: '变量速查卡',
    group: '动态',
    description: '展示页面可用变量，方便编辑者复制。',
    html: `<section data-component-title="变量速查卡" class="yb-variable-card" style="padding:24px; border-radius:16px; background:#f8fafc; border:1px solid #e5e7eb;">
  <h2>动态变量</h2>
  <p>站点：{{site.name}}，当前路径：{{route.path}}</p>
  <p>登录状态：{{auth.isLoggedIn}}，欢迎语：{{auth.welcome}}</p>
  <p>用户：{{user.username}} / {{user.nickname}}</p>
</section>`,
  },
]

const groups = computed(() => ['全部', ...Array.from(new Set(blocks.map(item => item.group)))])
const filteredBlocks = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return blocks.filter((item) => {
    const matchGroup = activeGroup.value === '全部' || item.group === activeGroup.value
    const matchKeyword = !key || `${item.title} ${item.group} ${item.description}`.toLowerCase().includes(key)
    return matchGroup && matchKeyword
  })
})

async function insertBlock(block: CmsBlock) {
  inserting.value = true
  try {
    await getPageBuilder().addComponent({
      id: null,
      title: block.title,
      html_code: block.html,
    })
    toast.success(`已插入「${block.title}」`)
    closeAddComponentModal()
  }
  finally {
    inserting.value = false
  }
}
</script>

<template>
  <section class="cms-builder-components">
    <div class="components-toolbar">
      <FaInput v-model="keyword" clearable placeholder="搜索区块、模板、变量" />
      <div class="group-tabs">
        <button v-for="group in groups" :key="group" type="button" :class="{ active: activeGroup === group }" @click="activeGroup = group">
          {{ group }}
        </button>
      </div>
    </div>

    <div class="block-grid">
      <button v-for="block in filteredBlocks" :key="block.title" type="button" class="block-card" :disabled="inserting" @click="insertBlock(block)">
        <span>{{ block.group }}</span>
        <strong>{{ block.title }}</strong>
        <small>{{ block.description }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.cms-builder-components {
  display: grid;
  gap: 14px;
}

.components-toolbar {
  display: grid;
  gap: 10px;
}

.group-tabs {
  display: flex;
  gap: 8px;
  overflow: auto;
  padding-bottom: 4px;
}

.group-tabs button {
  flex: 0 0 auto;
  padding: 8px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
}

.group-tabs button.active {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.block-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  max-height: 640px;
  overflow: auto;
}

.block-card {
  display: grid;
  gap: 8px;
  min-height: 146px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
  text-align: left;
}

.block-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.block-card span {
  color: rgb(var(--primary-6));
  font-size: 12px;
  font-weight: 800;
}

.block-card strong {
  font-size: 17px;
}

.block-card small {
  color: var(--color-text-3);
  line-height: 1.5;
}

@media (max-width: 920px) {
  .block-grid {
    grid-template-columns: 1fr;
  }
}
</style>

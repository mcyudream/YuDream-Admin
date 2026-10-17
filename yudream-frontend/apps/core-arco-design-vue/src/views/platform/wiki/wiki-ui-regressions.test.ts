/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import { wikiManageAreaOf, wikiManageAreaPanels, wikiManageAreas, wikiManagePanels } from './wiki-utils'

// 评审回归：工作台外壳、工作区分栏与多标签、治理分区导航、llm-gen、设置版式与图片可见性。
// 这些断言读源码做结构性回归，配合 vue-tsc/eslint 覆盖无法在单测里渲染的布局与导航。

function wikiFile(relativePath: string) {
  return readFileSync(join(import.meta.dirname, relativePath), 'utf8')
}

test('directories can be renamed from the toolbar title input', () => {
  const author = wikiFile('components/WikiAuthorEditor.vue')
  // 目录标题不再禁用（saveNode 一直支持提交目录标题）
  assert.doesNotMatch(author, /:disabled="node\?\.nodeType === 'DIRECTORY'"/)
  assert.doesNotMatch(author, /:disabled="node\.nodeType === 'DIRECTORY'"/)
  // 标题只保留工具条一处输入（窄屏不再复制第二份，工作区靠标签与面板切换承载多页面）
  assert.equal(author.match(/v-model="editorFields\.title"/g)?.length, 1)
  assert.match(author, /:disabled="!node"[\s\S]{0,200}aria-label="页面标题"/)
  // 保存分支仍然提交目录标题
  assert.match(author, /title: fields\.title/)
})

test('workbench shell fills the area with its own background and never remounts on internal navigation', () => {
  const index = wikiFile('index.vue')
  // 全部工作面共享一个铺满的外壳：绝对定位 + 自带背景，不再依赖宿主 auto 高度的百分比链
  assert.match(index, /\.wiki-workbench \{[\s\S]*?position: absolute;[\s\S]*?inset: 0;/)
  assert.match(index, /\.wiki-workbench \{[\s\S]*?background: var\(--color-bg-1\);/)
  // 入口页自己滚动（外壳 overflow: hidden），不会把长内容裁掉
  assert.match(index, /\.wiki-workbench__entry \{[\s\S]*?overflow: auto;/)
  // 页面内导航用 replaceState 静默同步，绝不 router.push(...)（宿主按 route.fullPath 重挂载整页）
  assert.doesNotMatch(index, /router\.push\(/)
  assert.doesNotMatch(index, /useRouter/)
  assert.match(index, /window\.history\.replaceState/)
  // 工作面状态是本地 ref，query 只在真实导航时采用
  assert.match(index, /const view = ref<WikiWorkbenchView>/)
  assert.match(index, /const activePanel = ref<string>/)
})

test('editor is a workspace: activity rail, multi-document tabs, draggable panes and a status bar', () => {
  const author = wikiFile('components/WikiAuthorEditor.vue')
  // 外壳来自组件库，分栏宽度由 storage-key 记忆
  assert.match(author, /<YdWorkspaceLayout/)
  assert.match(author, /storage-key="wiki-author"/)
  assert.match(author, /v-model:rail="rail"/)
  assert.match(author, /v-model:sidebar-open="sidebarOpen"/)
  assert.match(author, /v-model:aside-open="asideOpen"/)
  assert.match(author, /:rail-items="railItems"/)
  // 多文件标签栏 + 打开/关闭都经文档管理器
  assert.match(author, /<YdWorkspaceTabs/)
  assert.match(author, /createWikiDocuments\(\)/)
  assert.match(author, /function closeDocument\(key: string\)/)
  assert.match(author, /@close="closeDocument"/)
  // 关闭未保存标签要先确认，不能静默丢草稿
  assert.match(author, /关闭未保存的标签？/)
  assert.match(author, /离开未保存的页面？/)
  // 状态栏给出路径、字数与保存态
  assert.match(author, /const statusItems = computed/)
  assert.match(author, /字`, tone: 'muted'/)
  assert.match(author, /已保存/)
  // 窄屏由外壳的面板切换器承载，不再维护第二套移动端模板
  assert.match(author, /v-model:pane="pane"/)
  assert.doesNotMatch(author, /v-else-if="isMobile"/)
  assert.doesNotMatch(author, /mobilePane/)
})

test('conflict resolution is reachable in every viewport', () => {
  const author = wikiFile('components/WikiAuthorEditor.vue')
  // 正文流里的冲突说明与重新载入（任何视口都可见，窄屏也在主面板内）
  assert.match(author, /v-if="conflict" class="author-conflict"[\s\S]{0,400}重新载入/)
  assert.match(author, /@click="reloadRemote"/)
  // 移动端底部固定栏同样提供重新载入
  const savebar = author.slice(author.indexOf('<YdFixedBar'))
  assert.match(savebar, /conflict[\s\S]{0,300}重新载入/)
  assert.match(savebar, /@click="reloadRemote"/)
  // 定位评论引用时窄屏先把主面板切回来
  assert.match(author, /pane\.value = 'main'/)
})

test('llm-gen is a real first-level directory, not only a grouped view', () => {
  const panel = wikiFile('components/WikiGeneratedPanel.vue')
  // 真实一级目录：根节点匹配 slug/title
  assert.match(panel, /store\.tree\.value\.find\(item =>/)
  assert.match(panel, /item\.slug === 'llm-gen' \|\| item\.title === 'llm-gen'/)
  // 创建的是 DIRECTORY 节点（无父节点 = 一级）
  assert.match(panel, /nodeType: 'DIRECTORY'/)
  assert.match(panel, /title: 'llm-gen'/)
  assert.match(panel, /slug: 'llm-gen'/)
  assert.match(panel, /parentId: undefined/)
  // 页面可以真正移入该目录
  assert.match(panel, /moveWikiNode\(page\.id, dir\.id\)/)
  assert.match(panel, /function moveAllInto\(\)/)
  // 文案明确分组视图 ≠ 目录归属
  assert.match(panel, /分组只是阅读视图，不代表页面已经在 llm-gen 目录下/)
  // 移动接口通过 Wiki 目录内的本地封装调用，且契约是核对过的而不是猜的
  const ops = wikiFile('wiki-node-ops.ts')
  assert.match(ops, /api\/platform\/wiki\/nodes\/\$\{id\}\/move/)
  assert.match(ops, /WikiNodeMoveRequest \{ String parentId \}/)
  assert.match(ops, /POST \/api\/platform\/wiki\/nodes\/\{id\}\/move/)
  assert.match(ops, /platform:wiki:edit/)
  // 服务端 Long.valueOf(parentId)：必须传数字字符串，禁止 Number(id)
  assert.match(ops, /必须传数字字符串/)
})

test('entry cards carry density: usage, update time and actionable state', () => {
  const entry = wikiFile('components/WikiEntryView.vue')
  // 卡片信息密度：用量（页面/目录）、最近更新、需要处理的状态都直说，不靠封面撑场面
  assert.match(entry, /function usageText\(item: WikiSpace\): string/)
  assert.match(entry, /<YdTimeAgo :date="updatedAtOf\(item\)" \/>/)
  assert.match(entry, /未发布 \{\{ unpublishedCountOf\(item\) \}\}/)
  assert.match(entry, /摄入失败 \{\{ failedIngestCountOf\(item\) \}\}/)
  // 聚合字段缺失时不伪造 0：计数读取必须区分 number 与 undefined
  assert.match(entry, /typeof item\.pageCount === 'number' \? item\.pageCount : null/)
  // 封面弱化：字母降为 28px 小方块，封面是窄横幅；未设置封面用中性点阵而不是名称哈希渐变
  assert.match(entry, /\.entry-card__cover \{[\s\S]{0,120}height: 104px;/)
  assert.match(entry, /\.entry-card__initial \{[\s\S]{0,200}width: 28px;/)
  assert.match(entry, /entry-card__cover-art--empty/)
  // 少数据时网格居中，并提供虚线新建引导卡
  assert.match(entry, /\.entry__grid \{[\s\S]{0,200}justify-content: center;/)
  assert.match(entry, /class="entry-create"/)
  // 工具栏整合：搜索 / 筛选 / 视图切换同一条横栏
  assert.match(entry, /class="entry__toolbar"[\s\S]{0,300}aria-label="搜索知识库"[\s\S]{0,300}aria-label="公开状态筛选"[\s\S]{0,300}aria-label="展示方式"/)
})

test('author toolbar keeps the save state next to the title and promotes the executable action', () => {
  const author = wikiFile('components/WikiAuthorEditor.vue')
  // 状态紧跟标题：标题输入与保存态同组
  assert.match(author, /author-bar__title[\s\S]{0,700}wiki-author__status/)
  // 主按钮随当前可执行动作切换：有改动时发布按钮即「保存并发布」（publish 内部先保存再发布），保存降为次要动作
  assert.match(author, /const publishLabel = computed\(\(\) => dirty\.value/)
  assert.match(author, /'保存并发布'/)
  assert.match(author, /:variant="saveButtonVariant"/)
  // 预览面与编辑面区分：浅灰地面 + 白色文档卡片
  assert.match(author, /\.author-body__preview \{[\s\S]{0,120}background: var\(--color-fill-1\)/)
  assert.match(author, /\.author-markdown \{[\s\S]{0,200}background: var\(--color-bg-1\)/)
  // 工具栏按用途分组（组件库的分组分隔符）
  assert.match(author, /'bold',\s*'italic',\s*'-',/)
  assert.match(author, /'revoke',\s*'next',\s*'=',/)
  // 目录树类型标签：淡色圆点 + 灰字，不再是灰底文字块
  assert.match(author, /\.author-node__type-dot \{[\s\S]{0,120}border-radius: 50%;/)
  // 属性面板按 基础信息 / 标签与关联 / 来源 分组折叠
  assert.match(author, /<YdCollapsible v-model="asideGroups\.base">/)
  assert.match(author, /<YdCollapsible v-model="asideGroups\.relations">/)
  assert.match(author, /<YdCollapsible v-model="asideGroups\.sources">/)
  // 外观回到宿主主题令牌：玻璃态与硬编码品牌色不得回流
  assert.doesNotMatch(author, /backdrop-filter/)
  assert.doesNotMatch(author, /#4f46e5/)
})

test('entering a workspace always resolves the knowledge base into state, not only into the URL', () => {
  const index = wikiFile('index.vue')
  // 页面内导航要把 spaceId 收敛进状态：只写地址栏会让「列表点卡片进入」停在「未选择」、目录树空白
  assert.match(index, /if \('spaceId' in patch \|\| 'view' in patch\) \{\s*applyRoute\(\)/)
  // 进入工作面时状态优先、地址栏兜底；不允许把知识库从工作面上抹掉（治理区曾因此显示「暂无知识库」）
  assert.match(index, /function effectiveSpaceId\(\): string \{\s*return spaceId\.value \|\| urlQuery\.value\.spaceId \|\| ''/)
  assert.match(index, /spaceId: target\?\.id \|\| effectiveSpaceId\(\) \|\| undefined/)
  assert.match(index, /const nextSpaceId = target\?\.space\?\.id \|\| effectiveSpaceId\(\)/)
  // 深链补正 spaceId 时直接写地址栏：与 patchQuery 互相回调会递归
  assert.match(index, /const patched = mergedQuery\(\{ spaceId: nextSpaceId \}\)/)
  assert.doesNotMatch(index, /patchQuery\(\{ spaceId: nextSpaceId \}\)/)
})

test('inline images stay private and the public rendering gap is stated, not claimed solved', () => {
  const images = wikiFile('wiki-images.ts')
  // 上传固定私有，不做空间可见性推导
  assert.match(images, /始终按私有文件保存/)
  assert.doesNotMatch(images, /publicReadEnabled/)
  // 未决契约在代码里显式记录（代理 / 提升两种方案）
  assert.match(images, /nodes\/\{nodeId\}\/images\/\{fileId\}/)
  assert.match(images, /已知未决项/)
  // 作者界面提示公开页仍未决，不误导
  const author = wikiFile('components/WikiAuthorEditor.vue')
  assert.match(author, /wikiImageUploadHint\(\)/)
})

test('settings uses YdSettingsLayout with header save, no fixed bottom bar', () => {
  const settings = wikiFile('components/WikiSettingsPanel.vue')
  assert.match(settings, /<YdSettingsLayout/)
  assert.match(settings, /<YdSettingsSection/)
  assert.match(settings, /surface/)
  assert.match(settings, /scroll/)
  assert.match(settings, /<template #header>/)
  assert.match(settings, /保存设置/)
  assert.doesNotMatch(settings, /YdFixedBar/)
  // 滚动模式需要的有界外壳
  assert.match(settings, /\.settings-page-shell \{[\s\S]*?height: 100%;/)
})

test('settings tab is one full-bleed white page instead of a white card framed by the gray stage', () => {
  const manage = wikiFile('components/WikiManageView.vue')
  const settings = wikiFile('components/WikiSettingsPanel.vue')
  // 舞台底色随活动分区切换：只有设置分区换成白底，其余分区仍是灰底治理舞台
  assert.match(manage, /const stageClass = computed\(\(\) => activeArea\.value\.key === 'settings'/)
  assert.match(manage, /'manage__content manage__content--sheet'/)
  assert.match(manage, /\.manage__content--sheet\) \{\s*background: var\(--color-bg-1\);/)
  // 白底要铺到舞台四边：区域与面板拉满舞台，面板高度不再由内容决定（否则白卡下方露出灰条）
  assert.match(manage, /\.manage__content--sheet\) \.manage__area,\s*\.manage__tabs :deep\(\.manage__content--sheet\) \.set-panel \{[\s\S]{0,80}?flex: 1;[\s\S]{0,40}?min-height: 0;/)
  // 设置面板自身也铺白底，白底与白卡之间不再夹一层灰
  assert.match(settings, /\.set-panel \{[\s\S]{0,120}?background: var\(--color-bg-1\);/)
  assert.doesNotMatch(settings, /\.set-panel \{[\s\S]{0,120}?background: var\(--color-fill-1\);/)
  // 非设置分区的灰底舞台规则必须留着（白底只属于设置）
  assert.match(manage, /\.manage__tabs :deep\(\.manage__content\) \{[\s\S]{0,220}?background: var\(--color-fill-1\);/)
})

test('manage surface is one top tab bar: area tabs, full-width dynamic content', () => {
  const manage = wikiFile('components/WikiManageView.vue')
  assert.equal(wikiManageAreas.length, 6)
  // 每个分区恰好属于一个区域，且区域首个分区必须是已注册分区
  const registered = new Set(wikiManagePanels.map(panel => panel.key))
  const seen = new Set<string>()
  for (const area of wikiManageAreas) {
    assert.ok(area.panels.length > 0, `${area.key} 区域必须有分区`)
    for (const key of area.panels) {
      assert.ok(registered.has(key), `${area.key} 引用了未注册分区 ${key}`)
      assert.ok(!seen.has(key), `分区 ${key} 被多个区域引用`)
      seen.add(key)
    }
    assert.equal(wikiManageAreaOf(area.panels[0]).key, area.key)
    assert.equal(wikiManageAreaPanels(area.key).length, area.panels.length)
  }
  assert.equal(seen.size, registered.size, '所有分区都要落在某个区域下')
  // 区域导航放顶部横向 Tab（治理面板以表格与统计为主，不再把内容挤进居中窄列）；不引入左侧设置版式与自绘 <nav>
  assert.match(manage, /<YdTabs/)
  assert.match(manage, /:list="areaList"/)
  assert.match(manage, /:content-class="stageClass"/)
  assert.doesNotMatch(manage, /<YdSettingsLayout/)
  assert.doesNotMatch(manage, /<nav class="manage__nav"/)
  assert.doesNotMatch(manage, /manage__mobile-nav/)
  // 区域插槽显式写全，不用 v-for + 动态插槽名（该组合会让组件库模板编译器崩溃）
  assert.doesNotMatch(manage, /#\[area\.key\]/)
  assert.equal(manage.match(/<WikiManageArea/g)?.length, 6)
  for (const area of wikiManageAreas) {
    assert.match(manage, new RegExp(`area-key="${area.key}"`), `${area.key} 区域要有对应内容插槽`)
  }
  // 离开未保存的设置要确认，不能静默丢改动
  assert.match(manage, /areaInstances\.get\('settings'\)\?\.settingsDirty\(\)/)
  assert.match(manage, /离开未保存的设置？/)
  // 区域内容：多分区用横向子标签，且子标签行允许换行（不再被头部 overflow 硬裁）
  const area = wikiFile('components/WikiManageArea.vue')
  assert.match(area, /function areaTabs|const areaTabs = computed/)
  assert.match(area, /<YdSegmented[\s\S]{0,260}v-model="activePanelKey"/)
  assert.match(area, /\.manage__subhead \{[\s\S]*?flex-wrap: wrap;/)
  assert.match(area, /settingsDirty: \(\) => Boolean\(settingsRef\.value\?\.dirty\)/)
})

test('overview leads with what needs attention and keeps statistics as the main figure', () => {
  const overview = wikiFile('components/WikiOverviewPanel.vue')
  // 失败任务置顶（左侧色条 + 直达摄入队列），不混在统计里
  assert.match(overview, /overview-alert[\s\S]{0,600}打开摄入队列/)
  assert.match(overview, /border-left: 3px solid rgb\(var\(--danger-6\)\)/)
  // 四个统计仍是主视觉，未发布需要处理时用警告色，不造假环比数据
  assert.match(overview, /overview__stats[\s\S]{0,400}repeat\(4, minmax\(0, 1fr\)\)/)
  assert.match(overview, /overview__value--attention[\s\S]{0,120}rgb\(var\(--warning-6\)\)/)
  // 待接入能力是说明性内容：折叠收起，不占概览主视线
  assert.match(overview, /<YdCollapsible v-model="pendingOpen">/)
  assert.doesNotMatch(overview, /const pendingOpen = ref\(true\)/)
  // 双栏版面放宽到宽屏上限，不再压进居中窄列
  assert.match(overview, /grid-template-columns: minmax\(0, 1\.1fr\) minmax\(0, 1fr\)/)
  assert.match(overview, /max-width: 1280px/)
})

test('collaboration area is real workbenches: space ACL members and comment review, no placeholder panels', () => {
  const utils = wikiFile('wiki-utils.ts')
  // 协作区两个分区是真实面板；「能力待接入」占位模式已随契约落地退役
  assert.match(utils, /key: 'members', label: '成员与权限'/)
  assert.match(utils, /key: 'comments', label: '评论'/)
  // 退役的是「能力待接入」占位模型本身（WikiUnavailableCapability + 面板的 unavailable 字段）；
  // 不再对 unavailable 这个词做全局禁止——评论 AI 判定的三态里它是合法的线协议值
  assert.doesNotMatch(utils, /WikiUnavailableCapability/)

  const area = wikiFile('components/WikiManageArea.vue')
  assert.match(area, /members: WikiMembersPanel/)
  assert.match(area, /comments: WikiCommentsPanel/)
  assert.doesNotMatch(area, /UnavailablePanel/)

  const api = wikiFile('../../../api/modules/platform-wiki.ts')
  // 端点与后端 WikiSpaceMemberController / WikiCommentManageController 一一对应
  assert.match(api, /spaces\/\$\{spaceId\}\/members/)
  assert.match(api, /members\/\$\{memberId\}/)
  assert.match(api, /acl\/restricted/)
  assert.match(api, /comments\/\$\{commentId\}\/\$\{action\}/)
  assert.match(api, /expectedVersion/)

  const members = wikiFile('components/WikiMembersPanel.vue')
  // 调整授权必须回传 CAS 版本，权限动作语义包含关系要讲清楚
  assert.match(members, /expectedVersion: member\.version/)
  assert.match(members, /管理 &gt; 编辑 &gt; 评论 &gt; 读取/)
  assert.match(members, /platform:wiki:member:manage/)
  assert.match(members, /enableWikiRestrictedAcl/)
  // LEGACY 下成员表不生效必须在界面上直说，不能让人以为勾了就生效
  assert.match(members, /下表授权暂不生效/)

  const comments = wikiFile('components/WikiCommentsPanel.vue')
  assert.match(comments, /reviewWikiComment/)
  assert.match(comments, /platform:wiki:comment:manage/)
  // 删除是软状态：措辞要说明可恢复，不能只留一个危险按钮
  assert.match(comments, /软状态/)
  // 四个状态筛选与审核动作齐全
  assert.match(comments, /'PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED'/)
  assert.match(comments, /comment\.status === 'PENDING'/)
  assert.match(comments, /comment\.status === 'PUBLISHED' && !comment\.pinned/)
  assert.match(comments, /'HIDDEN', 'DELETED'\]\.includes\(comment\.status/)
})

test('public comments are switchable from space settings; hints never claim the interface is missing', () => {
  const settings = wikiFile('components/WikiSettingsPanel.vue')
  const author = wikiFile('components/WikiAuthorEditor.vue')
  const comments = wikiFile('components/WikiCommentsPanel.vue')

  // 「评论区」是设置里的真实分区：开关直接绑空间字段，依赖公开访问
  assert.match(settings, /\{ label: '评论区', value: 'comments'/)
  assert.match(settings, /<template #comments>/)
  assert.match(settings, /<YdSwitch v-model="form\.commentEnabled" :disabled="!form\.publicReadEnabled" \/>/)
  // 依赖未满足时给直达入口（切到检索与切分分区），不是留一个禁用开关
  assert.match(settings, /function goPublicRead\(\) \{[\s\S]{0,90}activeSection\.value = 'retrieval'/)
  // 审核队列数量走治理接口（total 是服务端 Long，显示前归一化），并可跳到协作 → 评论
  assert.match(settings, /fetchWikiManagedComments\(spaceId, \{ page: 1, size: 1, status: 'PENDING' \}\)/)
  assert.match(settings, /Number\(pending\.data\?\.total \?\? 0\)/)
  assert.match(settings, /store\.openManage\('comments'\)/)

  // 提示区分「未开公开访问 / 未开公开评论」并指向真实位置：不能把空间没开写成接口不可用
  assert.match(author, /function commentUnavailableReason\(\)/)
  assert.match(author, /设置 → 检索与切分/)
  assert.match(author, /设置 → 评论区/)
  assert.doesNotMatch(author, /评论接口不可用/)
  assert.match(comments, /store\.openManage\('settings'\)/)

  // 评论读写的失败原因必须来自后端 message（例如引用无法定位），不能只显示 axios 的英文状态码文案
  const publicController = wikiFile('../../../views/wiki/use-wiki-public-comments.ts')
  const authorComments = wikiFile('components/WikiAuthorComments.vue')
  const thread = wikiFile('../../../views/wiki/WikiCommentThread.vue')
  assert.match(publicController, /resolveApiErrorMessage\(exception\)/)
  assert.doesNotMatch(publicController, /exception\?\.message/)
  assert.match(authorComments, /resolveApiErrorMessage\(exception\)/)
  assert.doesNotMatch(authorComments, /exception\?\.message/)
  // 引用评论提交成功后父级会收起草稿（内联提示随之消失）：必须有 toast 兜底确认
  assert.match(thread, /toast\.success\('引用评论已提交/)
})

test('comment review mode is a per-space setting: manual by default, AI opt-in with human fallback', () => {
  const settings = wikiFile('components/WikiSettingsPanel.vue')
  const comments = wikiFile('components/WikiCommentsPanel.vue')
  const utils = wikiFile('wiki-utils.ts')
  const api = wikiFile('../../../api/modules/platform-wiki.ts')

  // 设置里能选审核方式：人工（默认）/ AI，绑定空间字段并随保存提交
  assert.match(settings, /const reviewModeOptions = \[/)
  assert.match(settings, /label: '人工审核', value: 'MANUAL'/)
  assert.match(settings, /label: 'AI 审核', value: 'AI'/)
  assert.match(settings, /<YdSegmented v-model="reviewModeValue" :options="reviewModeOptions"/)
  assert.match(settings, /form\.value\.commentReviewMode = value === 'AI' \? 'AI' : 'MANUAL'/)
  // AI 审核依赖问答模型：没配模型必须提前说清楚会全部转人工
  assert.match(settings, /const aiReviewNeedsModel = computed/)
  assert.match(settings, /当前未配置问答模型/)
  assert.match(api, /commentReviewMode\?: 'MANUAL' \| 'AI'/)

  // 治理端能看到 AI 判定与理由（approved/rejected/unavailable 三态分开），并有标签元数据
  assert.match(utils, /approved: \{ label: 'AI 通过'/)
  assert.match(utils, /rejected: \{ label: 'AI 拒绝'/)
  assert.match(utils, /unavailable: \{ label: 'AI 未判定'/)
  assert.match(comments, /row\.original\.aiVerdict/)
  assert.match(comments, /wikiAiVerdictLabel\(row\.original\.aiVerdict\)/)
  assert.match(comments, /aiNote/)
})

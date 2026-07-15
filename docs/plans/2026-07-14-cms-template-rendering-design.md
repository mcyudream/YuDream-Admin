# CMS 模板运行时渲染设计

## 目标

让 CMS 首页和已发布页面模板在公共站点运行时读取最新的已发布 CMS 页面和公开知识库内容。用户与 AI 编辑模板，不在保存时生成内容快照。

## 架构

新增 `GET /api/public/cms/template-context`，由 CMS application service 统一编排 CMS 与 Wiki 数据。

接口只返回已发布 CMS 页面、公开知识库空间、节点当前 `publishedVersionId` 对应的版本，不返回草稿、未公开空间、索引分片或管理配置。Wiki 能力关闭时知识库上下文返回空集合，CMS 能力关闭时沿用现有公开 CMS 拒绝逻辑。

## 上下文

保留现有 `pages`、`navigation` 等字段，新增：

```text
cms.pages.latest
knowledge.spaces
knowledge.pages
knowledge.latest
```

列表项提供 `title`、`summary`、`url`、`content`、`updatedAt` 等字段。CMS 内容优先提供 `htmlContent`，Wiki 内容提供已发布 Markdown。

模板循环沿用 `data-yb-repeat`：

```html
<article data-yb-repeat="knowledge.latest">
  <a href="{{item.url}}">{{item.title}}</a>
  <p>{{item.summary}}</p>
</article>
```

## 安全边界

- 只读公开发布数据，页面访问时动态查询最新版本；
- 普通变量默认 HTML escape；
- HTML 继续清洗脚本、事件属性和 `javascript:`；
- Markdown 使用现有 Markdown 预览转换；
- 限制上下文条数和单条内容长度；
- AI 可以生成或修改模板 HTML/CSS，但不能通过模板工具直接发布。

## 验证

覆盖应用层发布过滤、Wiki 当前发布版本、公开接口、前端变量/循环/清洗，以及既有 Header/Footer 结构锁定规则。

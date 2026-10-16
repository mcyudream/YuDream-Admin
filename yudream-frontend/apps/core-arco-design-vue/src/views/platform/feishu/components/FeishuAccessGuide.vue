<script setup lang="ts">
const props = withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<template>
  <details class="access-guide" :class="{ 'access-guide--compact': props.compact }">
    <summary>
      <YdIcon name="morph:shield-check" />
      <span>如何授予飞书访问权限</span>
      <YdIcon class="access-guide__chevron" name="morph:chevron-down" />
    </summary>

    <div class="access-guide__body">
      <p class="access-guide__intro">
        访问范围由飞书开放平台和资源所有者控制，不能在 YuDream 中直接扩大。按目标资源完成对应授权后，刷新目录。
      </p>

      <ol>
        <li>
          <strong>确认应用凭证</strong>
          <span>在「飞书开放平台」的应用管理中打开当前企业自建应用，在「凭证与基础信息」查看 App ID 和 App Secret；回到 YuDream「平台能力 → 飞书开放平台」填写。已配置的 App Secret 不会回显，留空保存会保留原值。</span>
        </li>
        <li>
          <strong>开通 API 权限并发布版本</strong>
          <span>进入应用「权限管理」，按下方对应接口文档添加权限：云空间列表需要 <code>drive:drive:readonly</code> 或 <code>space:document:retrieve</code>；知识库空间列表需要 <code>wiki:wiki:readonly</code> 或 <code>wiki:space:retrieve</code>；知识库节点列表需要 <code>wiki:node:retrieve</code>（也可使用 <code>wiki:wiki</code> 或 <code>wiki:wiki:readonly</code>）；读取文档需要 <code>docx:document:readonly</code> 或 <code>docx:document</code>。如需编辑文本块，还需 <code>docx:document</code> 或 <code>docx:document:write_only</code>。保存后按组织流程创建并发布应用版本，等待审批生效。</span>
        </li>
        <li>
          <strong>授权知识库空间</strong>
          <span>请知识库管理员将该应用添加为知识库的可访问应用。只开通 API 权限不会自动让应用看到所有知识库空间。</span>
        </li>
        <li>
          <strong>授权个人文档</strong>
          <span>访问非应用自有文档时，先在应用设置中启用机器人能力，再请文档所有者打开目标文档，选择「…」→「更多」→「添加文档应用」，并将当前应用添加进去。</span>
        </li>
        <li>
          <strong>返回 YuDream 重试</strong>
          <span>确认飞书侧权限/资源授权已生效、YuDream 中的飞书能力已启用后，返回云空间目录或知识库空间，点击重新加载。YuDream 目前不能读取飞书应用控制台中的授权清单；若仍为空，请查看对应接口返回的飞书错误信息。</span>
        </li>
      </ol>

      <nav class="access-guide__links" aria-label="飞书权限官方文档">
        <a href="https://open.feishu.cn/app" target="_blank" rel="noopener noreferrer">
          打开飞书开放平台
          <YdIcon name="morph:external-link" />
        </a>
        <a href="https://open.feishu.cn/document/server-docs/docs/docs-overview" target="_blank" rel="noopener noreferrer">
          云文档概述与资源授权
          <YdIcon name="morph:external-link" />
        </a>
        <a href="https://open.feishu.cn/document/server-docs/docs/docs/docx-v1/document/get" target="_blank" rel="noopener noreferrer">
          获取文档权限要求
          <YdIcon name="morph:external-link" />
        </a>
        <a href="https://open.feishu.cn/document/server-docs/docs/drive-v1/file/list" target="_blank" rel="noopener noreferrer">
          列出云空间文件权限要求
          <YdIcon name="morph:external-link" />
        </a>
        <a href="https://open.feishu.cn/document/server-docs/docs/wiki-v2/space/list" target="_blank" rel="noopener noreferrer">
          列出知识库空间权限要求
          <YdIcon name="morph:external-link" />
        </a>
        <a href="https://open.feishu.cn/document/server-docs/docs/wiki-v2/space-node/list" target="_blank" rel="noopener noreferrer">
          列出知识库节点权限要求
          <YdIcon name="morph:external-link" />
        </a>
      </nav>
    </div>
  </details>
</template>

<style scoped>
.access-guide {
  min-width: 0;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.access-guide summary {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  color: var(--color-text-1);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  list-style: none;
}

.access-guide summary::-webkit-details-marker {
  display: none;
}

.access-guide summary:focus-visible,
.access-guide a:focus-visible {
  outline: 2px solid var(--yd-emphasis, var(--color-primary));
  outline-offset: 2px;
}

.access-guide__chevron {
  margin-left: auto;
  transition: transform 160ms ease;
}

.access-guide[open] .access-guide__chevron {
  transform: rotate(180deg);
}

.access-guide__body {
  display: grid;
  gap: 10px;
  padding: 0 12px 12px;
  border-top: 1px solid var(--color-border-2);
}

.access-guide__intro,
.access-guide ol {
  margin: 0;
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.6;
}

.access-guide__intro {
  padding-top: 10px;
}

.access-guide ol {
  display: grid;
  gap: 8px;
  padding-left: 20px;
}

.access-guide li strong,
.access-guide li span {
  display: block;
}

.access-guide li strong {
  color: var(--color-text-1);
  font-weight: 600;
}

.access-guide__links {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border-2);
}

.access-guide__links a {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  color: var(--yd-emphasis, var(--color-primary));
  font-size: 12px;
  text-decoration: none;
}

.access-guide__links a:hover {
  text-decoration: underline;
}

.access-guide--compact .access-guide__body {
  gap: 8px;
}
</style>

<template>
  <section class="mc-page">
    <section class="mc-hero">
      <div>
        <h2>服务器管理</h2>
        <p>维护服务器信息、线路地址、Markdown 描述和周目继承规则。</p>
      </div>
      <div class="mc-actions">
        <FaButton type="button" @click="model.newServer">
          <FaIcon name="i-ri:add-line" />
          新建
        </FaButton>
      </div>
    </section>

    <div class="mc-admin-layout">
      <McPanel title="服务器" eyebrow="Servers">
        <div class="mc-admin-list">
          <button
            v-for="server in model.servers"
            :key="server.id"
            type="button"
            :class="{ active: model.selectedId === server.id }"
            @click="model.selectServer(server.id); model.editServer(server)"
          >
            <strong>{{ server.name }}</strong>
            <span>{{ server.currentSeason?.name || '暂无周目' }}</span>
            <StatusPill :status="server.status?.status" />
            <code class="mc-server-id">{{ server.id }}</code>
          </button>
        </div>
      </McPanel>

      <McPanel title="基础信息" eyebrow="Form">
        <form class="mc-form" @submit.prevent="model.saveServer">
          <div class="mc-form-grid">
            <label>
              <span>名称</span>
              <input v-model="model.serverForm.name" placeholder="生存一区">
            </label>
            <label>
              <span>排序</span>
              <input v-model.number="model.serverForm.sort" type="number">
            </label>
            <label>
              <span>状态</span>
              <select v-model="model.serverForm.enabled">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
          </div>

          <div v-if="model.serverForm.id" class="mc-id-row">
            <span>服务器 ID</span>
            <code>{{ model.serverForm.id }}</code>
            <button type="button" class="mc-icon-button" title="复制服务器 ID" @click="model.copyServerId(model.serverForm.id)">
              <FaIcon name="i-ri:file-copy-line" />
            </button>
          </div>

          <div class="mc-section-title">
            <strong>多线地址</strong>
            <button type="button" class="mc-icon-button" title="新增线路" @click="model.addEndpoint">
              <FaIcon name="i-ri:add-line" />
            </button>
          </div>
          <div class="mc-endpoint-editor">
            <div v-for="(endpoint, index) in model.serverForm.endpoints" :key="index" class="mc-endpoint-row">
              <input v-model="endpoint.name" placeholder="线路名称">
              <input v-model="endpoint.host" placeholder="IP 或域名">
              <input v-model.number="endpoint.port" type="number" min="1" max="65535" placeholder="端口（留空自动/SRV）">
              <select v-model="endpoint.edition">
                <option value="JAVA">Java</option>
                <option value="BEDROCK">基岩版</option>
              </select>
              <label class="mc-check">
                <input v-model="endpoint.primaryLine" type="checkbox">
                <span>主线</span>
              </label>
              <label class="mc-check">
                <input v-model="endpoint.enabled" type="checkbox">
                <span>启用</span>
              </label>
              <button type="button" class="mc-icon-button danger" title="删除线路" @click="model.removeEndpoint(index)">
                <FaIcon name="i-ri:delete-bin-line" />
              </button>
            </div>
          </div>

          <div class="mc-section-title">
            <strong>Markdown 描述</strong>
          </div>
          <MarkdownEditor v-model="model.serverForm.descriptionMarkdown" />

          <div class="mc-actions end">
            <FaButton
              v-if="model.serverForm.id"
              class="mc-danger-button"
              :loading="model.saving"
              type="button"
              variant="outline"
              @click="confirmDeleteCurrent"
            >
              <FaIcon name="i-ri:delete-bin-line" />
              删除服务器
            </FaButton>
            <FaButton :loading="model.saving" type="submit">
              <FaIcon name="i-ri:save-3-line" />
              保存服务器
            </FaButton>
          </div>
        </form>
      </McPanel>
    </div>

    <div v-if="model.walletEnabled" class="mc-season-layout">
      <McPanel title="开启新周目" eyebrow="Season">
        <form class="mc-form" @submit.prevent="model.previewSeason">
          <div class="mc-form-grid">
            <label>
              <span>新周目名称</span>
              <input v-model="model.seasonForm.name" placeholder="第二周目">
            </label>
            <label>
              <span>开始时间</span>
              <input v-model="model.seasonForm.startedAtText" type="datetime-local">
            </label>
            <label>
              <span>备注</span>
              <input v-model="model.seasonForm.remark" placeholder="可选">
            </label>
          </div>
          <label>
            <span>周目说明</span>
            <textarea v-model="model.seasonForm.description" rows="3" placeholder="本周目玩法、变更或公告" />
          </label>
          <InheritanceRulesEditor :model="model" />
          <div class="mc-actions end">
            <FaButton :loading="model.saving" type="submit" variant="outline">
              <FaIcon name="i-ri:calculator-line" />
              预览继承
            </FaButton>
            <FaButton :disabled="!model.previewOperation" :loading="model.saving" type="button" @click="model.openSeason">
              <FaIcon name="i-ri:play-circle-line" />
              确认开启
            </FaButton>
          </div>
        </form>
      </McPanel>

      <McPanel title="继承预览" eyebrow="Preview">
        <div v-if="model.previewOperation" class="mc-table-wrap">
          <table class="mc-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>币种</th>
                <th>历史总额</th>
                <th>继承可用</th>
                <th>本周目充值</th>
                <th>本周目可使用</th>
                <th>下周目可使用</th>
                <th>当前余额</th>
                <th>重置差额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in model.previewOperation.adjustments" :key="`${item.userId}:${item.assetCode}`">
                <td>{{ item.userId }}</td>
                <td>{{ item.assetCode }}</td>
                <td>{{ model.formatAmount(item.realTotalIncomeAmount) }}</td>
                <td>{{ model.formatAmount(item.inheritedAmount) }}</td>
                <td>{{ model.formatAmount(item.seasonIncomeAmount) }}</td>
                <td>{{ model.formatAmount(item.seasonTotalAmount) }}</td>
                <td>{{ model.formatAmount(item.nextInheritedAmount) }}</td>
                <td>{{ model.formatAmount(item.walletBalanceBefore) }}</td>
                <td>
                  <span class="mc-delta" :class="item.direction.toLowerCase()">
                    {{ model.directionText(item.direction) }} {{ model.formatAmount(item.deltaAmount) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="mc-empty compact">生成预览后可检查每个用户和币种的继承处理。</div>
      </McPanel>
    </div>

    <McPanel v-if="model.walletEnabled" title="周目操作记录" eyebrow="Operations">
      <div class="mc-table-wrap">
        <table class="mc-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>周目</th>
              <th>状态</th>
              <th>处理条数</th>
              <th>备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="operation in model.operations" :key="operation.id">
              <td>{{ model.formatTime(operation.createdAt) }}</td>
              <td>{{ operation.toSeasonName }}</td>
              <td>{{ model.statusText(operation.status) }}</td>
              <td>{{ operation.adjustments.length }}</td>
              <td class="wrap-cell">{{ operation.remark || '-' }}</td>
              <td>
                <FaButton
                  v-if="operation.status === 'APPLIED' && model.latestOperation?.id === operation.id"
                  size="sm"
                  variant="outline"
                  :loading="model.saving"
                  @click="model.rollbackOperation(operation)"
                >
                  撤回
                </FaButton>
                <span v-else>-</span>
              </td>
            </tr>
            <tr v-if="!model.operations.length">
              <td colspan="6"><div class="mc-empty compact">暂无周目操作</div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="mc-pagination">
        <FaButton size="sm" variant="outline" :disabled="model.operationsPager.page <= 1" @click="model.prevOperationsPage">
          <FaIcon name="i-ri:arrow-left-s-line" />
          上一页
        </FaButton>
        <span>第 {{ model.operationsPager.page }} 页</span>
        <FaButton size="sm" variant="outline" :disabled="!model.operationsPager.hasNext" @click="model.nextOperationsPage">
          下一页
          <FaIcon name="i-ri:arrow-right-s-line" />
        </FaButton>
      </div>
    </McPanel>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { FaButton, FaIcon, useFaModal } from '@fantastic-admin/components'
import InheritanceRulesEditor from '../components/InheritanceRulesEditor.vue'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import McPanel from '../components/McPanel.vue'
import StatusPill from '../components/StatusPill.vue'
import type { MinecraftServerPluginModel } from '../composables/useMinecraftServerPlugin'

const props = defineProps<{
  model: MinecraftServerPluginModel
}>()

const modal = useFaModal()

function confirmDeleteCurrent() {
  const server = props.model.servers.find(item => item.id === props.model.serverForm.id)
  if (!server) {
    return
  }
  modal.confirm({
    title: '删除服务器',
    content: `确认删除服务器「${server.name}」吗？相关状态、周目操作和玩家在线记录也会一起删除。`,
    onConfirm: () => props.model.deleteServer(server),
  })
}

onMounted(() => {
  if (props.model.selectedServer) {
    props.model.editServer(props.model.selectedServer)
  }
})
</script>

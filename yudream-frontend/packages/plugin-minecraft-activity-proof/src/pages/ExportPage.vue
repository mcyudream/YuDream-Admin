<script setup lang="ts">
import type { ActivityProofModel } from '../composables/useActivityProof'
import ProofPanel from '../components/ProofPanel.vue'

defineProps<{
  model: ActivityProofModel
}>()
</script>

<template>
  <section class="proof-page">
    <section class="proof-toolbar">
      <div>
        <span>Word Export</span>
        <h2>活动证明导出</h2>
      </div>
      <div class="proof-state">
        <span :class="{ ok: model.status?.dependencies.minecraftReady }">MC</span>
        <span :class="{ ok: model.status?.dependencies.studentInfoReady }">学生</span>
        <span :class="{ ok: model.status?.dependencies.wordTemplateReady }">模板能力</span>
        <span :class="{ ok: model.settings?.templateReady }">模板文件</span>
      </div>
    </section>

    <section v-if="model.status && !model.ready" class="proof-warning">
      <span class="i-ri:error-warning-line" />
      <span v-if="!model.status.dependencies.minecraftReady">请先启用 Minecraft 服务器插件。</span>
      <span v-else-if="!model.status.dependencies.studentInfoReady">请先启用学生信息插件。</span>
      <span v-else-if="!model.status.dependencies.wordTemplateReady">请先在能力管理中启用 Word 模板能力。</span>
      <span v-else-if="!model.settings?.templateReady">请先上传 Word 模板文件。</span>
    </section>

    <section class="proof-grid">
      <ProofPanel title="导出参数" eyebrow="Export">
        <form class="proof-form" @submit.prevent="model.exportWord">
          <label>
            <span>服务器</span>
            <select v-model="model.selectedServerId" @change="model.reloadServerData">
              <option v-for="server in model.servers" :key="server.id" :value="server.id">
                {{ server.name }}{{ server.enabled ? '' : '（停用）' }}
              </option>
            </select>
          </label>
          <label>
            <span>活动名称</span>
            <input v-model="model.exportForm.activityName" autocomplete="off">
          </label>
          <label>
            <span>活动日期</span>
            <input v-model="model.exportForm.activityDate" autocomplete="off" placeholder="2026年7月8日">
          </label>
          <label>
            <span>证明编号</span>
            <input v-model="model.exportForm.proofNo" autocomplete="off" placeholder="留空自动生成">
          </label>
          <label>
            <span>学院</span>
            <input v-model="model.exportForm.college" autocomplete="off">
          </label>
          <label>
            <span>落款</span>
            <input v-model="model.exportForm.issuer" autocomplete="off">
          </label>
          <label>
            <span>出具日期</span>
            <input v-model="model.exportForm.issueDate" autocomplete="off">
          </label>
          <div class="proof-inline">
            <label>
              <span>最低在线分钟</span>
              <input v-model.number="model.exportForm.minOnlineMinutes" type="number" min="0" @change="model.reloadServerData">
            </label>
            <label class="proof-check">
              <input v-model="model.exportForm.includeAfk" type="checkbox" @change="model.reloadServerData">
              <span>计入 AFK</span>
            </label>
          </div>
          <button class="proof-primary" type="submit" :disabled="model.exporting || !model.ready">
            <span class="i-ri:file-word-2-line" />
            生成 Word
          </button>
        </form>
      </ProofPanel>

      <ProofPanel title="模板和默认值" eyebrow="Template">
        <div class="proof-template">
          <strong>{{ model.settings?.templateFilename || '未上传模板' }}</strong>
          <span>{{ model.settings?.templateUpdatedAt ? model.formatTime(model.settings.templateUpdatedAt) : '等待上传 .docx' }}</span>
          <label class="proof-upload">
            <span class="i-ri:upload-2-line" />
            上传模板
            <input type="file" accept=".docx" :disabled="model.uploading" @change="model.uploadTemplate(($event.target as HTMLInputElement).files?.[0])">
          </label>
        </div>
        <form class="proof-form compact" @submit.prevent="model.saveSettings">
          <label>
            <span>默认活动</span>
            <input v-model="model.settingsForm.defaultActivityName" autocomplete="off">
          </label>
          <label>
            <span>默认学院</span>
            <input v-model="model.settingsForm.defaultCollege" autocomplete="off">
          </label>
          <label>
            <span>默认落款</span>
            <input v-model="model.settingsForm.defaultIssuer" autocomplete="off">
          </label>
          <button class="proof-secondary" type="submit" :disabled="model.saving">
            保存默认值
          </button>
        </form>
      </ProofPanel>
    </section>

    <ProofPanel title="玩家与学生信息" eyebrow="Participants">
      <template #action>
        <div class="proof-actions">
          <button type="button" @click="model.selectAll">全选</button>
          <button type="button" @click="model.clearSelection">清空</button>
          <button type="button" @click="model.reloadServerData">刷新</button>
        </div>
      </template>
      <div class="proof-summary">
        <span>{{ model.participants.length }} 条记录</span>
        <span>{{ model.selectedCount }} 人待导出</span>
        <span>{{ model.unmatchedCount }} 人未匹配学生信息</span>
      </div>
      <div class="proof-table-wrap">
        <table class="proof-table">
          <thead>
            <tr>
              <th></th>
              <th>玩家</th>
              <th>姓名</th>
              <th>班级</th>
              <th>学号</th>
              <th>有效在线</th>
              <th>映射</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in model.participants" :key="row.playerId">
              <td>
                <input type="checkbox" :checked="model.selectedPlayerIds.includes(row.playerId)" @change="model.togglePlayer(row)">
              </td>
              <td class="proof-main-cell">
                <strong>{{ row.playerName }}</strong>
                <span>{{ row.playerId }}</span>
              </td>
              <td>{{ row.studentName || '-' }}</td>
              <td>{{ row.className || '-' }}</td>
              <td>{{ row.studentNo || '-' }}</td>
              <td>{{ model.minutes(row.effectiveOnlineMillis) }}</td>
              <td>
                <form class="proof-map-form" @submit.prevent="model.bindStudent(row)">
                  <input v-model="model.mappingInputs[row.playerId]" name="studentNo" placeholder="学号">
                  <button type="submit">保存</button>
                </form>
              </td>
            </tr>
            <tr v-if="!model.participants.length">
              <td colspan="7">
                <div class="proof-empty">暂无可导出的玩家在线记录</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </ProofPanel>

    <section class="proof-grid bottom">
      <ProofPanel title="已保存映射" eyebrow="Mappings">
        <div class="proof-list">
          <article v-for="row in model.mappings" :key="row.id">
            <div>
              <strong>{{ row.playerName || row.playerId }}</strong>
              <span>{{ row.studentNo }}</span>
            </div>
            <button type="button" @click="model.deleteMapping(row)">删除</button>
          </article>
          <div v-if="!model.mappings.length" class="proof-empty">暂无手动映射</div>
        </div>
      </ProofPanel>

      <ProofPanel title="导出记录" eyebrow="History">
        <div class="proof-list">
          <article v-for="row in model.exports" :key="row.id">
            <div>
              <strong>{{ row.outputFilename }}</strong>
              <span>{{ row.participantCount }} 人 / {{ model.formatTime(row.generatedAt) }}</span>
            </div>
            <button type="button" @click="model.openDownload(row)">下载</button>
          </article>
          <div v-if="!model.exports.length" class="proof-empty">暂无导出记录</div>
        </div>
      </ProofPanel>
    </section>
  </section>
</template>

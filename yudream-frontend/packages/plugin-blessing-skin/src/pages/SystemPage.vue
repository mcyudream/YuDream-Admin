<template>
  <section class="workbench two-col">
    <SkinPanel stack>
      <div>
        <div class="panel-head"><strong>皮肤站配置</strong></div>
        <div class="form-grid">
          <label>
            <span>每个用户最多角色数</span>
            <FaInput v-model.number="model.settingsForm.maxPlayersPerUser" type="number" placeholder="0 表示不限制" />
          </label>
          <label class="switch-row">
            <span>允许公开上传材质</span>
            <FaSwitch v-model="model.settingsForm.allowPublicUpload" />
          </label>
          <label>
            <span>站点公告</span>
            <FaInput v-model="model.settingsForm.siteNotice" placeholder="显示在皮肤站仪表盘的简短提示" />
          </label>
          <FaButton :loading="model.saving === 'settings'" @click="model.saveSettings">
            <FaIcon name="i-ri:save-3-line" />
            保存配置
          </FaButton>
        </div>
      </div>

      <div class="divider" />

      <div>
        <div class="panel-head"><strong>皮肤站用户</strong><FaTag variant="secondary">{{ model.users.length }}</FaTag></div>
        <div class="form-grid">
          <label>
            <span>邮箱</span>
            <FaInput v-model="model.userForm.email" placeholder="skin@example.com" />
          </label>
          <label>
            <span>昵称</span>
            <FaInput v-model="model.userForm.nickname" />
          </label>
          <label>
            <span>密码</span>
            <FaInput v-model="model.userForm.password" type="password" />
          </label>
          <FaButton :loading="model.saving === 'user'" @click="model.createUser">
            <FaIcon name="i-ri:user-add-line" />
            创建用户
          </FaButton>
        </div>
      </div>
    </SkinPanel>

    <SkinPanel title="Blessing Skin 数据迁移">
      <div class="form-grid">
        <label>
          <span>驱动类</span>
          <FaInput v-model="model.migrationForm.driverClass" placeholder="com.mysql.cj.jdbc.Driver / org.sqlite.JDBC" />
        </label>
        <label>
          <span>JDBC 地址</span>
          <FaInput v-model="model.migrationForm.jdbcUrl" placeholder="jdbc:mysql://localhost:3306/blessing_skin" />
        </label>
        <label>
          <span>用户名</span>
          <FaInput v-model="model.migrationForm.username" />
        </label>
        <label>
          <span>密码</span>
          <FaInput v-model="model.migrationForm.password" type="password" />
        </label>
        <label>
          <span>材质目录</span>
          <FaInput v-model="model.migrationForm.textureBaseDir" placeholder="storage/textures" />
        </label>
        <FaButton :loading="model.saving === 'migration'" @click="model.runMigration">
          <FaIcon name="i-ri:database-2-line" />
          开始迁移
        </FaButton>
      </div>

      <div v-if="model.migrationReport" class="report-grid">
        <span>用户 {{ model.migrationReport.users }}</span>
        <span>角色 {{ model.migrationReport.players }}</span>
        <span>材质 {{ model.migrationReport.textures }}</span>
        <span>衣柜 {{ model.migrationReport.closetItems }}</span>
        <span>配置 {{ model.migrationReport.options }}</span>
      </div>
      <div v-if="model.migrationReport?.warnings.length" class="warning-list">
        <strong>警告</strong>
        <span v-for="item in model.migrationReport.warnings" :key="item">{{ item }}</span>
      </div>
    </SkinPanel>
  </section>
</template>

<script setup lang="ts">
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import { FaButton, FaIcon, FaInput, FaSwitch, FaTag } from '@fantastic-admin/components'
import SkinPanel from '../components/SkinPanel.vue'

defineProps<{
  model: SkinPluginModel
}>()
</script>

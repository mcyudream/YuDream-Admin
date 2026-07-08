<template>
  <div class="mc-rules">
    <div class="mc-rules__toolbar">
      <span>继承规则按币种通配和累计金额区间匹配，区间必须从 0 连续覆盖到无上限。</span>
      <div>
        <button type="button" class="mc-icon-button" title="恢复默认规则" @click="model.resetRules">
          <FaIcon name="i-ri:refresh-line" />
        </button>
        <button type="button" class="mc-icon-button" title="新增规则" @click="model.addRule">
          <FaIcon name="i-ri:add-line" />
        </button>
      </div>
    </div>
    <div class="mc-table-wrap">
      <table class="mc-table">
        <thead>
          <tr>
            <th>币种通配</th>
            <th>最小累计</th>
            <th>最大累计</th>
            <th>继承比例</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(rule, index) in model.seasonForm.rules" :key="index">
            <td><input v-model="rule.assetPattern" placeholder="* / CNY / POINT_*"></td>
            <td><input v-model="rule.minAmount" inputmode="decimal" placeholder="0"></td>
            <td><input v-model="rule.maxAmount" inputmode="decimal" placeholder="留空表示无上限"></td>
            <td><input v-model="rule.inheritRate" inputmode="decimal" placeholder="0.5"></td>
            <td>
              <button type="button" class="mc-icon-button danger" title="删除规则" @click="model.removeRule(index)">
                <FaIcon name="i-ri:delete-bin-line" />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FaIcon } from '@fantastic-admin/components'
import type { MinecraftServerPluginModel } from '../composables/useMinecraftServerPlugin'

defineProps<{
  model: MinecraftServerPluginModel
}>()
</script>

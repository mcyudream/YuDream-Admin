<template>
  <section class="wallet-page">
    <section class="wallet-hero">
      <div>
        <span>钱包设置</span>
        <h2>币种配置</h2>
        <p>维护人民币和积分类币种，默认币种也可以改名，并可单独开启或关闭转账。</p>
      </div>
      <FaButton :loading="model.loading" variant="outline" @click="model.load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </section>

    <WalletPanel title="币种表格" eyebrow="Assets">
      <div class="wallet-table-wrap">
        <table class="wallet-table">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>符号</th>
              <th>类型</th>
              <th>精度</th>
              <th>转账</th>
              <th>状态</th>
              <th>最低转账</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="asset in model.assets" :key="asset.code">
              <td class="amount-cell">{{ asset.code }}</td>
              <td>{{ asset.name }}</td>
              <td>{{ asset.symbol || '-' }}</td>
              <td>{{ asset.money ? '货币' : '积分' }}</td>
              <td>{{ asset.scale }}</td>
              <td>
                <span class="wallet-tag" :class="{ muted: !asset.transferEnabled }">
                  {{ asset.transferEnabled ? '允许' : '关闭' }}
                </span>
              </td>
              <td>
                <span class="wallet-tag" :class="{ muted: !asset.enabled }">
                  {{ asset.enabled ? '启用' : '停用' }}
                </span>
              </td>
              <td>{{ asset.minTransferAmount ?? 0 }}</td>
              <td>
                <div class="wallet-row-actions">
                  <FaButton size="sm" variant="outline" @click="model.editAsset(asset)">
                    编辑
                  </FaButton>
                  <FaButton size="sm" variant="destructive" :loading="model.saving" @click="confirmDeleteAsset(asset)">
                    删除
                  </FaButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </WalletPanel>

    <WalletPanel title="编辑币种" eyebrow="Form">
      <form class="wallet-form" @submit.prevent="model.saveAsset">
        <div class="wallet-form-grid">
          <label>
            <span>编码</span>
            <input v-model="model.assetForm.code" placeholder="POINT">
          </label>
          <label>
            <span>名称</span>
            <input v-model="model.assetForm.name" placeholder="积分">
          </label>
          <label>
            <span>符号</span>
            <input v-model="model.assetForm.symbol" placeholder="￥ / 积分">
          </label>
          <label>
            <span>精度</span>
            <input v-model.number="model.assetForm.scale" type="number" min="0" max="8">
          </label>
        </div>
        <div class="wallet-form-grid">
          <label>
            <span>最低转账</span>
            <input v-model="model.assetForm.minTransferAmount" inputmode="decimal">
          </label>
          <label>
            <span>资产类型</span>
            <select v-model="model.assetForm.money">
              <option :value="true">货币</option>
              <option :value="false">积分</option>
            </select>
          </label>
          <label>
            <span>转账开关</span>
            <select v-model="model.assetForm.transferEnabled">
              <option :value="true">允许转账</option>
              <option :value="false">关闭转账</option>
            </select>
          </label>
          <label>
            <span>状态</span>
            <select v-model="model.assetForm.enabled">
              <option :value="true">启用</option>
              <option :value="false">停用</option>
            </select>
          </label>
        </div>
        <div class="wallet-actions">
          <FaButton :loading="model.saving" type="submit">
            <FaIcon name="i-ri:save-3-line" />
            保存
          </FaButton>
          <FaButton variant="outline" type="button" @click="model.resetAssetForm">
            重置
          </FaButton>
        </div>
      </form>
    </WalletPanel>

    <WalletPanel title="充值配置" eyebrow="Recharge">
      <form class="wallet-form" @submit.prevent="model.saveRechargeSettings">
        <div class="wallet-form-grid">
          <label>
            <span>充值总开关</span>
            <select v-model="model.rechargeSettingsForm.enabled">
              <option :value="true">启用</option>
              <option :value="false">停用</option>
            </select>
          </label>
        </div>
        <div class="wallet-table-wrap">
          <table class="wallet-table">
            <thead>
              <tr>
                <th>币种</th>
                <th>充值</th>
                <th>支付 1 元到账</th>
                <th>最低支付</th>
                <th>最高支付</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="rule in model.rechargeSettingsForm.rules" :key="rule.assetCode">
                <td class="amount-cell">{{ model.assetName(rule.assetCode) }}（{{ rule.assetCode }}）</td>
                <td>
                  <select v-model="rule.enabled">
                    <option :value="true">开启</option>
                    <option :value="false">关闭</option>
                  </select>
                </td>
                <td>
                  <input v-model="rule.ratio" inputmode="decimal" placeholder="1">
                </td>
                <td>
                  <input v-model="rule.minPayAmount" inputmode="decimal" placeholder="1.00">
                </td>
                <td>
                  <input v-model="rule.maxPayAmount" inputmode="decimal" placeholder="不限制">
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="wallet-actions">
          <FaButton :loading="model.saving" type="submit">
            <FaIcon name="i-ri:save-3-line" />
            保存充值配置
          </FaButton>
        </div>
      </form>
    </WalletPanel>
  </section>
</template>

<script setup lang="ts">
import type { WalletAsset } from '../types'
import { FaButton, FaIcon, useFaModal } from '@fantastic-admin/components'
import WalletPanel from '../components/WalletPanel.vue'
import type { WalletPluginModel } from '../composables/useWalletPlugin'

const props = defineProps<{
  model: WalletPluginModel
}>()

const modal = useFaModal()

function confirmDeleteAsset(asset: WalletAsset) {
  modal.confirm({
    title: '删除币种',
    content: `确认删除币种「${asset.name}（${asset.code}）」吗？已产生余额或流水的币种会被后端拒绝删除。`,
    onConfirm: () => props.model.deleteAsset(asset),
  })
}
</script>

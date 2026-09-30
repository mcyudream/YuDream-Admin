<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QRCode from 'qrcode'
import { FaButton, FaIcon } from '@yudream/components'
import type { CapabilityItem } from '@/api/modules/platform-capability'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiCapability from '@/api/modules/platform-capability'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

const props = defineProps<Props>()

const loading = ref(false)
const enabled = ref(false)
const missing = ref(false)
const message = ref('')
const canvasRef = ref<HTMLCanvasElement>()
/** 二维码内容 = 当前管理端访问地址：管理员用哪个地址打开后台，App 就添加哪个域 */
const pairUrl = `${window.location.origin}`

onMounted(load)

async function load() {
  loading.value = true
  message.value = ''
  try {
    const res = await apiCapability.list()
    const rows: CapabilityItem[] = res.data
    const item = rows.find(row => row.code === 'mobile-app')
    if (!item) {
      missing.value = true
      message.value = '当前服务端未包含移动应用能力，请更新服务端镜像'
      return
    }
    enabled.value = Boolean(item.enabled)
    if (enabled.value) {
      await renderQr()
    }
  }
  catch (error) {
    message.value = error instanceof Error ? error.message : '能力数据暂不可用'
  }
  finally {
    loading.value = false
  }
}

async function renderQr() {
  if (!canvasRef.value) {
    return
  }
  await QRCode.toCanvas(canvasRef.value, pairUrl, {
    width: 148,
    margin: 1,
    color: { dark: '#111827', light: '#ffffff' },
  })
}
</script>

<template>
  <div class="dashboard-card__content dashboard-mobile-pair">
    <template v-if="enabled">
      <div class="pair-body">
        <div class="pair-text">
          <strong>用 YDAM App 扫码添加本站点</strong>
          <p class="pair-url">
            <FaIcon name="i-ri:global-line" />
            {{ pairUrl }}
          </p>
          <p class="pair-hint">
            打开 App → 域管理 → 右上角扫一扫，扫下方二维码即可完成添加与登录引导。
          </p>
        </div>
        <div class="pair-qr">
          <canvas ref="canvasRef" />
        </div>
      </div>
    </template>
    <div v-else class="pair-empty">
      <FaIcon name="i-ri:qr-code-line" class="pair-empty__icon" />
      <p>{{ loading ? '正在检查移动应用能力…' : (message || 'mobile-app 能力未启用：请先在「平台 → 能力管理」启用后再扫码配对。') }}</p>
    </div>

    <div class="pair-footer">
      <span>站点公开地址即二维码内容</span>
      <FaButton v-if="card.actionPath" size="sm" variant="outline" @click="props.onOpen?.(card)">
        <FaIcon name="i-ri:arrow-right-line" />
        移动端配置
      </FaButton>
    </div>
  </div>
</template>

<style scoped>
.dashboard-mobile-pair {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.pair-body {
  display: flex;
  gap: 16px;
  align-items: center;
  flex: 1;
}

.pair-text {
  display: grid;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.pair-text strong {
  color: var(--color-text-1);
  font-size: 14px;
}

.pair-url {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  color: var(--color-text-2);
  font-family: ui-monospace, monospace;
  font-size: 12px;
  word-break: break-all;
}

.pair-hint {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.pair-qr {
  display: grid;
  flex: none;
  place-items: center;
  padding: 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  background: #fff;
}

.pair-qr canvas {
  display: block;
}

.pair-empty {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: center;
  color: var(--color-text-3);
  font-size: 13px;
}

.pair-empty__icon {
  flex: none;
  font-size: 24px;
}

.pair-footer {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>

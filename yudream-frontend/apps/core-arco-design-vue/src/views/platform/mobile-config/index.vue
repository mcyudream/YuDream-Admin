<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { FaAlert, FaButton, FaCard, FaIcon, FaInput, FaPageHeader, FaPageMain, FaSwitch, useFaToast } from '@yudream/components'
import apiCapability from '@/api/modules/platform-capability'
import type { CapabilityItem } from '@/api/modules/platform-capability'

interface BannerDraft {
  imageUrl: string
  title: string
  route: string
}

const MOBILE_CODE = 'mobile-app'
const toast = useFaToast()

const loading = ref(false)
const saving = ref(false)
const enabled = ref(false)
const iosEnabled = ref(false)
const loginHeroImage = ref('')
const loginHeroBackground = ref('')
const banners = ref<BannerDraft[]>([])
/** mobile-app 能力里除本页键之外的其他配置键（保存时原样保留） */
const extraConfig = ref<Record<string, string>>({})

const bannerCount = computed(() => banners.value.length)

async function load() {
  loading.value = true
  try {
    const res = await apiCapability.list()
    const rows: CapabilityItem[] = res.data
    const item = rows.find(row => row.code === MOBILE_CODE)
    if (!item) {
      toast.error('未找到 mobile-app 能力，请先确认平台能力已注册')
      return
    }
    enabled.value = Boolean(item.enabled)
    const config = item.config || {}
    iosEnabled.value = String(config.iosEnabled ?? '').toLowerCase() === 'true'
    loginHeroImage.value = String(config.loginHeroImage ?? '')
    loginHeroBackground.value = String(config.loginHeroBackground ?? '')
    try {
      const parsed = JSON.parse(config.homeBanners || '[]')
      banners.value = Array.isArray(parsed)
        ? parsed.map((item: Record<string, unknown>) => ({
            imageUrl: String(item.imageUrl ?? ''),
            title: String(item.title ?? ''),
            route: String(item.route ?? ''),
          }))
        : []
    }
    catch {
      banners.value = []
    }
    for (const [key, value] of Object.entries(config)) {
      if (key !== 'iosEnabled' && key !== 'loginHeroImage' && key !== 'loginHeroBackground' && key !== 'homeBanners') {
        extraConfig.value[key] = String(value ?? '')
      }
    }
  }
  finally {
    loading.value = false
  }
}

function addBanner() {
  banners.value.push({ imageUrl: '', title: '', route: '' })
}

function removeBanner(index: number) {
  banners.value.splice(index, 1)
}

function moveBanner(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= banners.value.length)
    return
  const [row] = banners.value.splice(index, 1)
  banners.value.splice(target, 0, row)
}

async function save() {
  const cleaned = banners.value
    .map(banner => ({
      imageUrl: banner.imageUrl.trim(),
      title: banner.title.trim(),
      route: banner.route.trim(),
    }))
    .filter(banner => banner.imageUrl || banner.title || banner.route)
  for (const banner of cleaned) {
    if (!banner.imageUrl) {
      toast.error('轮播图存在未填写图片地址的条目')
      return
    }
  }
  saving.value = true
  try {
    const config: Record<string, string> = { ...extraConfig.value }
    config.iosEnabled = String(iosEnabled.value)
    config.loginHeroImage = loginHeroImage.value.trim()
    config.loginHeroBackground = loginHeroBackground.value.trim()
    config.homeBanners = cleaned.length ? JSON.stringify(cleaned) : ''
    const res = await apiCapability.updateConfig(MOBILE_CODE, config)
    enabled.value = Boolean(res.data?.enabled)
    toast.success('移动端配置已保存', { description: 'App 重新进入首页/登录页后生效' })
    await load()
  }
  catch (error) {
    const message = (error as { message?: string })?.message
    toast.error(message || '保存失败')
  }
  finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <FaPageHeader title="移动端配置" class="mb-0">
      <template #description>
        YDAM App 的站点品牌（登录页主视觉）、首页轮播图与 iOS 通道开关。保存后 App 重新拉取站点信息即生效。
      </template>
    </FaPageHeader>

    <FaPageMain>
      <FaAlert v-if="!enabled" type="warning">
        mobile-app 能力当前未启用：配置可先编辑保存，但 App 端在能力启用前不会拉取这些内容。
      </FaAlert>

      <div class="mobile-config-grid">
        <FaCard class="section">
          <template #header>
            <span class="section-title">登录页品牌</span>
          </template>
          <div class="section-body">
            <label class="config-field">
              <span>登录页主视觉图（站点资产路径或完整 URL，留空用内置样式）</span>
              <FaInput v-model="loginHeroImage" placeholder="/upload/hero.png 或 https://..." />
            </label>
            <label class="config-field">
              <span>登录页 hero 底色（CSS 颜色值，留空用主题 accent）</span>
              <FaInput v-model="loginHeroBackground" placeholder="如 #101728" />
            </label>
          </div>
        </FaCard>

        <FaCard class="section">
          <template #header>
            <div class="section-title-row">
              <span class="section-title">首页轮播图（{{ bannerCount }}）</span>
              <FaButton size="sm" @click="addBanner">
                <FaIcon name="i-ri:add-line" />
                添加轮播
              </FaButton>
            </div>
          </template>
          <div class="section-body">
            <div v-if="!banners.length" class="empty-hint">
              还没有轮播图。添加后 App 首页顶部将按顺序轮播展示。
            </div>
            <div v-for="(banner, index) in banners" :key="index" class="banner-row">
              <div class="banner-row__index">
                {{ index + 1 }}
              </div>
              <div class="banner-row__fields">
                <label class="config-field">
                  <span>图片地址</span>
                  <FaInput v-model="banner.imageUrl" placeholder="/upload/banner.png 或完整 URL" />
                </label>
                <label class="config-field">
                  <span>标题（可空，展示在图上）</span>
                  <FaInput v-model="banner.title" placeholder="站点公告：欢迎来到 YuDream" />
                </label>
                <label class="config-field">
                  <span>点击跳转的应用内路由（可空）</span>
                  <FaInput v-model="banner.route" placeholder="/posts/latest" />
                </label>
              </div>
              <div class="banner-row__ops">
                <FaButton size="sm" variant="ghost" :disabled="index === 0" title="上移" @click="moveBanner(index, -1)">
                  <FaIcon name="i-ri:arrow-up-line" />
                </FaButton>
                <FaButton size="sm" variant="ghost" :disabled="index === banners.length - 1" title="下移" @click="moveBanner(index, 1)">
                  <FaIcon name="i-ri:arrow-down-line" />
                </FaButton>
                <FaButton size="sm" variant="ghost" title="删除" @click="removeBanner(index)">
                  <FaIcon name="i-ri:delete-bin-line" />
                </FaButton>
              </div>
            </div>
          </div>
        </FaCard>

        <FaCard class="section">
          <template #header>
            <span class="section-title">通道</span>
          </template>
          <div class="section-body">
            <div class="switch-row">
              <div>
                <strong>iOS 通道开关</strong>
                <p>iOS 构建参与 App 分发与能力探测；Android 不受影响。</p>
              </div>
              <FaSwitch v-model="iosEnabled" />
            </div>
          </div>
        </FaCard>

        <div class="save-row">
          <FaButton :loading="saving" @click="save">
            <FaIcon name="i-ri:save-3-line" />
            保存移动端配置
          </FaButton>
        </div>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.mobile-config-grid {
  display: grid;
  gap: 16px;
  max-width: 860px;
}

.section-title {
  color: var(--color-text-1);
  font-weight: 700;
}

.section-title-row {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.section-body {
  display: grid;
  gap: 12px;
}

.banner-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
}

.banner-row__index {
  display: grid;
  width: 26px;
  height: 26px;
  flex: none;
  place-items: center;
  margin-top: 18px;
  border-radius: 13px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 12px;
}

.banner-row__fields {
  display: grid;
  flex: 1;
  gap: 8px;
}

.banner-row__ops {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 18px;
}

.switch-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.switch-row p {
  margin: 2px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.save-row {
  position: sticky;
  bottom: 0;
  padding: 10px 0;
  background: var(--color-bg-1);
}
</style>

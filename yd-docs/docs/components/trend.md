<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingMisc from '../.vitepress/theme/components/demos/InteractiveRemainingMisc.vue'

const srcBasic = `<template>
  <div class="flex gap-2 items-center">
    <FaTrend value="12.3%" />
    <FaTrend value="12.3%" type="down" />
  </div>
</template>`

const srcVariant = `<template>
  <!-- default / filled / soft / outline 四种风格 -->
  <FaTrend value="+12.3%" variant="filled" />
  <FaTrend value="12.3%" variant="soft" type="down" />
  <FaTrend value="12.3%" variant="outline" />
</template>`

const srcReverse = `<template>
  <!-- 成本、错误率等「下降是好事」的场景 -->
  <FaTrend value="错误率 -12.3%" type="down" reverse variant="filled" />
</template>`

const props = [
  ['<code>value</code>', '<code>string</code>', '—', '趋势数值文本，使用等宽数字（tabular-nums）展示'],
  ['<code>type</code>', "<code>'up' | 'down'</code>", "<code>'up'</code>", '趋势方向；<code>up</code> 箭头朝上，<code>down</code> 箭头旋转 180° 朝下'],
  ['<code>prefix</code>', '<code>string</code>', "<code>''</code>", '数值前缀（如货币符号），以较低透明度展示'],
  ['<code>suffix</code>', '<code>string</code>', "<code>''</code>", '数值后缀（如 %、单位），以较低透明度展示'],
  ['<code>reverse</code>', '<code>boolean</code>', '<code>false</code>', '反转配色语义：开启后 down 显示绿色、up 显示红色，适用于成本、错误率等下降为正面的指标'],
  ['<code>size</code>', "<code>'small' | 'medium' | 'large'</code>", "<code>'medium'</code>", '尺寸'],
  ['<code>variant</code>', "<code>'default' | 'filled' | 'soft' | 'outline'</code>", "<code>'default'</code>", '视觉风格：纯文字 / 渐变填充 / 浅色底 / 描边'],
]
</script>

# FaTrend 趋势标记

用于展示指标涨跌的胶囊形趋势标签，按方向自动配色（涨绿跌红），支持四种视觉风格与配色语义反转。

> 源码位置：`yudream-frontend/packages/components/src/basic/trend/index.vue`

## 基础用法

`type` 控制方向，默认 `up`。

<Demo title="涨跌方向" description="up 绿色 / down 红色" :source="srcBasic">
  <ClientOnly><InteractiveRemainingMisc type="trend" /></ClientOnly>
</Demo>

## 视觉风格

`variant` 提供纯文字、渐变填充、浅色底、描边四种风格。

<Demo title="四种风格" description="default / filled / soft / outline" :source="srcVariant">
  <div class="demo-row">
    <span style="display: inline-flex; align-items: center; gap: 4px; border-radius: 9999px; padding: 4px 8px; font-size: 14px; color: #22c55e;">+12.3% ▲</span>
    <span style="display: inline-flex; align-items: center; gap: 4px; border-radius: 9999px; padding: 4px 8px; font-size: 14px; background: linear-gradient(90deg, #ef4444, #f43f5e); color: #fff;">12.3% ▼</span>
    <span style="display: inline-flex; align-items: center; gap: 4px; border-radius: 9999px; padding: 4px 8px; font-size: 14px; background: rgba(239,68,68,.1); color: #dc2626;">12.3% ▼</span>
    <span style="display: inline-flex; align-items: center; gap: 4px; border-radius: 9999px; padding: 4px 8px; font-size: 14px; border: 1px solid rgba(34,197,94,.3); color: #16a34a; background: rgba(34,197,94,.05);">12.3% ▲</span>
  </div>
</Demo>

## 前后缀与语义反转

`prefix` / `suffix` 用于携带货币符号或单位；`reverse` 用于成本、错误率等「下降是好事」的场景——此时 `type="down"` 反而显示绿色。

<Demo title="reverse 反转配色" description="错误率下降显示为正面绿色" :source="srcReverse">
  <div class="demo-row">
    <span style="display: inline-flex; align-items: center; gap: 4px; border-radius: 9999px; padding: 4px 8px; font-size: 14px; background: linear-gradient(90deg, #22c55e, #10b981); color: #fff;">错误率 -12.3% ▼</span>
    <span style="display: inline-flex; align-items: center; gap: 4px; border-radius: 9999px; padding: 4px 8px; font-size: 14px; background: rgba(34,197,94,.1); color: #16a34a;">↓ 成本节省 15% ▼</span>
  </div>
</Demo>

## 示例讲解

```vue
<FaTrend value="错误率 -12.3%" type="down" reverse variant="filled" />
```

- 配色由 `isColorUp` 计算得出：默认 `up = 绿 / down = 红`；设置 `reverse` 后整体取反。
- 方向箭头复用内置 `Icon` 组件（`i-ep:caret-top`），`type="down"` 通过 rotate 180° 实现，带过渡动画。
- 数值部分使用 `tabular-nums` 保证多位数字宽度稳定，适合在表格/看板中纵向对比。

## API

### Props

<ApiTable title="FaTrend Props" :data="props" />

### Slots

无插槽，内容全部通过 props 传入。

### Emits

无自定义 emits。

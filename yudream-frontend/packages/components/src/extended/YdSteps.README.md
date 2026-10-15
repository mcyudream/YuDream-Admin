# YdSteps 步骤导航

保留 `v-model<number>`、`change(index)`、`clickable` 与按步骤 ID 命名的插槽。每个步骤可选 `status: 'error' | 'process' | 'wait' | 'finish'`、`icon`；未指定 status 时仍由当前模型和组件级 `status` 推导。

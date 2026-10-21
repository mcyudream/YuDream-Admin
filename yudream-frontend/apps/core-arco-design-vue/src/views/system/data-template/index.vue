<script setup lang="ts">
import type { ExcelTemplatePackageImportResult } from '@/api/modules/system-excel'
import apiExcel from '@/api/modules/system-excel'
import { excelForm, importResultMessage, pickExcelFile, saveExcelResponse } from '@/utils/excel'
import { templateImportFeedbackKind } from '@/utils/template-import-feedback'

const toast = useYdToast()
const modal = useYdModal()

const blankDownloading = ref(false)
const exporting = ref(false)
const importing = ref(false)
const lastResult = ref<ExcelTemplatePackageImportResult | null>(null)

/** 下载空白模板：仅含受控字段表头与示例说明，用于首次填写 */
async function downloadBlank() {
  blankDownloading.value = true
  try {
    const res = await apiExcel.templatePackageBlank()
    saveExcelResponse(res, '系统数据模板-空白.xlsx')
  }
  finally {
    blankDownloading.value = false
  }
}

/** 导出当前数据：与模板同构的填写基线，改完可直接导回 */
async function exportCurrent() {
  exporting.value = true
  try {
    const res = await apiExcel.templatePackageExport()
    saveExcelResponse(res, '系统数据模板-当前数据.xlsx')
  }
  finally {
    exporting.value = false
  }
}

/** 导入入口：先选文件，弹出覆盖与保留规则确认，确认后才真正上传 */
function startImport() {
  pickExcelFile((file) => {
    modal.confirm({
      title: '确认导入数据模板',
      content: `即将导入「${file.name}」。导入仅覆盖部门、角色、用户、菜单中的受控字段：与现有记录同标识的行按模板内容覆盖更新；目标环境已有、模板中不存在的记录不会被删除。确认开始导入吗？`,
      confirmButtonText: '开始导入',
      onConfirm: () => submitImport(file),
    })
  })
}

async function submitImport(file: File) {
  importing.value = true
  try {
    const res = await apiExcel.importTemplatePackage(excelForm(file))
    lastResult.value = res.data
    notifyImportResult(res.data)
  }
  catch {
    // 失败原因由拦截器统一提示；保留面板便于与上次结果对照
  }
  finally {
    importing.value = false
  }
}

/** 按结果如实提示：有失败行不报成功；只有入队信息，不承诺邮件送达 */
function notifyImportResult(result: ExcelTemplatePackageImportResult) {
  const feedback = templateImportFeedbackKind(result)
  if (feedback === 'error') {
    toast.error('导入完成，部分行失败', { description: importResultMessage(result) })
    return
  }
  if (feedback === 'warning') {
    toast.warning('导入完成，但有提示', { description: (result.warnings ?? []).slice(0, 3).join('；') })
    return
  }
  toast.success(importResultMessage(result))
}
</script>

<template>
  <div class="settings-page-shell">
    <YdPageHeader
      title="数据模板"
      class="mb-0"
    >
      <template #description>
        通过 Excel 模板包批量维护部门、角色、用户与菜单：下载模板填写后在本页导入，或导出当前数据作为填写基线。导入只写入受控字段，不删除模板之外的任何记录。
      </template>
    </YdPageHeader>

    <div class="flex flex-col gap-4 p-5 lg:p-6">
      <YdSettingsSection
        title="获取模板包"
        description="空白模板只含受控字段表头与示例说明；导出当前数据会生成与模板同构的文件，直接在导出结果上修改后即可导回。"
      >
        <div class="flex flex-wrap gap-3">
          <YdButton v-auth="'system:template:export'" variant="outline" :loading="blankDownloading" @click="downloadBlank">
            <YdIcon name="morph:download" />
            下载空白模板
          </YdButton>
          <YdButton v-auth="'system:template:export'" variant="outline" :loading="exporting" @click="exportCurrent">
            <YdIcon name="morph:file-spreadsheet" />
            导出当前数据
          </YdButton>
        </div>
      </YdSettingsSection>

      <YdSettingsSection
        title="导入数据"
        description="选择 .xlsx 模板包导入。导入为受控覆盖：同标识记录按模板更新，模板外记录一律保留。"
      >
        <ul class="text-sm text-secondary-foreground/80 space-y-1.5 list-disc list-inside mb-4">
          <li>仅导入部门、角色、用户、菜单四类数据，且只写入模板中的受控字段，其余字段保持不变。</li>
          <li>系统数据模板包是完整快照：受控字段留空会同步清空目标值；目标环境已有、模板中不存在的记录不会被删除。含插件数据、附件与配置的整体迁移请使用「数据备份」生成的备份归档。</li>
          <li>新增用户完成导入后，密码初始化邮件进入发送队列（仅代表已入队，不代表邮件已送达）。</li>
          <li>单个记录失败不影响其余记录，失败原因在下方结果面板逐条展示。</li>
        </ul>
        <YdButton v-auth="'system:template:import'" :loading="importing" @click="startImport">
          <YdIcon name="morph:upload" />
          选择文件并导入
        </YdButton>

        <template v-if="lastResult">
          <YdDivider class="my-5" />
          <div class="flex flex-wrap gap-2 items-center justify-between mb-2">
            <div class="text-sm font-medium">
              上次导入结果
            </div>
            <YdTag :variant="lastResult.failed > 0 ? 'destructive' : 'default'">
              {{ importResultMessage(lastResult) }}
            </YdTag>
          </div>
          <div class="flex flex-wrap gap-2 items-center">
            <YdTag v-if="lastResult.created !== undefined" variant="outline">
              新增 {{ lastResult.created }}
            </YdTag>
            <YdTag v-if="lastResult.updated !== undefined" variant="outline">
              更新 {{ lastResult.updated }}
            </YdTag>
            <YdTag v-if="lastResult.skipped !== undefined" variant="outline">
              跳过 {{ lastResult.skipped }}
            </YdTag>
          </div>
          <div v-if="(lastResult.created ?? 0) > 0" class="text-xs text-secondary-foreground/60 mt-3">
            新导入用户的密码初始化邮件已进入发送队列；本页不承诺投递结果，请以用户收件情况为准。
          </div>
          <div v-if="lastResult.warnings?.length" class="text-xs text-secondary-foreground/70 mt-3">
            <div class="font-medium mb-1">
              提示
            </div>
            <ul class="list-disc list-inside space-y-0.5">
              <li v-for="(warning, index) in lastResult.warnings" :key="index">
                {{ warning }}
              </li>
            </ul>
          </div>
          <div v-if="lastResult.errors?.length" class="text-xs text-secondary-foreground/70 mt-3">
            <div class="font-medium mb-1">
              失败详情
            </div>
            <ul class="list-disc list-inside space-y-0.5">
              <li v-for="(error, index) in lastResult.errors" :key="index">
                {{ error }}
              </li>
            </ul>
          </div>
        </template>
      </YdSettingsSection>
    </div>
  </div>
</template>

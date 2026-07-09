import type {
  DetailForm,
  ProjectCheckIn,
  ProjectForm,
  ProjectProgressEvent,
  ProjectProgressProject,
  ProjectProgressStatus,
  ProjectWorkDetail,
} from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createProjectProgressApi } from '../api/project-progress-api'

export function useProjectProgress(sdk: YuDreamPluginSdk) {
  const api = createProjectProgressApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const status = ref<ProjectProgressStatus | null>(null)
  const projects = ref<ProjectProgressProject[]>([])
  const details = ref<ProjectWorkDetail[]>([])
  const claimableTasks = ref<ProjectWorkDetail[]>([])
  const myTasks = ref<ProjectWorkDetail[]>([])
  const pendingAcceptance = ref<ProjectWorkDetail[]>([])
  const checkIns = ref<ProjectCheckIn[]>([])
  const events = ref<ProjectProgressEvent[]>([])
  const selectedProjectId = ref('')
  const selectedDetailId = ref('')
  const selectedAcceptanceId = ref('')
  const evidenceFile = ref<File | null>(null)

  const projectForm = reactive<ProjectForm>({
    name: '',
    description: '',
    managerUserIds: '',
    memberUserIds: '',
    statusesText: 'TODO,未完成,false,10\nREVIEWING,复审中,false,20\nREPAIRING,修缮中,false,30\nDONE,完成,true,40',
    defaultStatusCode: 'TODO',
    doneStatusCode: 'DONE',
    reworkStatusCode: 'REPAIRING',
    minCheckInIntervalMinutes: 0,
    allowedCheckInTypes: ['IMAGE', 'FILE', 'LOCATION'],
    minecraftPolicy: {
      enabled: false,
      serverId: '',
      requiredOnlineMinutes: 30,
      includeAfk: false,
      autoCheckInEnabled: false,
    },
    enabled: true,
  })

  const detailForm = reactive<DetailForm>({
    title: '',
    description: '',
    statusCode: 'TODO',
    assignmentMode: 'CLAIM',
    requiredAssigneeCount: 1,
    candidateUserIds: '',
    assigneeUserIds: '',
    acceptorUserIds: '',
    dueAt: '',
  })

  const checkInForm = reactive({
    type: 'IMAGE',
    summary: '',
    address: '',
    latitude: '',
    longitude: '',
  })

  const acceptanceForm = reactive({
    reason: '',
    toStatusCode: '',
  })

  const selectedProject = computed(() => projects.value.find(item => item.id === selectedProjectId.value) || null)
  const selectedDetail = computed(() => details.value.find(item => item.id === selectedDetailId.value) || myTasks.value.find(item => item.id === selectedDetailId.value) || claimableTasks.value.find(item => item.id === selectedDetailId.value) || null)
  const completion = computed(() => {
    const doneCode = selectedProject.value?.doneStatusCode
    if (!details.value.length || !doneCode) {
      return 0
    }
    return Math.round((details.value.filter(item => item.statusCode === doneCode).length / details.value.length) * 100)
  })

  async function loadPage(page: string) {
    if (page === 'task-center') {
      await loadTaskCenter()
      return
    }
    if (page === 'my-tasks') {
      await loadMyTasks()
      return
    }
    if (page === 'acceptance') {
      await loadAcceptance()
      return
    }
    await load()
  }

  async function load() {
    loading.value = true
    try {
      const [nextStatus, nextProjects] = await Promise.all([api.status(), api.projects()])
      status.value = nextStatus
      projects.value = nextProjects
      if (!selectedProjectId.value && nextProjects.length) {
        selectProject(nextProjects[0])
      }
      if (selectedProjectId.value) {
        await reloadProjectData()
      }
    }
    finally {
      loading.value = false
    }
  }

  async function loadMyTasks() {
    loading.value = true
    try {
      myTasks.value = await api.myTasks()
      if (!selectedDetailId.value && myTasks.value.length) {
        selectedDetailId.value = myTasks.value[0].id
      }
      if (selectedDetailId.value) {
        await loadCheckIns(selectedDetailId.value)
      }
    }
    finally {
      loading.value = false
    }
  }

  async function loadTaskCenter() {
    loading.value = true
    try {
      const [nextStatus, nextProjects, nextClaimableTasks, nextMyTasks] = await Promise.all([
        api.status(),
        api.projects(),
        api.claimableTasks(),
        api.myTasks(),
      ])
      status.value = nextStatus
      projects.value = nextProjects
      claimableTasks.value = nextClaimableTasks
      myTasks.value = nextMyTasks
      if (!selectedDetailId.value) {
        selectedDetailId.value = nextMyTasks[0]?.id || nextClaimableTasks[0]?.id || ''
      }
      if (selectedDetailId.value && nextMyTasks.some(item => item.id === selectedDetailId.value)) {
        await loadCheckIns(selectedDetailId.value)
      }
    }
    finally {
      loading.value = false
    }
  }

  async function loadAcceptance() {
    loading.value = true
    try {
      pendingAcceptance.value = await api.pendingAcceptance()
      selectedAcceptanceId.value = selectedAcceptanceId.value || pendingAcceptance.value[0]?.id || ''
    }
    finally {
      loading.value = false
    }
  }

  async function reloadProjectData() {
    if (!selectedProjectId.value) {
      details.value = []
      events.value = []
      return
    }
    const [nextDetails, nextEvents] = await Promise.all([
      api.details(selectedProjectId.value),
      api.events(selectedProjectId.value),
    ])
    details.value = nextDetails
    events.value = nextEvents
    selectedDetailId.value = selectedDetailId.value || nextDetails[0]?.id || ''
  }

  function selectProject(project: ProjectProgressProject) {
    selectedProjectId.value = project.id
    fillProjectForm(project)
  }

  function selectDetail(detail: ProjectWorkDetail) {
    selectedDetailId.value = detail.id
    fillDetailForm(detail)
    void loadCheckIns(detail.id)
  }

  async function saveProject() {
    saving.value = true
    try {
      const payload = projectPayload()
      const saved = selectedProjectId.value
        ? await api.updateProject(selectedProjectId.value, payload)
        : await api.createProject(payload)
      upsert(projects.value, saved)
      selectProject(saved)
      toast.success('项目已保存')
      await reloadProjectData()
    }
    finally {
      saving.value = false
    }
  }

  async function deleteProject(project: ProjectProgressProject) {
    if (!confirmText(`确定删除项目「${project.name}」吗？`)) {
      return
    }
    saving.value = true
    try {
      await api.deleteProject(project.id)
      projects.value = projects.value.filter(item => item.id !== project.id)
      selectedProjectId.value = projects.value[0]?.id || ''
      toast.success('项目已删除')
      await reloadProjectData()
    }
    finally {
      saving.value = false
    }
  }

  async function saveDetail() {
    if (!selectedProjectId.value) {
      toast.warning('请先选择项目')
      return
    }
    saving.value = true
    try {
      const payload = detailPayload()
      const saved = selectedDetailId.value
        ? await api.updateDetail(selectedDetailId.value, payload)
        : await api.createDetail(selectedProjectId.value, payload)
      upsert(details.value, saved)
      selectDetail(saved)
      toast.success('工作细节已保存')
    }
    finally {
      saving.value = false
    }
  }

  async function publish(detail: ProjectWorkDetail) {
    const saved = await action(() => api.publishDetail(detail.id), '工作细节已发布')
    if (saved) {
      upsert(details.value, saved)
    }
  }

  async function randomAssign(detail: ProjectWorkDetail) {
    const saved = await action(() => api.randomAssign(detail.id), '任务已随机分配')
    if (saved) {
      upsert(details.value, saved)
    }
  }

  async function claim(detail: ProjectWorkDetail) {
    const saved = await action(() => api.claim(detail.id), '任务已认领')
    if (saved) {
      claimableTasks.value = claimableTasks.value.filter(item => item.id !== saved.id)
      upsert(myTasks.value, saved)
      upsert(details.value, saved)
    }
  }

  async function submitCheckIn() {
    if (!selectedDetail.value) {
      toast.warning('请选择工作细节')
      return
    }
    const files = evidenceFile.value
      ? [{
          filename: evidenceFile.value.name,
          contentType: evidenceFile.value.type || 'application/octet-stream',
          base64: await fileToBase64(evidenceFile.value),
          image: checkInForm.type === 'IMAGE',
        }]
      : []
    const record = await action(() => api.createCheckIn(selectedDetail.value!.id, {
      type: checkInForm.type,
      summary: checkInForm.summary,
      files,
      location: checkInForm.type === 'LOCATION'
        ? {
            address: checkInForm.address,
            latitude: toNumber(checkInForm.latitude),
            longitude: toNumber(checkInForm.longitude),
          }
        : null,
    }), '打卡已提交')
    if (record) {
      await loadCheckIns(selectedDetail.value.id)
    }
  }

  async function minecraftCheckIn(detail: ProjectWorkDetail) {
    const record = await action(() => api.minecraftCheckIn(detail.id), 'Minecraft 在线时长打卡已生成')
    if (record) {
      selectedDetailId.value = detail.id
      await loadCheckIns(detail.id)
    }
  }

  async function autoMinecraftCheckIns() {
    if (!selectedProjectId.value) {
      toast.warning('请先选择项目')
      return
    }
    await action(() => api.autoMinecraftCheckIns(selectedProjectId.value), '自动打卡检查已完成')
  }

  async function review(detail: ProjectWorkDetail, accepted: boolean) {
    const request = accepted ? api.accept : api.reject
    const record = await action(() => request(detail.id, {
      reason: acceptanceForm.reason,
      toStatusCode: acceptanceForm.toStatusCode || undefined,
    }), accepted ? '验收已通过' : '已退回返工')
    if (record) {
      pendingAcceptance.value = pendingAcceptance.value.filter(item => item.id !== detail.id)
      await reloadProjectData()
    }
  }

  async function loadCheckIns(detailId: string) {
    checkIns.value = await api.checkIns(detailId)
  }

  async function action<T>(fn: () => Promise<T>, success: string) {
    saving.value = true
    try {
      const result = await fn()
      toast.success(success)
      return result
    }
    finally {
      saving.value = false
    }
  }

  function newProject() {
    selectedProjectId.value = ''
    Object.assign(projectForm, defaultProjectForm())
    details.value = []
    events.value = []
  }

  function newDetail() {
    selectedDetailId.value = ''
    Object.assign(detailForm, defaultDetailForm(selectedProject.value?.defaultStatusCode || 'TODO'))
  }

  function fillProjectForm(project: ProjectProgressProject) {
    projectForm.name = project.name
    projectForm.description = project.description
    projectForm.managerUserIds = project.managerUserIds.join(',')
    projectForm.memberUserIds = project.memberUserIds.join(',')
    projectForm.statusesText = project.statuses.map(item => `${item.code},${item.label},${item.terminal},${item.sort}`).join('\n')
    projectForm.defaultStatusCode = project.defaultStatusCode
    projectForm.doneStatusCode = project.doneStatusCode
    projectForm.reworkStatusCode = project.reworkStatusCode || ''
    projectForm.minCheckInIntervalMinutes = project.minCheckInIntervalMinutes
    projectForm.allowedCheckInTypes = [...project.allowedCheckInTypes]
    projectForm.minecraftPolicy = { ...project.minecraftPolicy }
    projectForm.enabled = project.enabled
  }

  function fillDetailForm(detail: ProjectWorkDetail) {
    detailForm.title = detail.title
    detailForm.description = detail.description
    detailForm.statusCode = detail.statusCode
    detailForm.assignmentMode = detail.assignmentMode
    detailForm.requiredAssigneeCount = detail.requiredAssigneeCount
    detailForm.candidateUserIds = detail.candidateUserIds.join(',')
    detailForm.assigneeUserIds = detail.assigneeUserIds.join(',')
    detailForm.acceptorUserIds = detail.acceptorUserIds.join(',')
    detailForm.dueAt = detail.dueAt ? new Date(detail.dueAt).toISOString().slice(0, 16) : ''
  }

  function projectPayload() {
    return {
      name: projectForm.name,
      description: projectForm.description,
      managerUserIds: splitIds(projectForm.managerUserIds),
      memberUserIds: splitIds(projectForm.memberUserIds),
      statuses: parseStatuses(projectForm.statusesText),
      defaultStatusCode: projectForm.defaultStatusCode,
      doneStatusCode: projectForm.doneStatusCode,
      reworkStatusCode: projectForm.reworkStatusCode,
      minCheckInIntervalMinutes: projectForm.minCheckInIntervalMinutes,
      allowedCheckInTypes: projectForm.allowedCheckInTypes,
      minecraftPolicy: projectForm.minecraftPolicy,
      enabled: projectForm.enabled,
    }
  }

  function detailPayload() {
    return {
      title: detailForm.title,
      description: detailForm.description,
      statusCode: detailForm.statusCode,
      assignmentMode: detailForm.assignmentMode,
      requiredAssigneeCount: detailForm.requiredAssigneeCount,
      candidateUserIds: splitIds(detailForm.candidateUserIds),
      assigneeUserIds: splitIds(detailForm.assigneeUserIds),
      acceptorUserIds: splitIds(detailForm.acceptorUserIds),
      dueAt: detailForm.dueAt ? new Date(detailForm.dueAt).getTime() : null,
    }
  }

  function formatTime(value?: number | null) {
    return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
  }

  function minutes(value: number) {
    return `${Math.floor(value / 60000)} 分钟`
  }

  function projectName(projectId: string) {
    return projects.value.find(item => item.id === projectId)?.name || projectId
  }

  function canMinecraftCheckIn(detail: ProjectWorkDetail) {
    const project = projects.value.find(item => item.id === detail.projectId)
    return !!project?.minecraftPolicy.enabled && project.allowedCheckInTypes.includes('MINECRAFT_ONLINE')
  }

  return reactive({
    loading,
    saving,
    status,
    projects,
    details,
    claimableTasks,
    myTasks,
    pendingAcceptance,
    checkIns,
    events,
    selectedProjectId,
    selectedDetailId,
    selectedAcceptanceId,
    selectedProject,
    selectedDetail,
    completion,
    projectForm,
    detailForm,
    checkInForm,
    acceptanceForm,
    evidenceFile,
    loadPage,
    load,
    loadTaskCenter,
    reloadProjectData,
    loadMyTasks,
    loadAcceptance,
    selectProject,
    selectDetail,
    saveProject,
    deleteProject,
    saveDetail,
    publish,
    randomAssign,
    claim,
    submitCheckIn,
    minecraftCheckIn,
    autoMinecraftCheckIns,
    review,
    newProject,
    newDetail,
    formatTime,
    minutes,
    projectName,
    canMinecraftCheckIn,
  })
}

function defaultProjectForm(): ProjectForm {
  return {
    name: '',
    description: '',
    managerUserIds: '',
    memberUserIds: '',
    statusesText: 'TODO,未完成,false,10\nREVIEWING,复审中,false,20\nREPAIRING,修缮中,false,30\nDONE,完成,true,40',
    defaultStatusCode: 'TODO',
    doneStatusCode: 'DONE',
    reworkStatusCode: 'REPAIRING',
    minCheckInIntervalMinutes: 0,
    allowedCheckInTypes: ['IMAGE', 'FILE', 'LOCATION'],
    minecraftPolicy: {
      enabled: false,
      serverId: '',
      requiredOnlineMinutes: 30,
      includeAfk: false,
      autoCheckInEnabled: false,
    },
    enabled: true,
  }
}

function defaultDetailForm(statusCode: string): DetailForm {
  return {
    title: '',
    description: '',
    statusCode,
    assignmentMode: 'CLAIM',
    requiredAssigneeCount: 1,
    candidateUserIds: '',
    assigneeUserIds: '',
    acceptorUserIds: '',
    dueAt: '',
  }
}

function splitIds(value: string) {
  return value.split(/[,，\s]+/).map(item => item.trim()).filter(Boolean)
}

function parseStatuses(value: string) {
  return value.split('\n')
    .map(line => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [code, label, terminal = 'false', sort = '0'] = line.split(',').map(item => item.trim())
      return { code, label, terminal: terminal === 'true', sort: Number(sort) || 0 }
    })
}

function upsert<T extends { id: string }>(items: T[], item: T) {
  const index = items.findIndex(row => row.id === item.id)
  if (index >= 0) {
    items.splice(index, 1, item)
    return
  }
  items.unshift(item)
}

function confirmText(message: string) {
  return typeof window === 'undefined' || window.confirm(message)
}

function toNumber(value: string) {
  const number = Number(value)
  return Number.isFinite(number) ? number : undefined
}

function fileToBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const value = String(reader.result || '')
      const commaIndex = value.indexOf(',')
      resolve(commaIndex >= 0 ? value.slice(commaIndex + 1) : value)
    }
    reader.onerror = () => reject(reader.error || new Error('文件读取失败'))
    reader.readAsDataURL(file)
  })
}

export type ProjectProgressModel = ReturnType<typeof useProjectProgress>

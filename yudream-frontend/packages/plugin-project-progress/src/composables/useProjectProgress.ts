import type {
  AcceptanceSubmitForm,
  DetailForm,
  ProjectCheckIn,
  ProjectDeptOption,
  ProjectForm,
  ProjectMinecraftServerOption,
  ProjectProgressEvent,
  ProjectProgressProject,
  ProjectProgressStatus,
  ProjectStatusOption,
  ProjectUserOption,
  ProjectWorkDetail,
} from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createProjectProgressApi } from '../api/project-progress-api'

const DEFAULT_STATUSES_TEXT = 'TODO,未完成,false,10\nREVIEWING,复审中,false,20\nREPAIRING,修缮中,false,30\nDONE,完成,true,40'

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
  const departments = ref<ProjectDeptOption[]>([])
  const minecraftServers = ref<ProjectMinecraftServerOption[]>([])
  const usersById = ref<Record<string, ProjectUserOption>>({})
  const selectedProjectId = ref('')
  const selectedDetailId = ref('')
  const selectedAcceptanceId = ref('')
  const evidenceFile = ref<File | null>(null)
  const evidenceFiles = ref<Array<{ name: string, size: number, status?: 'uploading' | 'success' | 'error', progress?: number, file?: File }>>([])

  const projectForm = reactive<ProjectForm>({
    name: '',
    description: '',
    managerUserIds: [],
    memberUserIds: [],
    statusesText: DEFAULT_STATUSES_TEXT,
    defaultStatusCode: 'TODO',
    doneStatusCode: 'DONE',
    reworkStatusCode: 'REPAIRING',
    minCheckInIntervalMinutes: 1440,
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
    candidateScope: 'ALL',
    requiredAssigneeCount: 1,
    candidateUserIds: [],
    assigneeUserIds: [],
    acceptorUserIds: [],
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

  const acceptanceSubmitForm = reactive<AcceptanceSubmitForm>({
    summary: '',
  })
  const acceptanceFiles = ref<Array<{ name: string, size: number, status?: 'uploading' | 'success' | 'error', progress?: number, file?: File }>>([])

  const selectedProject = computed(() => projects.value.find(item => item.id === selectedProjectId.value) || null)
  const selectedDetail = computed(() => details.value.find(item => item.id === selectedDetailId.value) || myTasks.value.find(item => item.id === selectedDetailId.value) || claimableTasks.value.find(item => item.id === selectedDetailId.value) || null)
  const projectStatusOptions = computed(() => selectedProject.value?.statuses || parseStatuses(projectForm.statusesText))
  const completion = computed(() => {
    const doneCode = selectedProject.value?.doneStatusCode
    if (!details.value.length || !doneCode) {
      return 0
    }
    return Math.round((details.value.filter(item => item.statusCode === doneCode).length / details.value.length) * 100)
  })
  const recentEvents = computed(() => events.value.slice(0, 20))

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
      const [nextStatus, nextProjects, nextMinecraftServers] = await Promise.all([
        api.status(),
        api.projects(),
        api.minecraftServers(),
      ])
      status.value = nextStatus
      projects.value = nextProjects
      minecraftServers.value = nextMinecraftServers
      await resolveProjectUsers(nextProjects)
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
      const [nextStatus, nextProjects, nextMyTasks] = await Promise.all([
        api.status(),
        api.projects(),
        api.myTasks(),
      ])
      status.value = nextStatus
      projects.value = nextProjects
      myTasks.value = nextMyTasks
      await resolveProjectUsers(nextProjects)
      await resolveDetailUsers(myTasks.value)
      if (!selectedDetailId.value && myTasks.value.length) {
        selectedDetailId.value = myTasks.value[0].id
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
      await Promise.all([
        resolveProjectUsers(nextProjects),
        resolveDetailUsers([...nextClaimableTasks, ...nextMyTasks]),
      ])
      if (!selectedDetailId.value) {
        selectedDetailId.value = nextMyTasks[0]?.id || nextClaimableTasks[0]?.id || ''
      }
    }
    finally {
      loading.value = false
    }
  }

  async function loadAcceptance() {
    loading.value = true
    try {
      const [nextStatus, nextProjects, nextPendingAcceptance] = await Promise.all([
        api.status(),
        api.projects(),
        api.pendingAcceptance(),
      ])
      status.value = nextStatus
      projects.value = nextProjects
      pendingAcceptance.value = nextPendingAcceptance
      await resolveProjectUsers(nextProjects)
      await resolveDetailUsers(pendingAcceptance.value)
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
      checkIns.value = []
      return
    }
    const [nextDetails, nextEvents, nextCheckIns] = await Promise.all([
      api.details(selectedProjectId.value),
      api.events(selectedProjectId.value),
      api.projectCheckIns(selectedProjectId.value),
    ])
    details.value = nextDetails
    events.value = nextEvents
    checkIns.value = nextCheckIns
    await resolveDetailUsers(nextDetails)
    await resolveUsers(nextCheckIns.map(item => item.userId))
    selectedDetailId.value = selectedDetailId.value || nextDetails[0]?.id || ''
  }

  function selectProject(project: ProjectProgressProject) {
    selectedProjectId.value = project.id
    fillProjectForm(project)
  }

  async function selectProjectById(projectId: string) {
    if (!projectId) {
      selectedProjectId.value = ''
      await reloadProjectData()
      return
    }
    const project = projects.value.find(item => item.id === projectId)
    if (!project) {
      selectedProjectId.value = projectId
      return
    }
    selectProject(project)
    await reloadProjectData()
  }

  function selectDetail(detail: ProjectWorkDetail) {
    selectedDetailId.value = detail.id
    fillDetailForm(detail)
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
      syncProjectMembersFromDetail(saved)
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
      syncProjectMembersFromDetail(saved)
      fillDetailForm(saved)
    }
  }

  async function randomAssign(detail: ProjectWorkDetail) {
    const saved = await action(() => api.randomAssign(detail.id), '任务已随机分配')
    if (saved) {
      upsert(details.value, saved)
      syncProjectMembersFromDetail(saved)
      fillDetailForm(saved)
    }
  }

  async function claim(detail: ProjectWorkDetail) {
    const saved = await action(() => api.claim(detail.id), '任务已认领')
    if (saved) {
      claimableTasks.value = claimableTasks.value.filter(item => item.id !== saved.id)
      upsert(myTasks.value, saved)
      upsert(details.value, saved)
      syncProjectMembersFromDetail(saved)
    }
  }

  async function submitCheckIn() {
    const projectId = selectedProjectId.value
    if (!projectId) {
      toast.warning('请先选择项目')
      return
    }
    const selectedFiles = evidenceFiles.value.map(item => item.file).filter((file): file is File => !!file)
    if (!selectedFiles.length && evidenceFile.value) {
      selectedFiles.push(evidenceFile.value)
    }
    const files = selectedFiles.length
      ? await Promise.all(selectedFiles.map(async file => ({
          filename: file.name,
          contentType: file.type || 'application/octet-stream',
          base64: await fileToBase64(file),
          image: checkInForm.type === 'IMAGE',
        })))
      : []
    const record = await action(() => api.createProjectCheckIn(projectId, {
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
    }), '项目打卡已提交')
    if (record) {
      evidenceFile.value = null
      evidenceFiles.value = []
      await loadProjectCheckIns(projectId)
    }
  }

  async function useCurrentLocation() {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      toast.warning('当前浏览器不支持定位')
      return
    }
    saving.value = true
    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 12000,
          maximumAge: 30000,
        })
      })
      const latitude = Number(position.coords.latitude.toFixed(6))
      const longitude = Number(position.coords.longitude.toFixed(6))
      checkInForm.latitude = String(latitude)
      checkInForm.longitude = String(longitude)
      checkInForm.address = checkInForm.address || `浏览器定位：${latitude}, ${longitude}`
      toast.success('定位已获取')
    }
    catch {
      toast.error('定位失败，请允许浏览器定位权限后重试')
    }
    finally {
      saving.value = false
    }
  }

  async function minecraftCheckIn(projectId = selectedProjectId.value) {
    if (!projectId) {
      toast.warning('请先选择项目')
      return
    }
    const record = await action(() => api.projectMinecraftCheckIn(projectId), 'Minecraft 在线时长打卡已生成')
    if (record) {
      selectedProjectId.value = projectId
      await loadProjectCheckIns(projectId)
    }
  }

  async function autoMinecraftCheckIns() {
    const projectId = selectedProjectId.value
    if (!projectId) {
      toast.warning('请先选择项目')
      return
    }
    await action(() => api.autoMinecraftCheckIns(projectId), '自动打卡检查已完成')
    await loadProjectCheckIns(projectId)
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

  async function submitAcceptance(detail: ProjectWorkDetail) {
    if (!acceptanceSubmitForm.summary.trim()) {
      toast.warning('请填写验收说明')
      return false
    }
    const files = acceptanceFiles.value.filter(item => item.file)
    if (!files.length) {
      toast.warning('请上传验收附件')
      return false
    }
    const payloadFiles = await Promise.all(files.map(async item => ({
      filename: item.file!.name,
      contentType: item.file!.type || 'application/octet-stream',
      base64: await fileToBase64(item.file!),
      image: item.file!.type.startsWith('image/'),
    })))
    const saved = await action(() => api.submitAcceptance(detail.id, {
      summary: acceptanceSubmitForm.summary,
      files: payloadFiles,
    }), '任务已提交验收')
    if (saved) {
      upsert(myTasks.value, saved)
      upsert(details.value, saved)
      selectedDetailId.value = saved.id
      acceptanceSubmitForm.summary = ''
      acceptanceFiles.value = []
      return true
    }
    return false
  }

  async function loadProjectCheckIns(projectId = selectedProjectId.value) {
    checkIns.value = projectId ? await api.projectCheckIns(projectId) : []
    await resolveUsers(checkIns.value.map(item => item.userId))
  }

  async function searchUsers(keyword = '', deptId = '') {
    const users = await api.users(keyword, deptId)
    rememberUsers(users)
    return users
  }

  async function searchUsersPage(keyword = '', deptId = '', page = 1, size = 10) {
    const users = await api.usersPage(keyword, deptId, page, size)
    rememberUsers(users)
    return users
  }

  async function resolveUsers(ids: string[]) {
    const missing = unique(ids).filter(id => !usersById.value[id])
    if (!missing.length) {
      return
    }
    rememberUsers(await api.resolveUsers(missing))
  }

  async function loadDepartments(keyword = '') {
    departments.value = await api.departments(keyword)
    return departments.value
  }

  async function loadDepartmentUsers(deptId: string) {
    return searchUsers('', deptId)
  }

  async function loadMinecraftServers(includeDisabled = false) {
    minecraftServers.value = await api.minecraftServers(includeDisabled)
    return minecraftServers.value
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
    projectForm.managerUserIds = [...project.managerUserIds]
    projectForm.memberUserIds = [...project.memberUserIds]
    projectForm.statusesText = project.statuses.map(item => `${item.code},${item.label},${item.terminal},${item.sort}`).join('\n')
    projectForm.defaultStatusCode = project.defaultStatusCode
    projectForm.doneStatusCode = project.doneStatusCode
    projectForm.reworkStatusCode = project.reworkStatusCode || ''
    projectForm.minCheckInIntervalMinutes = project.minCheckInIntervalMinutes
    projectForm.allowedCheckInTypes = [...project.allowedCheckInTypes]
    projectForm.minecraftPolicy = { ...project.minecraftPolicy }
    projectForm.enabled = project.enabled
    void resolveUsers([...project.managerUserIds, ...project.memberUserIds])
  }

  function fillDetailForm(detail: ProjectWorkDetail) {
    detailForm.title = detail.title
    detailForm.description = detail.description
    detailForm.statusCode = detail.statusCode
    detailForm.assignmentMode = detail.assignmentMode
    detailForm.candidateScope = detail.assignmentMode === 'RANDOM' && detail.candidateUserIds.length === 0
      ? 'PROJECT_MEMBERS'
      : detail.candidateUserIds.length === 0 ? 'ALL' : 'SELECTED'
    detailForm.requiredAssigneeCount = detail.requiredAssigneeCount
    detailForm.candidateUserIds = [...detail.candidateUserIds]
    detailForm.assigneeUserIds = [...detail.assigneeUserIds]
    detailForm.acceptorUserIds = [...detail.acceptorUserIds]
    detailForm.dueAt = detail.dueAt ? new Date(detail.dueAt).toISOString().slice(0, 16) : ''
    void resolveUsers([...detail.candidateUserIds, ...detail.assigneeUserIds, ...detail.acceptorUserIds])
  }

  function projectPayload() {
    return {
      name: projectForm.name,
      description: projectForm.description,
      managerUserIds: unique(projectForm.managerUserIds),
      memberUserIds: unique(projectForm.memberUserIds),
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
      candidateUserIds: detailCandidateUserIds(),
      assigneeUserIds: unique(detailForm.assigneeUserIds),
      acceptorUserIds: unique(detailForm.acceptorUserIds),
      dueAt: toTimestamp(detailForm.dueAt),
    }
  }

  function detailCandidateUserIds() {
    if (detailForm.assignmentMode === 'CLAIM' && detailForm.candidateScope === 'ALL') {
      return []
    }
    if (detailForm.assignmentMode === 'RANDOM' && detailForm.candidateScope === 'PROJECT_MEMBERS') {
      return []
    }
    return unique(detailForm.candidateUserIds)
  }

  function rememberUsers(users: ProjectUserOption[]) {
    if (!users.length) {
      return
    }
    const next = { ...usersById.value }
    users.forEach((user) => {
      next[user.id] = user
    })
    usersById.value = next
  }

  function userOptionsForIds(ids: string[]) {
    return unique(ids).map(id => usersById.value[id] || {
      id,
      username: '',
      nickname: '加载中',
      email: '',
      avatar: '',
      status: '',
      deptIds: [],
      deptNames: [],
    })
  }

  function userLabel(user?: ProjectUserOption | null) {
    if (!user) {
      return '未知用户'
    }
    return user.nickname || user.username || user.email || '未知用户'
  }

  function userMeta(user?: ProjectUserOption | null) {
    if (!user) {
      return ''
    }
    const account = user.username || user.email
    const dept = user.deptNames?.join(' / ')
    return [account, dept].filter(Boolean).join(' · ')
  }

  function statusLabel(code?: string | null) {
    if (!code) {
      return '-'
    }
    return projectStatusOptions.value.find(item => item.code === code)?.label || code
  }

  function detailStatusLabel(detail: ProjectWorkDetail) {
    const project = projects.value.find(item => item.id === detail.projectId)
    return project?.statuses.find(item => item.code === detail.statusCode)?.label || statusLabel(detail.statusCode)
  }

  function assignmentLabel(detail: ProjectWorkDetail) {
    if (detail.assignmentMode === 'RANDOM') {
      return detail.candidateUserIds.length ? '指定人员随机' : '项目成员随机'
    }
    return detail.candidateUserIds.length ? '指定人员认领' : '公开认领'
  }

  function projectMemberIds(project: ProjectProgressProject) {
    return unique([...project.managerUserIds, ...project.memberUserIds])
  }

  function projectMemberCount(project: ProjectProgressProject) {
    return projectMemberIds(project).length
  }

  function syncProjectMembersFromDetail(detail: ProjectWorkDetail) {
    const project = projects.value.find(item => item.id === detail.projectId)
    if (!project || !detail.assigneeUserIds.length) {
      return
    }
    project.memberUserIds = unique([...project.memberUserIds, ...project.managerUserIds, ...detail.assigneeUserIds])
    void resolveUsers(project.memberUserIds)
  }

  function formatTime(value?: number | string | null) {
    const timestamp = toTimestamp(value)
    if (!timestamp) {
      return '-'
    }
    const date = new Date(timestamp)
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN', { hour12: false })
  }

  function minutes(value: number) {
    return `${Math.floor(value / 60000)} 分钟`
  }

  function projectName(projectId: string) {
    return projects.value.find(item => item.id === projectId)?.name || '未知项目'
  }

  function serverLabel(serverId?: string | null) {
    if (!serverId) {
      return '未选择服务器'
    }
    return minecraftServers.value.find(item => item.id === serverId)?.name || '已选择服务器'
  }

  function canProjectMinecraftCheckIn(projectId = selectedProjectId.value) {
    const project = projects.value.find(item => item.id === projectId)
    return !!project?.minecraftPolicy.enabled && project.allowedCheckInTypes.includes('MINECRAFT_ONLINE')
  }

  function canMinecraftCheckIn(detail: ProjectWorkDetail) {
    return canProjectMinecraftCheckIn(detail.projectId)
  }

  function canSubmitAcceptance(detail: ProjectWorkDetail) {
    const project = projects.value.find(item => item.id === detail.projectId)
    return !!project && detail.published && !detail.pendingAcceptance && detail.statusCode !== project.doneStatusCode
  }

  async function resolveProjectUsers(items: ProjectProgressProject[]) {
    await resolveUsers(items.flatMap(item => [...item.managerUserIds, ...item.memberUserIds]))
  }

  async function resolveDetailUsers(items: ProjectWorkDetail[]) {
    await resolveUsers(items.flatMap(item => [...item.candidateUserIds, ...item.assigneeUserIds, ...item.acceptorUserIds]))
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
    departments,
    minecraftServers,
    usersById,
    selectedProjectId,
    selectedDetailId,
    selectedAcceptanceId,
    selectedProject,
    selectedDetail,
    projectStatusOptions,
    completion,
    recentEvents,
    projectForm,
    detailForm,
    checkInForm,
    acceptanceForm,
    acceptanceSubmitForm,
    evidenceFile,
    evidenceFiles,
    acceptanceFiles,
    loadPage,
    load,
    loadTaskCenter,
    reloadProjectData,
    loadMyTasks,
    loadAcceptance,
    selectProject,
    selectProjectById,
    selectDetail,
    saveProject,
    deleteProject,
    saveDetail,
    publish,
    randomAssign,
    claim,
    submitCheckIn,
    useCurrentLocation,
    minecraftCheckIn,
    autoMinecraftCheckIns,
    submitAcceptance,
    review,
    loadProjectCheckIns,
    newProject,
    newDetail,
    searchUsers,
    searchUsersPage,
    resolveUsers,
    loadDepartments,
    loadDepartmentUsers,
    loadMinecraftServers,
    userOptionsForIds,
    userLabel,
    userMeta,
    statusLabel,
    detailStatusLabel,
    assignmentLabel,
    projectMemberCount,
    formatTime,
    minutes,
    projectName,
    serverLabel,
    canProjectMinecraftCheckIn,
    canMinecraftCheckIn,
    canSubmitAcceptance,
  })
}

function defaultProjectForm(): ProjectForm {
  return {
    name: '',
    description: '',
    managerUserIds: [],
    memberUserIds: [],
    statusesText: DEFAULT_STATUSES_TEXT,
    defaultStatusCode: 'TODO',
    doneStatusCode: 'DONE',
    reworkStatusCode: 'REPAIRING',
    minCheckInIntervalMinutes: 1440,
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
    candidateScope: 'ALL',
    requiredAssigneeCount: 1,
    candidateUserIds: [],
    assigneeUserIds: [],
    acceptorUserIds: [],
    dueAt: '',
  }
}

function unique(values: string[]) {
  return Array.from(new Set(values.map(item => item.trim()).filter(Boolean)))
}

function parseStatuses(value: string): ProjectStatusOption[] {
  const result = value.split('\n')
    .map(line => line.trim())
    .filter(Boolean)
    .map((line, index) => {
      const [code, label, terminal = 'false', sort = String((index + 1) * 10)] = line.split(',').map(item => item.trim())
      return {
        code: (code || `STATUS_${index + 1}`).toUpperCase(),
        label: label || code || `状态 ${index + 1}`,
        terminal: terminal === 'true',
        sort: Number(sort) || (index + 1) * 10,
      }
    })
  return result.length ? result : parseStatuses(DEFAULT_STATUSES_TEXT)
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

function toTimestamp(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? value : null
  }
  const normalized = value.includes(' ') ? value.replace(' ', 'T') : value
  const timestamp = new Date(normalized).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
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

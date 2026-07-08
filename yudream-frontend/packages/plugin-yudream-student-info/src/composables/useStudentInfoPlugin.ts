import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { StudentInfoSummary, StudentProfile, StudentProfileFilters, StudentProfileForm, TimeValue } from '../types'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createStudentInfoApi } from '../api/student-info-api'

const MANAGE_PERMISSION = 'plugin:yudream-student-info:manage'

export function useStudentInfoPlugin(sdk: YuDreamPluginSdk) {
  const api = createStudentInfoApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const summary = ref<StudentInfoSummary | null>(null)
  const profile = ref<StudentProfile | null>(null)
  const profiles = ref<StudentProfile[]>([])
  const pager = reactive({ page: 1, size: 20, hasNext: false })
  const filters = reactive<StudentProfileFilters>({
    keyword: '',
    college: '',
    className: '',
  })
  const profileForm = reactive<StudentProfileForm>({
    userId: sdk.account.userId,
    studentNo: '',
    className: '',
    college: '',
  })
  const adminForm = reactive<StudentProfileForm>({
    userId: '',
    studentNo: '',
    className: '',
    college: '',
  })
  const editingUserId = ref('')

  const canManage = computed(() => sdk.account.permissions.includes('*') || sdk.account.permissions.includes(MANAGE_PERMISSION))
  const accountName = computed(() => sdk.account.username || `用户 ${sdk.account.userId}`)
  const profileReady = computed(() => !!profile.value?.studentNo && !!profile.value?.className && !!profile.value?.college)

  async function load() {
    loading.value = true
    try {
      const [nextSummary, nextProfile] = await Promise.all([api.status(), api.me()])
      summary.value = nextSummary
      profile.value = nextProfile
      syncProfileForm(nextProfile)
      if (canManage.value) {
        await loadProfiles()
      }
    }
    finally {
      loading.value = false
    }
  }

  async function saveMine() {
    if (!validProfileForm(profileForm)) {
      return
    }
    saving.value = true
    try {
      profile.value = await api.saveMe({
        studentNo: profileForm.studentNo.trim(),
        className: profileForm.className.trim(),
        college: profileForm.college.trim(),
      })
      toast.success('学生信息已保存')
      if (canManage.value) {
        await loadProfiles()
      }
    }
    finally {
      saving.value = false
    }
  }

  async function loadProfiles() {
    const items = await api.profiles(filters, pager.page, pager.size)
    profiles.value = items
    pager.hasNext = items.length >= pager.size
  }

  async function applyFilters() {
    pager.page = 1
    await loadProfiles()
  }

  async function resetFilters() {
    filters.keyword = ''
    filters.college = ''
    filters.className = ''
    pager.page = 1
    await loadProfiles()
  }

  async function nextPage() {
    if (!pager.hasNext) {
      return
    }
    pager.page += 1
    await loadProfiles()
  }

  async function prevPage() {
    if (pager.page <= 1) {
      return
    }
    pager.page -= 1
    await loadProfiles()
  }

  function editProfile(row: StudentProfile) {
    editingUserId.value = row.userId
    Object.assign(adminForm, {
      userId: row.userId,
      studentNo: row.studentNo || '',
      className: row.className || '',
      college: row.college || '',
    })
  }

  function resetAdminForm() {
    editingUserId.value = ''
    Object.assign(adminForm, {
      userId: '',
      studentNo: '',
      className: '',
      college: '',
    })
  }

  async function saveAdmin() {
    if (!adminForm.userId.trim()) {
      toast.warning('请填写用户 ID')
      return
    }
    if (!validProfileForm(adminForm)) {
      return
    }
    saving.value = true
    try {
      const saved = await api.saveProfile({
        userId: adminForm.userId.trim(),
        studentNo: adminForm.studentNo.trim(),
        className: adminForm.className.trim(),
        college: adminForm.college.trim(),
      })
      toast.success('学生档案已保存')
      if (saved.userId === sdk.account.userId) {
        profile.value = saved
        syncProfileForm(saved)
      }
      resetAdminForm()
      await loadProfiles()
    }
    finally {
      saving.value = false
    }
  }

  async function deleteProfile(row: StudentProfile) {
    if (typeof window !== 'undefined' && !window.confirm(`确认删除用户 ${displayName(row)} 的学生档案吗？`)) {
      return
    }
    saving.value = true
    try {
      await api.deleteProfile(row.userId)
      toast.success('学生档案已删除')
      if (row.userId === sdk.account.userId) {
        profile.value = await api.me()
        syncProfileForm(profile.value)
      }
      await loadProfiles()
    }
    finally {
      saving.value = false
    }
  }

  function syncProfileForm(nextProfile: StudentProfile | null) {
    profileForm.userId = sdk.account.userId
    profileForm.studentNo = nextProfile?.studentNo || ''
    profileForm.className = nextProfile?.className || ''
    profileForm.college = nextProfile?.college || ''
  }

  function validProfileForm(form: StudentProfileForm) {
    if (!form.studentNo.trim() || !form.className.trim() || !form.college.trim()) {
      toast.warning('请填写学号、班级和学院')
      return false
    }
    return true
  }

  function displayName(row?: StudentProfile | null) {
    if (!row) {
      return '-'
    }
    return row.nickname || row.username || row.email || `用户 ${row.userId}`
  }

  function profileStatus(row?: StudentProfile | null) {
    return row?.studentNo && row?.className && row?.college ? '已完善' : '未填写'
  }

  function formatTime(value?: TimeValue) {
    const timestamp = normalizeTime(value)
    if (!timestamp) {
      return '-'
    }
    return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
  }

  return reactive({
    loading,
    saving,
    summary,
    profile,
    profiles,
    pager,
    filters,
    profileForm,
    adminForm,
    editingUserId,
    canManage,
    accountName,
    profileReady,
    load,
    saveMine,
    loadProfiles,
    applyFilters,
    resetFilters,
    nextPage,
    prevPage,
    editProfile,
    resetAdminForm,
    saveAdmin,
    deleteProfile,
    displayName,
    profileStatus,
    formatTime,
  })
}

function normalizeTime(value: TimeValue) {
  if (value == null || value === '') {
    return 0
  }
  if (value instanceof Date) {
    return validTimestamp(value.getTime())
  }
  if (Array.isArray(value)) {
    return normalizeDateArray(value)
  }
  if (typeof value === 'number') {
    return validTimestamp(value)
  }
  const numeric = Number(value.trim())
  if (Number.isFinite(numeric)) {
    return validTimestamp(numeric)
  }
  return validTimestamp(Date.parse(value))
}

function normalizeDateArray(value: number[]) {
  if (value.length < 3) {
    return 0
  }
  const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = value
  return validTimestamp(new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000)).getTime())
}

function validTimestamp(value: number) {
  if (!Number.isFinite(value) || value <= 0) {
    return 0
  }
  return value < 10000000000 ? value * 1000 : value
}

export type StudentInfoPluginModel = ReturnType<typeof useStudentInfoPlugin>

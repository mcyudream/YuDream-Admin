export interface StudentProfile {
  userId: string
  username?: string
  nickname?: string
  email?: string
  studentNo?: string | null
  className?: string | null
  college?: string | null
  createdAt?: TimeValue
  updatedAt?: TimeValue
}

export interface StudentInfoSummary {
  profileCount: number
}

export interface StudentProfileForm {
  userId: string
  studentNo: string
  className: string
  college: string
}

export interface StudentProfileFilters {
  keyword: string
  college: string
  className: string
}

export type TimeValue = number | string | number[] | Date | null | undefined

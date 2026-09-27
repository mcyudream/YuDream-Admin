import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface NotificationItem {
  id: string
  sourcePlugin?: string
  type?: string
  title: string
  body?: string
  link?: string
  read: boolean
  createdAt?: string
}

export default {
  page: (params: { page: number, size: number }) => {
    return systemClient.get<unknown, ApiResponse<PageResult<NotificationItem>>>('api/notifications', { params })
  },
  unreadCount: () => {
    return systemClient.get<unknown, ApiResponse<{ unread: number }>>('api/notifications/unread-count')
  },
  markRead: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/notifications/${id}/read`)
  },
  markAllRead: () => {
    return systemClient.post<unknown, ApiResponse<{ updated: number }>>('api/notifications/read-all')
  },
}

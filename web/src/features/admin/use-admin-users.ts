import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { AdminUser } from '@/api/types'
export type { AdminUser } from '@/api/types'
export type { AdminUserImportParseResult, AdminUserImportResult } from '@/api/types'

/**
 * Admin user-management hooks for listing users and mutating their role or account status.
 */
export interface AdminUsersParams {
  search?: string
  status?: string
  page?: number
  size?: number
}

export interface PagedAdminUsers {
  items: AdminUser[]
  total: number
  page: number
  size: number
}

async function getAdminUsers(params: AdminUsersParams): Promise<PagedAdminUsers> {
  return adminApi.getUsers(params)
}

async function updateUserRole(userId: string, role: string): Promise<void> {
  await adminApi.updateUserRole(userId, role)
}

async function createUser(payload: { username: string; password: string; email: string }): Promise<void> {
  await adminApi.createUser(payload)
}

async function updateUserProfile(
  userId: string,
  payload: { displayName: string; email: string },
): Promise<void> {
  await adminApi.updateUser(userId, payload)
}

async function deleteUser(userId: string): Promise<void> {
  await adminApi.deleteUser(userId)
}

async function updateUserStatus(userId: string, status: 'ACTIVE' | 'DISABLED'): Promise<void> {
  await adminApi.updateUserStatus(userId, status)
}

export function useAdminUsers(params: AdminUsersParams) {
  return useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => getAdminUsers(params),
  })
}

export function useCreateAdminUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: { username: string; password: string; email: string }) =>
      createUser(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}

export function useUpdateUserRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string }) =>
      updateUserRole(userId, role),
    onSuccess: () => {
      // User role changes affect both the admin list and the current session when an administrator
      // edits their own account.
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })
}

export function useUpdateAdminUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, payload }: {
      userId: string
      payload: { displayName: string; email: string }
    }) => updateUserProfile(userId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })
}

export function useDeleteAdminUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => deleteUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}

/** 上传 Excel 解析预览（只读，不创建账号） */
export function useParseUserImport() {
  return useMutation({
    mutationFn: (file: File) => adminApi.parseUserImport(file),
  })
}

/** 确认执行批量导入，成功后刷新用户列表 */
export function useImportUsers() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: {
      rows: Array<{ rowNumber: number; username: string; password?: string | null; email: string }>
    }) => adminApi.importUsers(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}

export function useUpdateUserStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: 'ACTIVE' | 'DISABLED' }) =>
      updateUserStatus(userId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })
}

export function useApproveUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => adminApi.approveUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })
}

export function useDisableUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => adminApi.disableUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })
}

export function useEnableUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => adminApi.enableUser(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
    },
  })
}

export function useTriggerUserPasswordReset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => adminApi.triggerPasswordReset(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}

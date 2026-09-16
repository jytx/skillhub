import { describe, expect, it } from 'vitest'
import * as adminUsers from './use-admin-users'

/**
 * use-admin-users exports interfaces (AdminUsersParams, PagedAdminUsers) for typing,
 * a type re-export (AdminUser), and several thin hooks: useAdminUsers (query),
 * useCreateAdminUser, useUpdateAdminUser, useDeleteAdminUser, useUpdateUserRole,
 * useUpdateUserStatus, useApproveUser, useDisableUser, useEnableUser (mutations).
 * All mutations invalidate ['admin', 'users'] and ['auth', 'me'] caches.
 *
 * There are no exported pure functions or data transformations to unit-test.
 * This file verifies the public API surface so that accidental export removals are caught.
 */
describe('use-admin-users module exports', () => {
  it('exports useAdminUsers query hook', () => {
    expect(adminUsers.useAdminUsers).toBeTypeOf('function')
  })

  it('exports useCreateAdminUser mutation hook', () => {
    expect(adminUsers.useCreateAdminUser).toBeTypeOf('function')
  })

  it('exports useUpdateAdminUser mutation hook', () => {
    expect(adminUsers.useUpdateAdminUser).toBeTypeOf('function')
  })

  it('exports useDeleteAdminUser mutation hook', () => {
    expect(adminUsers.useDeleteAdminUser).toBeTypeOf('function')
  })

  it('exports useParseUserImport mutation hook', () => {
    expect(adminUsers.useParseUserImport).toBeTypeOf('function')
  })

  it('exports useImportUsers mutation hook', () => {
    expect(adminUsers.useImportUsers).toBeTypeOf('function')
  })

  it('exports useUpdateUserRole mutation hook', () => {
    expect(adminUsers.useUpdateUserRole).toBeTypeOf('function')
  })

  it('exports useUpdateUserStatus mutation hook', () => {
    expect(adminUsers.useUpdateUserStatus).toBeTypeOf('function')
  })

  it('exports useApproveUser mutation hook', () => {
    expect(adminUsers.useApproveUser).toBeTypeOf('function')
  })

  it('exports useDisableUser mutation hook', () => {
    expect(adminUsers.useDisableUser).toBeTypeOf('function')
  })

  it('exports useEnableUser mutation hook', () => {
    expect(adminUsers.useEnableUser).toBeTypeOf('function')
  })
})

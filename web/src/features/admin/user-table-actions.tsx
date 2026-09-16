import { useTranslation } from 'react-i18next'
import type { AdminUser } from '@/api/types'
import { Button } from '@/shared/ui/button'

interface UserTableActionsProps {
  /** 当前行用户 */
  user: AdminUser
  /** 打开编辑资料弹窗 */
  onEdit: (user: AdminUser) => void
  /** 打开角色变更弹窗 */
  onChangeRole: (user: AdminUser) => void
  /** 直接审批 PENDING 用户 */
  onApprove: (user: AdminUser) => void
  /** 打开禁用/启用确认弹窗 */
  onToggleStatus: (user: AdminUser, action: 'ban' | 'unban') => void
  /** 打开重置密码确认弹窗 */
  onResetPassword: (user: AdminUser) => void
  /** 打开删除确认弹窗 */
  onDelete: (user: AdminUser) => void
  /** 任一变更操作进行中时禁用全部按钮，避免并发操作同一行 */
  busy: boolean
}

/**
 * 用户管理表格的操作列：编辑、变更角色、审批、禁用/启用、重置密码、删除。
 *
 * 删除入口按后端计算的 deletable 标志收敛：内置系统账号与引导管理员账号
 * 不显示删除按钮（后端同样强制保护，前端仅做展示层收敛）。
 */
export function UserTableActions({
  user,
  onEdit,
  onChangeRole,
  onApprove,
  onToggleStatus,
  onResetPassword,
  onDelete,
  busy,
}: UserTableActionsProps) {
  const { t } = useTranslation()

  return (
    <div className="flex flex-wrap gap-2">
      <Button variant="outline" size="sm" disabled={busy} onClick={() => onEdit(user)}>
        {t('adminUsers.editUser.action')}
      </Button>
      <Button variant="outline" size="sm" onClick={() => onChangeRole(user)}>
        {t('adminUsers.changeRole')}
      </Button>
      {user.status === 'PENDING' && (
        <Button variant="outline" size="sm" disabled={busy} onClick={() => onApprove(user)}>
          {t('adminUsers.approveUser')}
        </Button>
      )}
      {user.status === 'ACTIVE' ? (
        <Button variant="outline" size="sm" disabled={busy} onClick={() => onToggleStatus(user, 'ban')}>
          {t('adminUsers.disable')}
        </Button>
      ) : (
        <Button variant="outline" size="sm" disabled={busy} onClick={() => onToggleStatus(user, 'unban')}>
          {t('adminUsers.enable')}
        </Button>
      )}
      <Button variant="outline" size="sm" disabled={busy} onClick={() => onResetPassword(user)}>
        {t('adminUsers.resetPassword')}
      </Button>
      {user.deletable === false ? null : (
        <Button variant="destructive" size="sm" disabled={busy} onClick={() => onDelete(user)}>
          {t('adminUsers.deleteUser.action')}
        </Button>
      )}
    </div>
  )
}

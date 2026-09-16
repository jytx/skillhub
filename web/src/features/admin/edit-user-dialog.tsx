import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api/client'
import type { AdminUser } from '@/api/types'
import { EMAIL_PATTERN, isDuplicateEmailError, USERNAME_PATTERN } from '@/features/auth/local-account-rules'
import { useUpdateAdminUser } from '@/features/admin/use-admin-users'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

type EditUserFieldErrors = {
  displayName?: string
  email?: string
}

interface EditUserDialogProps {
  /** 对话框开关，由父页面持有 */
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 待编辑的用户；打开弹窗时以它初始化表单 */
  user: AdminUser | null
}

/**
 * 管理员编辑用户资料的对话框，仅支持修改显示名与邮箱。
 *
 * 校验规则与创建用户共享 local-account-rules；登录名、角色、状态、
 * 密码由各自独立的管理入口负责，不在此弹窗内编辑。
 */
export function EditUserDialog({ open, onOpenChange, user }: EditUserDialogProps) {
  const { t } = useTranslation()
  const updateUserMutation = useUpdateAdminUser()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [fieldErrors, setFieldErrors] = useState<EditUserFieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [initializedUserId, setInitializedUserId] = useState<string | null>(null)

  // 弹窗每次打开（或切换目标用户）时，用当前用户资料重新初始化表单
  if (open && user && initializedUserId !== user.userId) {
    setDisplayName(user.username)
    setEmail(user.email ?? '')
    setFieldErrors({})
    setFormError(null)
    setInitializedUserId(user.userId)
  }
  if (!open && initializedUserId !== null) {
    setInitializedUserId(null)
  }

  function validateDisplayName(value: string): string | undefined {
    const trimmed = value.trim()
    if (!trimmed) {
      return t('adminUsers.editUser.usernameRequired')
    }
    if (!USERNAME_PATTERN.test(trimmed)) {
      return t('adminUsers.editUser.usernameInvalid')
    }
    return undefined
  }

  function validateEmail(value: string): string | undefined {
    const trimmed = value.trim().toLowerCase()
    if (!trimmed) {
      return t('adminUsers.editUser.emailRequired')
    }
    if (!EMAIL_PATTERN.test(trimmed)) {
      return t('adminUsers.editUser.emailInvalid')
    }
    return undefined
  }

  function clearErrorFor(field: keyof EditUserFieldErrors) {
    if (fieldErrors[field] || formError) {
      setFieldErrors((current) => ({ ...current, [field]: undefined }))
      setFormError(null)
    }
  }

  /** 把服务端错误映射回具体字段，便于管理员直接定位问题输入 */
  function mapApiError(error: unknown): EditUserFieldErrors {
    if (!(error instanceof ApiError)) {
      return {}
    }
    const errorKey = error.serverMessageKey ?? error.serverMessage ?? error.message
    if (errorKey === 'error.admin.user.email.exists' || isDuplicateEmailError(errorKey)) {
      return { email: t('adminUsers.editUser.emailExists') }
    }
    return {}
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!user) {
      return
    }
    const nextFieldErrors: EditUserFieldErrors = {
      displayName: validateDisplayName(displayName),
      email: validateEmail(email),
    }
    if (nextFieldErrors.displayName || nextFieldErrors.email) {
      setFieldErrors(nextFieldErrors)
      setFormError(null)
      return
    }

    setFieldErrors({})
    setFormError(null)
    try {
      await updateUserMutation.mutateAsync({
        userId: user.userId,
        payload: {
          displayName: displayName.trim(),
          email: email.trim().toLowerCase(),
        },
      })
      onOpenChange(false)
    } catch (error) {
      const apiFieldErrors = mapApiError(error)
      if (Object.keys(apiFieldErrors).length > 0) {
        setFieldErrors(apiFieldErrors)
      } else {
        setFormError(error instanceof Error ? error.message : t('apiError.unknown'))
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('adminUsers.editUser.title')}</DialogTitle>
          <DialogDescription>
            {t('adminUsers.editUser.description', { username: user?.username ?? '' })}
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4 py-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="edit-user-username">{t('adminUsers.editUser.usernameLabel')}</Label>
            <Input
              id="edit-user-username"
              autoComplete="off"
              value={displayName}
              onChange={(event) => {
                setDisplayName(event.target.value)
                clearErrorFor('displayName')
              }}
              aria-invalid={fieldErrors.displayName ? 'true' : 'false'}
            />
            {fieldErrors.displayName ? (
              <p className="text-sm text-red-600">{fieldErrors.displayName}</p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="edit-user-email">{t('adminUsers.editUser.emailLabel')}</Label>
            <Input
              id="edit-user-email"
              type="email"
              autoComplete="off"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value)
                clearErrorFor('email')
              }}
              aria-invalid={fieldErrors.email ? 'true' : 'false'}
            />
            {fieldErrors.email ? (
              <p className="text-sm text-red-600">{fieldErrors.email}</p>
            ) : null}
          </div>
          {formError ? <p className="text-sm text-red-600">{formError}</p> : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button type="submit" disabled={updateUserMutation.isPending}>
              {updateUserMutation.isPending
                ? t('adminUsers.editUser.submitting')
                : t('adminUsers.editUser.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

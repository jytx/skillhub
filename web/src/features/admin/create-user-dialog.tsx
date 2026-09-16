import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api/client'
import {
  countPasswordCharacterTypes,
  EMAIL_PATTERN,
  isDuplicateEmailError,
  isDuplicateUsernameError,
  USERNAME_PATTERN,
} from '@/features/auth/local-account-rules'
import { useCreateAdminUser } from '@/features/admin/use-admin-users'
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

type CreateUserFieldErrors = {
  username?: string
  email?: string
  password?: string
}

type CreateUserFormState = {
  username: string
  email: string
  password: string
}

const EMPTY_FORM: CreateUserFormState = { username: '', email: '', password: '' }

interface CreateUserDialogProps {
  /** 对话框开关，由父页面持有 */
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * 管理员创建本地账号的对话框表单。
 *
 * 校验规则与自助注册页保持一致（共享 local-account-rules），
 * 创建成功后自动关闭并刷新用户列表。
 */
export function CreateUserDialog({ open, onOpenChange }: CreateUserDialogProps) {
  const { t } = useTranslation()
  const createUserMutation = useCreateAdminUser()
  const [form, setForm] = useState<CreateUserFormState>(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<CreateUserFieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  function validateUsername(value: string): string | undefined {
    const trimmed = value.trim()
    if (!trimmed) {
      return t('adminUsers.createUser.usernameRequired')
    }
    if (!USERNAME_PATTERN.test(trimmed)) {
      return t('adminUsers.createUser.usernameInvalid')
    }
    return undefined
  }

  function validateEmail(value: string): string | undefined {
    const trimmed = value.trim().toLowerCase()
    if (!trimmed) {
      return t('adminUsers.createUser.emailRequired')
    }
    if (!EMAIL_PATTERN.test(trimmed)) {
      return t('adminUsers.createUser.emailInvalid')
    }
    return undefined
  }

  function validatePassword(value: string): string | undefined {
    if (!value) {
      return t('adminUsers.createUser.passwordRequired')
    }
    if (value.length < 8) {
      return t('adminUsers.createUser.passwordTooShort')
    }
    if (countPasswordCharacterTypes(value) < 3) {
      return t('adminUsers.createUser.passwordTooWeak')
    }
    return undefined
  }

  function updateField(field: keyof CreateUserFormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
    if (fieldErrors[field] || formError) {
      setFieldErrors((current) => ({ ...current, [field]: undefined }))
      setFormError(null)
    }
  }

  /** 把服务端错误映射回具体字段，便于管理员直接定位问题输入 */
  function mapApiError(error: unknown): CreateUserFieldErrors {
    if (!(error instanceof ApiError)) {
      return {}
    }
    const errorKey = error.serverMessageKey ?? error.serverMessage ?? error.message
    if (errorKey === 'error.auth.local.username.invalid') {
      return { username: t('adminUsers.createUser.usernameInvalid') }
    }
    if (errorKey === 'error.auth.local.password.tooShort') {
      return { password: t('adminUsers.createUser.passwordTooShort') }
    }
    if (errorKey === 'error.auth.local.password.tooWeak') {
      return { password: t('adminUsers.createUser.passwordTooWeak') }
    }
    if (errorKey === 'error.auth.local.username.exists' || isDuplicateUsernameError(errorKey)) {
      return { username: t('adminUsers.createUser.usernameExists') }
    }
    if (errorKey === 'error.auth.local.email.exists' || isDuplicateEmailError(errorKey)) {
      return { email: t('adminUsers.createUser.emailExists') }
    }
    return {}
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextFieldErrors: CreateUserFieldErrors = {
      username: validateUsername(form.username),
      email: validateEmail(form.email),
      password: validatePassword(form.password),
    }
    if (nextFieldErrors.username || nextFieldErrors.email || nextFieldErrors.password) {
      setFieldErrors(nextFieldErrors)
      setFormError(null)
      return
    }

    setFieldErrors({})
    setFormError(null)
    try {
      await createUserMutation.mutateAsync({
        username: form.username.trim(),
        email: form.email.trim().toLowerCase(),
        password: form.password,
      })
      setForm(EMPTY_FORM)
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
          <DialogTitle>{t('adminUsers.createUser.title')}</DialogTitle>
          <DialogDescription>{t('adminUsers.createUser.description')}</DialogDescription>
        </DialogHeader>
        <form className="space-y-4 py-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="create-user-username">{t('adminUsers.createUser.usernameLabel')}</Label>
            <Input
              id="create-user-username"
              autoComplete="off"
              value={form.username}
              onChange={(event) => updateField('username', event.target.value)}
              placeholder={t('adminUsers.createUser.usernamePlaceholder')}
              aria-invalid={fieldErrors.username ? 'true' : 'false'}
            />
            {fieldErrors.username ? (
              <p className="text-sm text-red-600">{fieldErrors.username}</p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="create-user-email">{t('adminUsers.createUser.emailLabel')}</Label>
            <Input
              id="create-user-email"
              type="email"
              autoComplete="off"
              value={form.email}
              onChange={(event) => updateField('email', event.target.value)}
              placeholder={t('adminUsers.createUser.emailPlaceholder')}
              aria-invalid={fieldErrors.email ? 'true' : 'false'}
            />
            {fieldErrors.email ? (
              <p className="text-sm text-red-600">{fieldErrors.email}</p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="create-user-password">{t('adminUsers.createUser.passwordLabel')}</Label>
            <Input
              id="create-user-password"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(event) => updateField('password', event.target.value)}
              placeholder={t('adminUsers.createUser.passwordPlaceholder')}
              aria-invalid={fieldErrors.password ? 'true' : 'false'}
            />
            {fieldErrors.password ? (
              <p className="text-sm text-red-600">{fieldErrors.password}</p>
            ) : null}
            <p className="text-xs text-muted-foreground">{t('adminUsers.createUser.passwordHint')}</p>
          </div>
          {formError ? <p className="text-sm text-red-600">{formError}</p> : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('dialog.cancel')}
            </Button>
            <Button type="submit" disabled={createUserMutation.isPending}>
              {createUserMutation.isPending
                ? t('adminUsers.createUser.submitting')
                : t('adminUsers.createUser.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

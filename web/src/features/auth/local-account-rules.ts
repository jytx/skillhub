/**
 * 本地账号的纯校验规则与错误判定，供注册页与管理员建用户表单共用。
 *
 * 与后端 LocalAuthService / PasswordPolicyValidator 的规则保持一致：
 * 用户名 3-64 位字母数字下划线；密码至少 8 位且包含至少 3 种字符类型。
 */
export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,64}$/

export const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

/** 统计密码中出现的字符类型数（小写、大写、数字、其他符号） */
export function countPasswordCharacterTypes(password: string): number {
  let typeCount = 0
  if (/[a-z]/.test(password)) {
    typeCount += 1
  }
  if (/[A-Z]/.test(password)) {
    typeCount += 1
  }
  if (/\d/.test(password)) {
    typeCount += 1
  }
  if (/[^A-Za-z0-9]/.test(password)) {
    typeCount += 1
  }
  return typeCount
}

/** 判定服务端错误是否为“用户名已存在”（兼容不同语言下的消息文案） */
export function isDuplicateUsernameError(errorKey: string): boolean {
  return errorKey === 'error.auth.local.username.exists'
    || errorKey.includes('Username already exists')
    || errorKey.includes('用户名已存在')
}

/** 判定服务端错误是否为“邮箱已存在”（兼容不同语言下的消息文案） */
export function isDuplicateEmailError(errorKey: string): boolean {
  return errorKey === 'error.auth.local.email.exists'
    || errorKey.includes('Email already exists')
    || errorKey.includes('邮箱已存在')
}

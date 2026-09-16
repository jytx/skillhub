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

/** 随机密码可用的字符池：大小写字母、数字，以及不易与命令行/JSON 转义冲突的符号 */
const PASSWORD_CHAR_POOLS: ReadonlyArray<readonly string[]> = [
  'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
  'abcdefghijklmnopqrstuvwxyz'.split(''),
  '0123456789'.split(''),
  '!@#$%^&*-_'.split(''),
]

/**
 * 用加密安全随机源（crypto.getRandomValues）生成 [0, maxExclusive) 的均匀随机整数。
 * 通过拒绝采样消除取模偏差，保证每个取值概率严格相等。
 */
function randomIndex(maxExclusive: number): number {
  const limit = Math.floor(0x100000000 / maxExclusive) * maxExclusive
  const buffer = new Uint32Array(1)
  let value: number
  do {
    crypto.getRandomValues(buffer)
    value = buffer[0]
  } while (value >= limit)
  return value % maxExclusive
}

function pickRandomChar(chars: readonly string[]): string {
  return chars[randomIndex(chars.length)]
}

/**
 * 生成随机初始密码：保证包含大小写字母、数字、符号各至少一个，
 * 天然满足“至少 8 位且至少 3 种字符类型”的密码策略，然后整体洗牌打散固定位置。
 */
export function generateRandomPassword(length: number): string {
  const poolCount = PASSWORD_CHAR_POOLS.length
  if (length < poolCount) {
    throw new Error(`Password length must be at least ${poolCount}`)
  }
  // 每个字符池先各取一个，剩余位从全量字符池随机取
  const chars: string[] = PASSWORD_CHAR_POOLS.map((pool) => pickRandomChar(pool))
  const allChars: string[] = PASSWORD_CHAR_POOLS.flat()
  for (let i = poolCount; i < length; i += 1) {
    chars.push(pickRandomChar(allChars))
  }
  // Fisher-Yates 洗牌，避免前几位总是固定来自特定字符池
  for (let i = chars.length - 1; i > 0; i -= 1) {
    const j = randomIndex(i + 1)
    const temp = chars[i]
    chars[i] = chars[j]
    chars[j] = temp
  }
  return chars.join('')
}

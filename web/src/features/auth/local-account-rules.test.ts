import { describe, expect, it } from 'vitest'
import {
  countPasswordCharacterTypes,
  generateRandomPassword,
  USERNAME_PATTERN,
} from './local-account-rules'

describe('USERNAME_PATTERN', () => {
  it('accepts 3-64 letters, digits and underscores', () => {
    expect(USERNAME_PATTERN.test('abc')).toBe(true)
    expect(USERNAME_PATTERN.test('User_01')).toBe(true)
  })

  it('rejects too-short values and invalid characters', () => {
    expect(USERNAME_PATTERN.test('ab')).toBe(false)
    expect(USERNAME_PATTERN.test('user name')).toBe(false)
    expect(USERNAME_PATTERN.test('用户名')).toBe(false)
  })
})

describe('generateRandomPassword', () => {
  it('returns a password of the requested length', () => {
    expect(generateRandomPassword(16)).toHaveLength(16)
    expect(generateRandomPassword(8)).toHaveLength(8)
  })

  it('always includes all four character types, satisfying the password policy', () => {
    for (let i = 0; i < 100; i += 1) {
      const password = generateRandomPassword(16)
      expect(password.length).toBeGreaterThanOrEqual(8)
      expect(countPasswordCharacterTypes(password)).toBeGreaterThanOrEqual(3)
      expect(/[A-Z]/.test(password)).toBe(true)
      expect(/[a-z]/.test(password)).toBe(true)
      expect(/\d/.test(password)).toBe(true)
      expect(/[!@#$%^&*-_]/.test(password)).toBe(true)
    }
  })

  it('only contains characters from the known pools', () => {
    const allowed = /^[A-Za-z0-9!@#$%^&*-_]+$/
    for (let i = 0; i < 50; i += 1) {
      expect(allowed.test(generateRandomPassword(16))).toBe(true)
    }
  })

  it('produces different passwords across calls', () => {
    const passwords = new Set(Array.from({ length: 20 }, () => generateRandomPassword(16)))
    expect(passwords.size).toBe(20)
  })

  it('rejects lengths that cannot hold every character pool', () => {
    expect(() => generateRandomPassword(3)).toThrow(Error)
  })
})

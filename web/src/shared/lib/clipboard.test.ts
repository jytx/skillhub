// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { copyToClipboard } from './clipboard'

/**
 * 剪贴板复制工具的三条路径：
 * 1. 异步 clipboard API 可用且成功
 * 2. clipboard API 抛错（权限拒绝）时降级到 execCommand 同步复制
 * 3. 降级仍失败（execCommand 返回 false）时抛错，调用方需给出失败反馈
 */
describe('copyToClipboard', () => {
  const originalClipboard = navigator.clipboard

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      writable: true,
      value: originalClipboard,
    })
    delete (document as Partial<Document>).execCommand
  })

  function stubClipboard(writeText: (() => Promise<void>) | undefined) {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      writable: true,
      value: writeText ? { writeText } : undefined,
    })
  }

  function stubExecCommand(result: boolean) {
    const execCommand = vi.fn(() => result)
    Object.defineProperty(document, 'execCommand', {
      configurable: true,
      writable: true,
      value: execCommand,
    })
    return execCommand
  }

  it('uses the async clipboard API when available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubClipboard(writeText)

    await copyToClipboard('hello')

    expect(writeText).toHaveBeenCalledWith('hello')
  })

  it('falls back to execCommand when the clipboard API rejects', async () => {
    stubClipboard(vi.fn().mockRejectedValue(new Error('NotAllowedError')))
    const execCommand = stubExecCommand(true)

    await copyToClipboard('fallback-text')

    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  it('selects a hidden span via Range (no focus change) before execCommand', async () => {
    stubClipboard(undefined)
    const execCommand = stubExecCommand(true)
    const addRangeSpy = vi.fn()
    const removeAllRangesSpy = vi.fn()
    const selectNodeContentsSpy = vi.fn()
    vi.spyOn(window, 'getSelection').mockReturnValue({
      addRange: addRangeSpy,
      removeAllRanges: removeAllRangesSpy,
    } as unknown as Selection)
    vi.spyOn(document, 'createRange').mockReturnValue({
      selectNodeContents: selectNodeContentsSpy,
    } as unknown as Range)

    await copyToClipboard('secret')

    // Range 选中隐藏 span → 清空并应用选区 → 复制 → 清理选区；全程不动焦点
    expect(selectNodeContentsSpy).toHaveBeenCalled()
    expect(addRangeSpy).toHaveBeenCalled()
    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(removeAllRangesSpy).toHaveBeenCalledTimes(2)
  })

  it('throws when the execCommand fallback reports failure', async () => {
    stubClipboard(undefined)
    stubExecCommand(false)
    vi.spyOn(window, 'getSelection').mockReturnValue({
      addRange: vi.fn(),
      removeAllRanges: vi.fn(),
    } as unknown as Selection)
    vi.spyOn(document, 'createRange').mockReturnValue({
      selectNodeContents: vi.fn(),
    } as unknown as Range)

    await expect(copyToClipboard('nope')).rejects.toThrow('Failed to copy text to clipboard')
  })
})

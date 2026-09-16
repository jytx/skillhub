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

  it('focuses and selects the fallback textarea, then restores focus', async () => {
    stubClipboard(undefined)
    const execCommand = stubExecCommand(true)
    const focusSpy = vi.fn()
    const selectRangeSpy = vi.fn()
    const activeElement = document.createElement('button')
    document.body.appendChild(activeElement)
    activeElement.focus = focusSpy
    activeElement.focus()
    const originalCreateElement = document.createElement.bind(document)
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      const element = originalCreateElement(tag)
      if (tag === 'textarea') {
        const textarea = element as HTMLTextAreaElement
        textarea.focus = focusSpy
        textarea.setSelectionRange = selectRangeSpy
        return textarea
      }
      return element
    })

    try {
      await copyToClipboard('secret')
    } finally {
      createElementSpy.mockRestore()
      document.body.removeChild(activeElement)
    }

    // 聚焦 + 编程选中文本后才执行复制，并把焦点归还原元素
    expect(selectRangeSpy).toHaveBeenCalledWith(0, 'secret'.length)
    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(focusSpy).toHaveBeenCalled()
  })

  it('throws when the execCommand fallback reports failure', async () => {
    stubClipboard(undefined)
    stubExecCommand(false)

    await expect(copyToClipboard('nope')).rejects.toThrow('Failed to copy text to clipboard')
  })
})

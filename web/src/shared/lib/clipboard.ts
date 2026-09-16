import { useCallback, useRef, useState } from 'react'

/**
 * Copy text to clipboard with fallback for insecure contexts (HTTP, iframes).
 *
 * 生产部署常以 HTTP + IP 访问（非安全上下文），navigator.clipboard 不存在，
 * 只能依赖 execCommand 同步复制；弹窗（焦点陷阱）与部分浏览器要求
 * textarea 被聚焦后 setSelectionRange 才能真正选中，复制完再把焦点
 * 归还原元素，避免破坏 Radix Dialog 的焦点管理。
 */
export async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // 权限被拒（如 iframe 未授权）等场景，退回同步复制
    }
  }
  // Fallback for insecure contexts (HTTP, iframes)
  const textarea = document.createElement('textarea')
  textarea.value = text
  // readonly 让 iOS Safari 允许编程选中
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '0'
  textarea.style.left = '0'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  const previousActiveElement = document.activeElement
  textarea.focus()
  textarea.setSelectionRange(0, text.length)
  let success = false
  try {
    success = document.execCommand('copy')
  } finally {
    document.body.removeChild(textarea)
    if (previousActiveElement instanceof HTMLElement && previousActiveElement !== document.body) {
      previousActiveElement.focus()
    }
  }

  if (!success) {
    throw new Error('Failed to copy text to clipboard')
  }
}

/**
 * React hook for clipboard copy with auto-reset "copied" state.
 *
 * @param timeout - ms before `copied` resets to false (default 2000)
 * @returns `[copied, copy]` — boolean status and an async copy function
 *
 * @example
 * const [copied, copy] = useCopyToClipboard()
 * <button onClick={() => copy('hello')}>
 *   {copied ? 'Copied!' : 'Copy'}
 * </button>
 */
export function useCopyToClipboard(timeout = 2000) {
  const [copied, setCopied] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const copy = useCallback(async (text: string) => {
    await copyToClipboard(text)
    setCopied(true)
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }
    timerRef.current = setTimeout(() => setCopied(false), timeout)
  }, [timeout])

  return [copied, copy] as const
}

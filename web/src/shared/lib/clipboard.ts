import { useCallback, useRef, useState } from 'react'

/**
 * Copy text to clipboard with fallback for insecure contexts (HTTP, iframes).
 *
 * 生产部署常以 HTTP + IP 访问（非安全上下文），navigator.clipboard 不存在，
 * 只能依赖 execCommand 同步复制。降级实现必须用 Selection/Range API
 * 选中临时节点内容——它不移动焦点，不会被 Radix Dialog 的 FocusScope
 * 视为"焦点逃逸"而拉回焦点、破坏选区（textarea 方案在弹窗内正因此失效）。
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
  // Fallback for insecure contexts (HTTP, iframes)：
  // 隐藏 span + Range 选中 + execCommand，全程不改焦点（white-space:pre 保留换行）
  const span = document.createElement('span')
  span.textContent = text
  span.style.position = 'fixed'
  span.style.top = '0'
  span.style.left = '0'
  span.style.opacity = '0'
  span.style.whiteSpace = 'pre'
  document.body.appendChild(span)
  const selection = window.getSelection()
  let success = false
  if (selection != null) {
    const range = document.createRange()
    range.selectNodeContents(span)
    try {
      selection.removeAllRanges()
      selection.addRange(range)
      success = document.execCommand('copy')
    } finally {
      selection.removeAllRanges()
    }
  }
  document.body.removeChild(span)

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

import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { copyToClipboard } from '@/shared/lib/clipboard'

interface CopyButtonProps {
  text: string
  className?: string
  ariaLabel?: string
}

/**
 * 通用复制按钮：成功后短暂显示“已复制”，失败（如 HTTP 环境剪贴板降级失效）
 * 时短暂显示“复制失败”提示手动选择文本复制，避免静默失败。
 */
export function CopyButton({ text, className, ariaLabel }: CopyButtonProps) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const [failed, setFailed] = useState(false)
  const resetTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => {
    return () => {
      if (resetTimerRef.current) {
        clearTimeout(resetTimerRef.current)
      }
    }
  }, [])

  const scheduleReset = () => {
    if (resetTimerRef.current) {
      clearTimeout(resetTimerRef.current)
    }
    resetTimerRef.current = setTimeout(() => {
      setCopied(false)
      setFailed(false)
    }, 2000)
  }

  const handleCopy = async () => {
    setCopied(false)
    setFailed(false)
    try {
      await copyToClipboard(text)
      setCopied(true)
    } catch (err) {
      console.error('Failed to copy:', err)
      setFailed(true)
    }
    scheduleReset()
  }

  return (
    <Button
      variant="outline"
      size="sm"
      onClick={handleCopy}
      className={className}
      aria-label={ariaLabel}
    >
      {copied
        ? t('copyButton.copied')
        : failed
          ? t('copyButton.copyFailed')
          : t('copyButton.copy')}
    </Button>
  )
}

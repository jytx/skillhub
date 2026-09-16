import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { adminApi } from '@/api/client'
import type { AdminUserImportParseResult, AdminUserImportResult } from '@/api/types'
import { useImportUsers, useParseUserImport } from '@/features/admin/use-admin-users'
import { CopyButton } from '@/shared/components/copy-button'
import { copyToClipboard } from '@/shared/lib/clipboard'
import { centeredToastOptions, toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'

type ImportStep = 'upload' | 'preview' | 'result'

interface ImportUsersDialogProps {
  /** 对话框开关，由父页面持有 */
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Excel 批量导入用户的三步向导：上传解析 → 预览校验 → 执行导入。
 *
 * 预览阶段展示行级校验结果（有误的行不参与导入）；结果阶段展示每行
 * 创建结论，自动生成的初始密码仅此一次明文展示，支持逐行或整批复制分发。
 */
export function ImportUsersDialog({ open, onOpenChange }: ImportUsersDialogProps) {
  const { t, i18n } = useTranslation()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const parseMutation = useParseUserImport()
  const importMutation = useImportUsers()

  const [step, setStep] = useState<ImportStep>('upload')
  const [fileName, setFileName] = useState('')
  const [parseResult, setParseResult] = useState<AdminUserImportParseResult | null>(null)
  const [importResult, setImportResult] = useState<AdminUserImportResult | null>(null)
  const [sessionActive, setSessionActive] = useState(false)

  // 每次打开弹窗都从上传步骤重新开始，避免残留上一次的解析/导入数据
  if (open && !sessionActive) {
    setStep('upload')
    setFileName('')
    setParseResult(null)
    setImportResult(null)
    setSessionActive(true)
  }
  if (!open && sessionActive) {
    setSessionActive(false)
  }

  /** 后端行级错误 key → 本地化文案；未覆盖的 key 原样展示便于排查 */
  function errorText(errorKey?: string | null): string {
    if (!errorKey) {
      return ''
    }
    const localeKey = `backendErrors.${errorKey}`
    return i18n.exists(localeKey) ? t(localeKey) : errorKey
  }

  async function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }
    try {
      const result = await parseMutation.mutateAsync(file)
      setFileName(file.name)
      setParseResult(result)
      setStep('preview')
    } catch (error) {
      toast.error(
        t('adminUsers.importUser.parseFailed'),
        error instanceof Error ? error.message : undefined,
        centeredToastOptions(),
      )
    }
  }

  async function handleConfirmImport() {
    if (!parseResult) {
      return
    }
    const validRows = parseResult.rows.filter((row) => row.valid)
    try {
      const result = await importMutation.mutateAsync({
        rows: validRows.map((row) => ({
          rowNumber: row.rowNumber,
          username: row.username,
          password: row.password ?? null,
          email: row.email,
        })),
      })
      setImportResult(result)
      setStep('result')
    } catch (error) {
      toast.error(
        t('adminUsers.importUser.importFailed'),
        error instanceof Error ? error.message : undefined,
        centeredToastOptions(),
      )
    }
  }

  /** 复制全部成功账号（用户名,密码 逐行）；密码优先用生成的，其次用文件里填写的 */
  async function handleCopyAll() {
    if (!importResult || !parseResult) {
      return
    }
    const lines = importResult.results
      .filter((row) => row.success)
      .map((row) => {
        const original = parseResult.rows
          .find((parsed) => parsed.rowNumber === row.rowNumber)
          ?.password
        return `${row.username},${row.generatedPassword ?? original ?? ''}`
      })
    if (lines.length === 0) {
      return
    }
    try {
      await copyToClipboard(lines.join('\n'))
      toast.success(t('adminUsers.importUser.copiedAll'), undefined, centeredToastOptions())
    } catch (error) {
      console.error('Failed to copy accounts:', error)
    }
  }

  const validCount = parseResult?.validCount ?? 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('adminUsers.importUser.title')}</DialogTitle>
          <DialogDescription>
            {step === 'result'
              ? t('adminUsers.importUser.resultTitle')
              : t('adminUsers.importUser.description')}
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
          {/* 文件选择输入框常驻渲染：预览步骤的「重新选择文件」也复用同一个输入框 */}
          <input
            ref={fileInputRef}
            type="file"
            accept=".xlsx"
            className="hidden"
            onChange={handleFileChange}
          />
          {step === 'upload' && (
            <div className="space-y-4">
              <p className="text-sm text-muted-foreground">
                {t('adminUsers.importUser.uploadHint')}
              </p>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  onClick={() => adminApi.downloadUserImportTemplate()}
                >
                  {t('adminUsers.importUser.template')}
                </Button>
                <Button onClick={() => fileInputRef.current?.click()} disabled={parseMutation.isPending}>
                  {parseMutation.isPending
                    ? t('adminUsers.importUser.parsing')
                    : t('adminUsers.importUser.selectFile')}
                </Button>
              </div>
            </div>
          )}

          {step === 'preview' && parseResult && (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {fileName} · {t('adminUsers.importUser.previewSummary', {
                  total: parseResult.totalRows,
                  valid: parseResult.validCount,
                  invalid: parseResult.invalidCount,
                })}
              </p>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('adminUsers.importUser.colRow')}</TableHead>
                    <TableHead>{t('adminUsers.colUsername')}</TableHead>
                    <TableHead>{t('adminUsers.colEmail')}</TableHead>
                    <TableHead>{t('adminUsers.importUser.colPassword')}</TableHead>
                    <TableHead>{t('adminUsers.importUser.colResult')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {parseResult.rows.map((row) => (
                    <TableRow key={row.rowNumber} className={row.valid ? '' : 'opacity-80'}>
                      <TableCell className="font-mono text-xs">{row.rowNumber}</TableCell>
                      <TableCell className="font-medium">{row.username || '-'}</TableCell>
                      <TableCell className="break-all">{row.email || '-'}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {row.password
                          ? t('adminUsers.importUser.passwordProvided')
                          : t('adminUsers.importUser.passwordAuto')}
                      </TableCell>
                      <TableCell className={row.valid ? 'text-emerald-600' : 'text-red-600'}>
                        {row.valid ? '✓' : errorText(row.errorKey)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {step === 'result' && importResult && (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {t('adminUsers.importUser.resultSummary', {
                  created: importResult.createdCount,
                  failed: importResult.failedCount,
                })}
              </p>
              {importResult.createdCount > 0 && (
                <Button variant="outline" size="sm" onClick={handleCopyAll}>
                  {t('adminUsers.importUser.copyAll')}
                </Button>
              )}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('adminUsers.importUser.colRow')}</TableHead>
                    <TableHead>{t('adminUsers.colUsername')}</TableHead>
                    <TableHead>{t('adminUsers.importUser.colResult')}</TableHead>
                    <TableHead>{t('adminUsers.importUser.colPassword')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {importResult.results.map((row) => (
                    <TableRow key={row.rowNumber}>
                      <TableCell className="font-mono text-xs">{row.rowNumber}</TableCell>
                      <TableCell className="font-medium">{row.username}</TableCell>
                      <TableCell className={row.success ? 'text-emerald-600' : 'text-red-600'}>
                        {row.success ? '✓' : errorText(row.errorKey)}
                      </TableCell>
                      <TableCell>
                        {row.success && row.generatedPassword ? (
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-xs">{row.generatedPassword}</span>
                            <CopyButton
                              text={row.generatedPassword}
                              ariaLabel={t('adminUsers.importUser.copyPassword', {
                                username: row.username,
                              })}
                            />
                          </div>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>

        <DialogFooter>
          {step === 'preview' && (
            <>
              <Button variant="outline" onClick={() => fileInputRef.current?.click()}>
                {t('adminUsers.importUser.reselect')}
              </Button>
              <Button
                onClick={handleConfirmImport}
                disabled={importMutation.isPending || validCount === 0}
              >
                {importMutation.isPending
                  ? t('adminUsers.importUser.importing')
                  : t('adminUsers.importUser.confirmImport', { count: validCount })}
              </Button>
            </>
          )}
          {step === 'result' && (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                {t('adminUsers.importUser.done')}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

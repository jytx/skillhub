package com.iflytek.skillhub.dto;

import java.util.List;

/**
 * Excel 批量导入执行结果：逐行报告创建结论。
 *
 * <p>generatedPassword 仅在"该行未填密码、由服务端自动生成且创建成功"时返回明文，
 * 供管理员一次性复制分发；此后系统只存哈希，无法再次查看。失败行的 errorKey
 * 是可本地化的消息 key。</p>
 */
public record AdminUserImportResultResponse(
        List<RowResult> results,
        int createdCount,
        int failedCount
) {
    public record RowResult(
            int rowNumber,
            String username,
            boolean success,
            String generatedPassword,
            String errorKey
    ) {
    }
}

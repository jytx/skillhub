package com.iflytek.skillhub.dto;

import java.util.List;

/**
 * Excel 导入解析（预览）结果：逐行回传归一化后的数据与行级校验结论。
 *
 * <p>password 为文件中填写的初始密码；为 null 表示该行未填密码，确认导入时由服务端
 * 自动生成随机密码。行级 errorKey 是可本地化的消息 key（如 error.auth.local.username.exists），
 * 仅 valid=false 时有值。</p>
 */
public record AdminUserImportParseResponse(
        List<Row> rows,
        int totalRows,
        int validCount,
        int invalidCount
) {
    public record Row(
            int rowNumber,
            String username,
            String email,
            String password,
            boolean valid,
            String errorKey
    ) {
    }
}

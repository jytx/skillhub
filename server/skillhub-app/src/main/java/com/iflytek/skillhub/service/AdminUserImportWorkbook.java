package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 用户导入工作簿（.xlsx）的读取与模板生成工具。
 *
 * <p>读取约定：第一个工作表、首行为表头（中英文均可识别列名），数据行的单元格值
 * 统一经 DataFormatter 转为字符串并 trim，密码单元格的空白也去除；三列全空的行跳过。</p>
 */
public final class AdminUserImportWorkbook {

    /** 模板文件名，同时用于下载响应与前端 accept 过滤 */
    public static final String TEMPLATE_FILENAME = "skillhub-users-template.xlsx";

    private static final Set<String> USERNAME_HEADERS = Set.of("username", "user name", "用户名");
    private static final Set<String> EMAIL_HEADERS = Set.of("email", "e-mail", "邮箱", "电子邮箱");
    private static final Set<String> PASSWORD_HEADERS = Set.of("password", "密码");

    /** 解析出的一行导入数据；rowNumber 为 Excel 中的实际行号（表头为第 1 行） */
    public record ImportRow(int rowNumber, String username, String email, String password) {
    }

    private AdminUserImportWorkbook() {
    }

    /**
     * 读取上传的工作簿数据行。缺 username 或 email 列、或没有任何数据行时抛出
     * 带 import 错误 key 的 {@link DomainBadRequestException}。
     */
    public static List<ImportRow> readRows(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getRow(0) == null) {
                throw new DomainBadRequestException("error.admin.user.import.headerMissing");
            }
            int usernameCol = -1;
            int emailCol = -1;
            int passwordCol = -1;
            for (var cell : sheet.getRow(0)) {
                String header = normalizeHeader(new DataFormatter().formatCellValue(cell));
                if (USERNAME_HEADERS.contains(header)) {
                    usernameCol = cell.getColumnIndex();
                } else if (EMAIL_HEADERS.contains(header)) {
                    emailCol = cell.getColumnIndex();
                } else if (PASSWORD_HEADERS.contains(header)) {
                    passwordCol = cell.getColumnIndex();
                }
            }
            if (usernameCol < 0 || emailCol < 0) {
                throw new DomainBadRequestException("error.admin.user.import.headerMissing");
            }
            return extractDataRows(sheet, usernameCol, emailCol, passwordCol);
        } catch (DomainBadRequestException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            // POI 对损坏或非 xlsx 文件抛出的异常类型众多，统一收敛为格式错误
            throw new DomainBadRequestException("error.admin.user.import.badFormat");
        }
    }

    private static List<ImportRow> extractDataRows(Sheet sheet, int usernameCol, int emailCol, int passwordCol) {
        DataFormatter formatter = new DataFormatter();
        List<ImportRow> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String username = cellText(row, usernameCol, formatter);
            String email = cellText(row, emailCol, formatter);
            String password = passwordCol >= 0 ? cellText(row, passwordCol, formatter) : null;
            if ((username == null || username.isBlank())
                    && (email == null || email.isBlank())
                    && (password == null || password.isBlank())) {
                continue;
            }
            rows.add(new ImportRow(rowIndex + 1, username, email, password));
        }
        return rows;
    }

    private static String cellText(Row row, int columnIndex, DataFormatter formatter) {
        var cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }

    /** 生成导入模板：username/email/password 三列表头 + 一行示例（密码留空表示自动生成） */
    public static byte[] templateBytes() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("username");
            header.createCell(1).setCellValue("email");
            header.createCell(2).setCellValue("password");
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("alice");
            example.createCell(1).setCellValue("alice@example.com");
            sheet.setColumnWidth(0, 16 * 256);
            sheet.setColumnWidth(1, 30 * 256);
            sheet.setColumnWidth(2, 24 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build user import template", ex);
        }
    }
}

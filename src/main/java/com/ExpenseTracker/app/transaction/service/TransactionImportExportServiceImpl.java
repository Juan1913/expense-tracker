package com.ExpenseTracker.app.transaction.service;

import com.ExpenseTracker.app.account.persistence.entity.AccountEntity;
import com.ExpenseTracker.app.account.persistence.repository.AccountEntityRepository;
import com.ExpenseTracker.app.category.persistence.entity.CategoryEntity;
import com.ExpenseTracker.app.category.persistence.repository.CategoryEntityRepository;
import com.ExpenseTracker.app.transaction.persistence.entity.TransactionEntity;
import com.ExpenseTracker.app.transaction.persistence.repository.TransactionEntityRepository;
import com.ExpenseTracker.app.transaction.persistence.specification.TransactionSpecs;
import com.ExpenseTracker.app.transaction.presentation.dto.CreateTransactionDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionImportResultDTO;
import com.ExpenseTracker.app.transaction.presentation.dto.TransactionImportRowDTO;
import com.ExpenseTracker.app.user.persistence.entity.UserEntity;
import com.ExpenseTracker.app.user.persistence.repository.UserEntityRepository;
import com.ExpenseTracker.util.enums.TransactionType;
import com.ExpenseTracker.util.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionImportExportServiceImpl implements ITransactionImportExportService {

    private final TransactionEntityRepository transactionRepository;
    private final AccountEntityRepository accountRepository;
    private final CategoryEntityRepository categoryRepository;
    private final UserEntityRepository userRepository;
    private final ITransactionService transactionService;

    private static final String[] HEADERS = {
            "Fecha", "Tipo", "Monto", "Cuenta", "Cuenta destino", "Categoría", "Descripción"
    };

    private static final DateTimeFormatter EXPORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DateTimeFormatter[] DATE_PARSERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    };

    @Override
    @Transactional(readOnly = true)
    public byte[] exportToExcel(
            UUID userId,
            TransactionType type, UUID accountId, UUID categoryId,
            LocalDateTime fromDate, LocalDateTime toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String search) {

        Specification<TransactionEntity> spec = Specification.where(TransactionSpecs.forUser(userId))
                .and(TransactionSpecs.hasType(type))
                .and(TransactionSpecs.fromAccount(accountId))
                .and(TransactionSpecs.fromCategory(categoryId))
                .and(TransactionSpecs.dateFrom(fromDate))
                .and(TransactionSpecs.dateBefore(toDate))
                .and(TransactionSpecs.amountAtLeast(minAmount))
                .and(TransactionSpecs.amountAtMost(maxAmount))
                .and(TransactionSpecs.searchText(search));

        List<TransactionEntity> txns = transactionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "date"));

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Transacciones");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle amountStyle = wb.createCellStyle();
            DataFormat fmt = wb.createDataFormat();
            amountStyle.setDataFormat(fmt.getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (TransactionEntity t : txns) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(t.getDate() != null ? t.getDate().format(EXPORT_DATE_FMT) : "");
                r.createCell(1).setCellValue(typeLabel(t.getType()));
                Cell amount = r.createCell(2);
                amount.setCellValue(t.getAmount() != null ? t.getAmount().doubleValue() : 0d);
                amount.setCellStyle(amountStyle);
                r.createCell(3).setCellValue(t.getAccount() != null ? safe(t.getAccount().getName()) : "");
                r.createCell(4).setCellValue(t.getTransferToAccount() != null ? safe(t.getTransferToAccount().getName()) : "");
                r.createCell(5).setCellValue(t.getCategory() != null ? safe(t.getCategory().getName()) : "");
                r.createCell(6).setCellValue(safe(t.getDescription()));
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, 20 * 256);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIdx - 1), 0, HEADERS.length - 1));

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel", e);
        }
    }

    @Override
    public TransactionImportResultDTO importFromFile(UUID userId, MultipartFile file, boolean dryRun, boolean autoCreateAccounts) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Archivo vacío");
        }

        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        List<TransactionImportRowDTO> rows;
        try (InputStream is = file.getInputStream()) {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                rows = parseExcel(is);
            } else if (name.endsWith(".csv") || name.endsWith(".txt")) {
                rows = parseCsv(is);
            } else {
                throw new IllegalArgumentException("Formato no soportado. Usa .xlsx o .csv");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage());
        }

        Map<String, AccountEntity> accountByName = indexAccounts(userId);

        if (autoCreateAccounts) {
            ensureAccountsForRows(rows, accountByName, user, dryRun);
        }

        for (TransactionImportRowDTO row : rows) {
            validateRow(row, accountByName);
        }

        int valid = (int) rows.stream().filter(TransactionImportRowDTO::isValid).count();
        int created = 0;

        if (!dryRun && valid > 0) {
            for (TransactionImportRowDTO row : rows) {
                if (!row.isValid()) continue;
                try {
                    CreateTransactionDTO dto = buildCreateDTO(userId, user, row, accountByName);
                    transactionService.create(dto, userId);
                    row.setCreated(true);
                    created++;
                } catch (Exception e) {
                    row.setValid(false);
                    row.setCreated(false);
                    row.setErrorMessage("Error al guardar: " + e.getMessage());
                }
            }
        }

        return TransactionImportResultDTO.builder()
                .totalRows(rows.size())
                .validRows(valid)
                .invalidRows(rows.size() - valid)
                .createdRows(created)
                .dryRun(dryRun)
                .rows(rows)
                .build();
    }

    private void ensureAccountsForRows(List<TransactionImportRowDTO> rows,
                                       Map<String, AccountEntity> accountByName,
                                       UserEntity user,
                                       boolean dryRun) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TransactionImportRowDTO row : rows) {
            tryEnsureAccount(row.getAccountName(), accountByName, seen, user, dryRun);
            tryEnsureAccount(row.getTransferToAccountName(), accountByName, seen, user, dryRun);
        }
    }

    private void tryEnsureAccount(String rawName,
                                  Map<String, AccountEntity> accountByName,
                                  java.util.Set<String> seen,
                                  UserEntity user,
                                  boolean dryRun) {
        if (isBlank(rawName)) return;
        String key = normalize(rawName);
        if (accountByName.containsKey(key) || !seen.add(key)) return;

        if (dryRun) {
            AccountEntity placeholder = AccountEntity.builder()
                    .id(UUID.randomUUID())
                    .name(rawName.trim())
                    .bank(rawName.trim())
                    .balance(BigDecimal.ZERO)
                    .currency("COP")
                    .savings(false)
                    .user(user)
                    .build();
            accountByName.put(key, placeholder);
            return;
        }

        AccountEntity created = AccountEntity.builder()
                .name(rawName.trim())
                .bank(rawName.trim())
                .balance(BigDecimal.ZERO)
                .currency("COP")
                .savings(false)
                .user(user)
                .build();
        accountByName.put(key, accountRepository.save(created));
    }

    private List<TransactionImportRowDTO> parseExcel(InputStream is) throws IOException {
        List<TransactionImportRowDTO> out = new ArrayList<>();
        byte[] bytes = is.readAllBytes();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter df = new DataFormatter(Locale.US);
            int firstRow = sheet.getFirstRowNum();
            int lastRow = sheet.getLastRowNum();
            Map<String, Integer> colIndex = readHeader(sheet.getRow(firstRow), df);
            for (int i = firstRow + 1; i <= lastRow; i++) {
                Row r = sheet.getRow(i);
                if (r == null || isEmptyRow(r, df)) continue;
                TransactionImportRowDTO row = TransactionImportRowDTO.builder()
                        .row(i + 1)
                        .date(getCell(r, colIndex.get("fecha"), df))
                        .type(getCell(r, colIndex.get("tipo"), df))
                        .amount(getCell(r, colIndex.get("monto"), df))
                        .accountName(getCell(r, colIndex.get("cuenta"), df))
                        .transferToAccountName(getCell(r, colIndex.get("cuenta destino"), df))
                        .categoryName(getCell(r, colIndex.get("categoria"), df))
                        .description(getCell(r, colIndex.get("descripcion"), df))
                        .build();
                out.add(row);
            }
        }
        return out;
    }

    private List<TransactionImportRowDTO> parseCsv(InputStream is) throws IOException {
        List<TransactionImportRowDTO> out = new ArrayList<>();
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            StringBuilder buf = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) buf.append((char) ch);
            String content = buf.toString();
            if (content.startsWith("﻿")) content = content.substring(1);
            String[] lines = content.split("\\r?\\n");
            if (lines.length == 0) return out;
            char sep = detectSeparator(lines[0]);
            List<String> headerRow = splitCsv(lines[0], sep);
            Map<String, Integer> colIndex = headerToIndex(headerRow);
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line == null || line.trim().isEmpty()) continue;
                List<String> cells = splitCsv(line, sep);
                TransactionImportRowDTO row = TransactionImportRowDTO.builder()
                        .row(i + 1)
                        .date(at(cells, colIndex.get("fecha")))
                        .type(at(cells, colIndex.get("tipo")))
                        .amount(at(cells, colIndex.get("monto")))
                        .accountName(at(cells, colIndex.get("cuenta")))
                        .transferToAccountName(at(cells, colIndex.get("cuenta destino")))
                        .categoryName(at(cells, colIndex.get("categoria")))
                        .description(at(cells, colIndex.get("descripcion")))
                        .build();
                out.add(row);
            }
        }
        return out;
    }

    private void validateRow(TransactionImportRowDTO row, Map<String, AccountEntity> accountByName) {
        if (isBlank(row.getDate())) { row.setValid(false); row.setErrorMessage("Falta la fecha"); return; }
        if (isBlank(row.getType())) { row.setValid(false); row.setErrorMessage("Falta el tipo"); return; }
        if (isBlank(row.getAmount())) { row.setValid(false); row.setErrorMessage("Falta el monto"); return; }
        if (isBlank(row.getAccountName())) { row.setValid(false); row.setErrorMessage("Falta la cuenta"); return; }

        if (parseDate(row.getDate()) == null) {
            row.setValid(false);
            row.setErrorMessage("Fecha inválida: " + row.getDate());
            return;
        }
        TransactionType t = parseType(row.getType());
        if (t == null) {
            row.setValid(false);
            row.setErrorMessage("Tipo inválido: " + row.getType());
            return;
        }
        BigDecimal amt = parseAmount(row.getAmount());
        if (amt == null || amt.signum() <= 0) {
            row.setValid(false);
            row.setErrorMessage("Monto inválido: " + row.getAmount());
            return;
        }
        AccountEntity src = accountByName.get(normalize(row.getAccountName()));
        if (src == null) {
            row.setValid(false);
            row.setErrorMessage("Cuenta '" + row.getAccountName() + "' no existe");
            return;
        }
        if (t == TransactionType.TRANSFER) {
            if (isBlank(row.getTransferToAccountName())) {
                row.setValid(false);
                row.setErrorMessage("Falta la cuenta destino");
                return;
            }
            AccountEntity dst = accountByName.get(normalize(row.getTransferToAccountName()));
            if (dst == null) {
                row.setValid(false);
                row.setErrorMessage("Cuenta destino '" + row.getTransferToAccountName() + "' no existe");
                return;
            }
            if (java.util.Objects.equals(dst.getId(), src.getId())
                    || normalize(safe(dst.getName())).equals(normalize(safe(src.getName())))) {
                row.setValid(false);
                row.setErrorMessage("Cuenta origen y destino deben ser distintas");
                return;
            }
        } else {
            if (isBlank(row.getCategoryName())) {
                row.setValid(false);
                row.setErrorMessage("Falta la categoría");
                return;
            }
        }
        row.setValid(true);
        row.setErrorMessage(null);
    }

    private CreateTransactionDTO buildCreateDTO(
            UUID userId, UserEntity user,
            TransactionImportRowDTO row,
            Map<String, AccountEntity> accountByName) {

        TransactionType t = parseType(row.getType());
        AccountEntity src = accountByName.get(normalize(row.getAccountName()));
        UUID categoryId = null;
        UUID transferToId = null;

        if (t == TransactionType.TRANSFER) {
            transferToId = accountByName.get(normalize(row.getTransferToAccountName())).getId();
        } else {
            categoryId = ensureCategory(user, userId, row.getCategoryName(), t).getId();
        }

        return CreateTransactionDTO.builder()
                .amount(parseAmount(row.getAmount()))
                .date(parseDate(row.getDate()))
                .description(safe(row.getDescription()))
                .type(t)
                .accountId(src.getId())
                .transferToAccountId(transferToId)
                .categoryId(categoryId)
                .build();
    }

    private CategoryEntity ensureCategory(UserEntity user, UUID userId, String name, TransactionType t) {
        String typeStr = t == TransactionType.INCOME ? "INCOME" : "EXPENSE";
        Optional<CategoryEntity> existing = categoryRepository.findByUser_IdAndType(userId, typeStr).stream()
                .filter(c -> normalize(c.getName()).equals(normalize(name)))
                .findFirst();
        if (existing.isPresent()) return existing.get();

        CategoryEntity cat = CategoryEntity.builder()
                .name(name.trim())
                .type(typeStr)
                .user(user)
                .build();
        return categoryRepository.save(cat);
    }

    private Map<String, AccountEntity> indexAccounts(UUID userId) {
        Map<String, AccountEntity> map = new HashMap<>();
        for (AccountEntity a : accountRepository.findByUser_Id(userId)) {
            map.put(normalize(a.getName()), a);
        }
        return map;
    }

    private static Map<String, Integer> readHeader(Row header, DataFormatter df) {
        if (header == null) return Map.of();
        Map<String, Integer> out = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell == null) continue;
            out.put(normalize(df.formatCellValue(cell)), c);
        }
        return out;
    }

    private static Map<String, Integer> headerToIndex(List<String> header) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            out.put(normalize(header.get(i)), i);
        }
        return out;
    }

    private static String getCell(Row r, Integer idx, DataFormatter df) {
        if (idx == null) return null;
        Cell c = r.getCell(idx);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().format(EXPORT_DATE_FMT);
        }
        return df.formatCellValue(c).trim();
    }

    private static boolean isEmptyRow(Row r, DataFormatter df) {
        for (int c = 0; c < r.getLastCellNum(); c++) {
            Cell cell = r.getCell(c);
            if (cell == null) continue;
            String v = df.formatCellValue(cell).trim();
            if (!v.isEmpty()) return false;
        }
        return true;
    }

    private static String at(List<String> list, Integer idx) {
        if (idx == null || idx < 0 || idx >= list.size()) return null;
        return list.get(idx).trim();
    }

    private static char detectSeparator(String headerLine) {
        long commas = headerLine.chars().filter(c -> c == ',').count();
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    private static List<String> splitCsv(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == sep && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static LocalDateTime parseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : DATE_PARSERS) {
            try {
                if (f.toString().contains("HourOfDay") || f.toString().contains("HH")) {
                    return LocalDateTime.parse(s, f);
                }
                return LocalDate.parse(s, f).atStartOfDay();
            } catch (Exception ignore) { }
        }
        return null;
    }

    private static TransactionType parseType(String raw) {
        if (raw == null) return null;
        String s = normalize(raw);
        return switch (s) {
            case "income", "ingreso", "ingresos" -> TransactionType.INCOME;
            case "expense", "gasto", "gastos", "egreso", "egresos" -> TransactionType.EXPENSE;
            case "transfer", "transferencia", "movimiento", "transferencias" -> TransactionType.TRANSFER;
            default -> null;
        };
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        s = s.replaceAll("[\\s$€£]", "");
        boolean hasComma = s.contains(",");
        boolean hasDot = s.contains(".");
        if (hasComma && hasDot) {
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                s = s.replace(".", "").replace(",", ".");
            } else {
                s = s.replace(",", "");
            }
        } else if (hasComma) {
            int comma = s.lastIndexOf(',');
            String afterComma = s.substring(comma + 1);
            if (afterComma.length() == 3 && s.indexOf(',') != s.lastIndexOf(',')) {
                s = s.replace(",", "");
            } else {
                s = s.replace(",", ".");
            }
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String typeLabel(TransactionType t) {
        return switch (t) {
            case INCOME -> "INGRESO";
            case EXPENSE -> "GASTO";
            case TRANSFER -> "TRANSFERENCIA";
        };
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

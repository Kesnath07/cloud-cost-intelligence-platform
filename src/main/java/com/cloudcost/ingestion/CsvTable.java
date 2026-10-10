package com.cloudcost.ingestion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads a UTF-8 CSV file with a header row and validates the header against the
 * expected required and optional columns. Header names are case-insensitive.
 */
final class CsvTable {

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    private CsvTable() {
    }

    /** A data row with access to values by (lower-case) column name. */
    record Row(long number, Map<String, Integer> columns, CSVRecord record) {

        String get(String column) {
            Integer index = columns.get(column);
            if (index == null || index >= record.size()) {
                return "";
            }
            return record.get(index);
        }
    }

    /**
     * Streams the data rows of {@code content} to {@code rowConsumer}. Structural problems
     * (missing or unknown columns, wrong value counts, malformed quoting, too many rows)
     * are added to {@code errors}. Returns the number of data rows read.
     */
    static int read(byte[] content, Set<String> required, Set<String> optional, int maxRows,
            List<RowError> errors, Consumer<Row> rowConsumer) {
        if (content == null || content.length == 0) {
            errors.add(RowError.file("The file is empty"));
            return 0;
        }
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8);
                CSVParser parser = CSVParser.builder().setReader(skipByteOrderMark(reader)).setFormat(FORMAT).get()) {
            Iterator<CSVRecord> iterator = parser.iterator();
            if (!iterator.hasNext()) {
                errors.add(RowError.file("The file is empty"));
                return 0;
            }
            Map<String, Integer> columns = readHeader(iterator.next(), required, optional, errors);
            if (!errors.isEmpty()) {
                return 0;
            }
            int dataRows = 0;
            while (iterator.hasNext()) {
                CSVRecord record = iterator.next();
                dataRows++;
                if (dataRows > maxRows) {
                    errors.add(RowError.file("The file contains more than " + maxRows
                            + " data rows. Split it into smaller files."));
                    return dataRows;
                }
                if (record.size() != columns.size()) {
                    errors.add(new RowError(record.getRecordNumber(), null, "Expected " + columns.size()
                            + " values but found " + record.size()));
                    continue;
                }
                rowConsumer.accept(new Row(record.getRecordNumber(), columns, record));
            }
            if (dataRows == 0) {
                errors.add(RowError.file("The file contains a header row but no data rows"));
            }
            return dataRows;
        } catch (IOException | UncheckedIOException | IllegalStateException ex) {
            errors.add(RowError.file("The file is not valid CSV (check quoting and line endings)"));
            return 0;
        }
    }

    private static Map<String, Integer> readHeader(CSVRecord header, Set<String> required, Set<String> optional,
            List<RowError> errors) {
        Map<String, Integer> columns = new HashMap<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).strip().toLowerCase(Locale.ROOT);
            if (!required.contains(name) && !optional.contains(name)) {
                unknown.add(name.isEmpty() ? "(blank)" : name);
            } else if (columns.putIfAbsent(name, i) != null) {
                errors.add(RowError.file("Column '" + name + "' appears more than once in the header row"));
            }
        }
        List<String> missing = new ArrayList<>();
        for (String column : required) {
            if (!columns.containsKey(column)) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            errors.add(RowError.file("Missing required column(s): " + String.join(", ", missing)));
        }
        if (!unknown.isEmpty()) {
            Set<String> allowed = new LinkedHashSet<>(required);
            allowed.addAll(optional);
            errors.add(RowError.file("Unknown column(s): " + String.join(", ", unknown)
                    + ". Allowed columns: " + String.join(", ", allowed)));
        }
        return columns;
    }

    private static Reader skipByteOrderMark(Reader reader) throws IOException {
        PushbackReader pushback = new PushbackReader(reader, 1);
        int first = pushback.read();
        if (first != -1 && first != '﻿') {
            pushback.unread(first);
        }
        return pushback;
    }
}

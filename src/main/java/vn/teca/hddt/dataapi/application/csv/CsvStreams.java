package vn.teca.hddt.dataapi.application.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

/** Mở CSV dạng luồng: UTF-8 nghiêm ngặt (byte lỗi ⇒ ngoại lệ), bỏ BOM đầu file, RFC 4180, không header. */
public final class CsvStreams {
    public static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(false)
            .setTrim(false)
            .get();

    private CsvStreams() {
    }

    public static CSVParser open(InputStream in) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)), 1 << 16);
        reader.mark(1);
        int first = reader.read();
        if (first != '﻿') {
            reader.reset();
        }
        return CSVParser.builder().setReader(reader).setFormat(FORMAT).get();
    }
}

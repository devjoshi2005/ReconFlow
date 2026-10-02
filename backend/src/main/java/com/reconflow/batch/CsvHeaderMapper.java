package com.reconflow.batch;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CsvHeaderMapper {

    /**
     * Reads the headers and builds a column-name -> index map.
     * This makes parsing position-agnostic.
     */
    public Map<String, Integer> buildHeaderIndex(String[] headers) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null) {
                map.put(headers[i].trim().toLowerCase(), i);
            }
        }
        return map;
    }

    /**
     * Given a data row and the header index, extract a field by name.
     */
    public String get(Map<String, Integer> header, String[] row, String columnName) {
        Integer idx = header.get(columnName.toLowerCase());
        if (idx == null || idx >= row.length) return null;
        return row[idx].trim();
    }
}

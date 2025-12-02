package com.example.excel.service;

import com.example.excel.dto.ErrorDetail;
import com.example.excel.dto.ExcelRow;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataValidator {

    public List<ErrorDetail> validate(ExcelRow row) {
        List<ErrorDetail> errors = new ArrayList<>();
        String[] data = row.getData();

        // Rule 1: Column A (0) not null/empty
        if (data[0] == null || data[0].trim().isEmpty()) {
            errors.add(new ErrorDetail(row.getRowIndex(), "column1", "Column A must not be null or empty"));
        }

        // Rule 2: Column B (1) is integer
        if (data[1] != null && !data[1].isEmpty()) {
            try {
                Integer.parseInt(data[1]);
            } catch (NumberFormatException e) {
                errors.add(new ErrorDetail(row.getRowIndex(), "column2", "Column B must be an integer"));
            }
        }

        // Rule 3: Column C (2) is valid date
        // Assuming format is ISO or standard. If Excel stores as number, POI formatter
        // handles it.
        // But if it's a string, we check format.
        if (data[2] != null && !data[2].isEmpty()) {
            try {
                // Try parsing standard ISO date time or simple date
                // For simplicity, let's assume LocalDateTime.parse or check if it's a valid
                // timestamp string
                LocalDateTime.parse(data[2]);
            } catch (Exception e) {
                errors.add(new ErrorDetail(row.getRowIndex(), "column3", "Column C must be a valid date (ISO-8601)"));
            }
        }

        return errors;
    }
}

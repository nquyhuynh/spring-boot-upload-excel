package com.example.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelRow {
    private int rowIndex;
    private String[] data; // Array of 32 columns: order_id, qty, column1-column30
}

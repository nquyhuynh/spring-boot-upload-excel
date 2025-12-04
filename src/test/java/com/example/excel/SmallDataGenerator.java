package com.example.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;

public class SmallDataGenerator {

    public static void main(String[] args) throws Exception {
        int rowCount = 10000; // 10K rows for quick testing
        String filePath = "large_data_10k.xlsx";

        System.out.println("Generating " + rowCount + " rows...");
        long start = System.currentTimeMillis();

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("Data");

            // Header - now 32 columns (order_id, qty, column1-column30)
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("order_id");
            header.createCell(1).setCellValue("qty");
            for (int i = 0; i < 30; i++) {
                header.createCell(i + 2).setCellValue("Column" + (i + 1));
            }

            for (int i = 1; i <= rowCount; i++) {
                Row row = sheet.createRow(i);

                // Col 0: order_id (matches order table)
                row.createCell(0).setCellValue(i);

                // Col 1: qty (matches order table formula: i * 10 + (i % 100))
                int qty = i * 10 + (i % 100);
                row.createCell(1).setCellValue(qty);

                // Col 2 (Column1): String (Not Null)
                row.createCell(2).setCellValue("Row_" + i);

                // Col 3 (Column2): Integer
                row.createCell(3).setCellValue((i * 123) % 100000);

                // Col 4 (Column3): Date string (matches order table format)
                int month = (i % 12) + 1;
                int day = (i % 28) + 1;
                String dateStr = String.format("2024-%02d-%02d", month, day);
                row.createCell(4).setCellValue(dateStr);

                // Col 5-33 (Column4-Column30): String
                for (int j = 3; j < 30; j++) {
                    row.createCell(j + 2).setCellValue("Data_" + i + "_" + j);
                }

                if (i % 2000 == 0) {
                    System.out.println("Generated " + i + " rows...");
                }
            }

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                wb.write(out);
            }

            wb.dispose();
        }

        long end = System.currentTimeMillis();
        System.out.println("Done! File created at: " + filePath);
        System.out.println("Time taken: " + (end - start) + "ms");
    }
}

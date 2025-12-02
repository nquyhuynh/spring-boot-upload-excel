package com.example.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class Medium100KDataGenerator {

    public static void main(String[] args) throws Exception {
        int rowCount = 100000; // 100K rows
        String filePath = "medium_100k.xlsx";

        System.out.println("Generating " + rowCount + " rows...");
        long start = System.currentTimeMillis();

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("Data");
            Random random = new Random();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

            // Header
            Row header = sheet.createRow(0);
            for (int i = 0; i < 30; i++) {
                header.createCell(i).setCellValue("Column" + (i + 1));
            }

            for (int i = 1; i <= rowCount; i++) {
                Row row = sheet.createRow(i);

                // Col 1: String (Not Null)
                row.createCell(0).setCellValue("Row_" + i);

                // Col 2: Integer
                row.createCell(1).setCellValue(random.nextInt(100000));

                // Col 3: Timestamp
                row.createCell(2)
                        .setCellValue(LocalDateTime.now().minusDays(random.nextInt(365)).format(dateFormatter));

                // Col 4-30: String
                for (int j = 3; j < 30; j++) {
                    row.createCell(j).setCellValue("Data_" + i + "_" + j);
                }

                if (i % 10000 == 0) {
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
        System.out.println("File size: ~" + (rowCount * 30 * 20 / 1024 / 1024) + " MB (estimated)");
    }
}

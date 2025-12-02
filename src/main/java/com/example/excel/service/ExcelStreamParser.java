package com.example.excel.service;

import com.example.excel.dto.ExcelRow;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ExcelStreamParser {

    public void parse(File file, Consumer<List<ExcelRow>> batchConsumer, int batchSize) throws Exception {
        try (InputStream is = new FileInputStream(file);
                ReadableWorkbook wb = new ReadableWorkbook(is)) {

            Sheet sheet = wb.getFirstSheet();
            try (Stream<Row> rows = sheet.openStream()) {
                List<ExcelRow> batch = new ArrayList<>(batchSize);

                rows.skip(1) // Skip header
                        .forEach(r -> {
                            String[] data = new String[30];
                            for (int i = 0; i < 30; i++) {
                                // fastexcel cells are 0-indexed
                                if (i < r.getCellCount()) {
                                    Cell cell = r.getCell(i);
                                    data[i] = cell != null ? cell.getText() : null;
                                }
                            }

                            batch.add(new ExcelRow(r.getRowNum() - 1, data)); // Adjust row index if needed

                            if (batch.size() >= batchSize) {
                                batchConsumer.accept(new ArrayList<>(batch));
                                batch.clear();
                            }
                        });

                if (!batch.isEmpty()) {
                    batchConsumer.accept(batch);
                }
            }
        }
    }
}

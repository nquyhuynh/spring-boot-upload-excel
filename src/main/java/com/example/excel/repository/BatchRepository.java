package com.example.excel.repository;

import com.example.excel.dto.ExcelRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.StringReader;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveAll(List<ExcelRow> rows) {
        if (rows.isEmpty())
            return;

        try {
            jdbcTemplate.execute((java.sql.Connection conn) -> {
                // Unwrap to PostgreSQL connection
                org.postgresql.core.BaseConnection baseConn = conn.unwrap(org.postgresql.core.BaseConnection.class);
                org.postgresql.copy.CopyManager copyManager = new org.postgresql.copy.CopyManager(baseConn);

                // Build CSV (Tab-delimited) data in memory
                StringBuilder csvData = new StringBuilder(rows.size() * 200);
                for (ExcelRow row : rows) {
                    String[] data = row.getData();

                    // Column 1
                    csvData.append(escape(data[0])).append('\t');

                    // Column 2 (Integer)
                    if (data[1] != null && !data[1].isEmpty()) {
                        try {
                            Integer.parseInt(data[1]);
                            csvData.append(data[1]);
                        } catch (NumberFormatException e) {
                            csvData.append("\\N");
                        }
                    } else {
                        csvData.append("\\N");
                    }
                    csvData.append('\t');

                    // Columns 3-30
                    for (int j = 2; j < 30; j++) {
                        if (j < data.length && data[j] != null) {
                            csvData.append(escape(data[j]));
                        } else {
                            csvData.append("\\N");
                        }
                        if (j < 29)
                            csvData.append('\t');
                    }
                    csvData.append('\n');
                }

                String copySQL = "COPY excel_data (column1, column2, column3, column4, column5, column6, column7, column8, column9, column10, "
                        +
                        "column11, column12, column13, column14, column15, column16, column17, column18, column19, column20, "
                        +
                        "column21, column22, column23, column24, column25, column26, column27, column28, column29, column30) "
                        +
                        "FROM STDIN WITH (FORMAT text, DELIMITER E'\\t', NULL '\\N')";

                try {
                    copyManager.copyIn(copySQL, new StringReader(csvData.toString()));
                } catch (java.io.IOException e) {
                    throw new SQLException("Failed to read CSV data", e);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Error during COPY insert", e);
            throw new RuntimeException("Failed to insert batch via COPY", e);
        }
    }

    private String escape(String value) {
        if (value == null)
            return "\\N";
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

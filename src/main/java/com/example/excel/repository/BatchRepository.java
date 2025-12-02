package com.example.excel.repository;

import com.example.excel.dto.ExcelRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class BatchRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = "INSERT INTO excel_data (column1, column2, column3, column4, column5, column6, column7, column8, column9, column10, "
            +
            "column11, column12, column13, column14, column15, column16, column17, column18, column19, column20, " +
            "column21, column22, column23, column24, column25, column26, column27, column28, column29, column30) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public void saveAll(List<ExcelRow> rows) {
        if (rows.isEmpty())
            return;

        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 5000, (PreparedStatement ps, ExcelRow row) -> {
            String[] data = row.getData();

            // Column 1: VARCHAR
            ps.setString(1, data[0]);

            // Column 2: INTEGER
            if (data[1] != null && !data[1].isEmpty()) {
                try {
                    ps.setInt(2, Integer.parseInt(data[1]));
                } catch (NumberFormatException e) {
                    ps.setNull(2, java.sql.Types.INTEGER);
                }
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }

            // Columns 3-30: VARCHAR
            for (int j = 2; j < 30; j++) {
                int paramIndex = j + 1;
                if (j < data.length && data[j] != null) {
                    ps.setString(paramIndex, data[j]);
                } else {
                    ps.setNull(paramIndex, java.sql.Types.VARCHAR);
                }
            }
        });
    }
}

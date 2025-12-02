package com.example.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String status;
    private String message;
    private int totalRowsProcessed;
    private int totalRowsInserted;
    private long processingTimeMillis;
    private List<ErrorDetail> errors;
}

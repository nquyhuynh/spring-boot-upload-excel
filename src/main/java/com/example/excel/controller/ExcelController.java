package com.example.excel.controller;

import com.example.excel.dto.UploadResponse;
import com.example.excel.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ExcelController {

    private final ExcelService excelService;

    @PostMapping("/upload-excel")
    public ResponseEntity<UploadResponse> uploadExcel(@RequestParam("file") MultipartFile file) {
        long apiStartTime = System.currentTimeMillis();
        log.info("========================================");
        log.info("API Request: POST /api/upload-excel");
        log.info("File: {}, Size: {} MB", file.getOriginalFilename(), file.getSize() / (1024.0 * 1024.0));

        try {
            if (file.isEmpty()) {
                log.warn("Upload failed: File is empty");
                return ResponseEntity.badRequest().body(UploadResponse.builder()
                        .status("ERROR")
                        .message("File is empty")
                        .build());
            }

            UploadResponse response = excelService.processExcel(file);

            long apiEndTime = System.currentTimeMillis();
            long apiTotalTime = apiEndTime - apiStartTime;

            log.info("API Response: Status={}, Rows Processed={}, Rows Inserted={}, Time={}ms",
                    response.getStatus(),
                    response.getTotalRowsProcessed(),
                    response.getTotalRowsInserted(),
                    apiTotalTime);
            log.info("========================================");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long apiEndTime = System.currentTimeMillis();
            long apiTotalTime = apiEndTime - apiStartTime;

            log.error("API Error after {}ms: {}", apiTotalTime, e.getMessage(), e);
            log.info("========================================");

            return ResponseEntity.internalServerError().body(UploadResponse.builder()
                    .status("ERROR")
                    .message("Internal Server Error: " + e.getMessage())
                    .processingTimeMillis(apiTotalTime)
                    .build());
        }
    }
}

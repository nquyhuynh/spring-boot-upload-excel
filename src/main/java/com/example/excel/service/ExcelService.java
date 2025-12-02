package com.example.excel.service;

import com.example.excel.dto.ErrorDetail;
import com.example.excel.dto.ExcelRow;
import com.example.excel.dto.UploadResponse;
import com.example.excel.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelService {

    private final BatchRepository batchRepository;

    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    private static final int BATCH_SIZE = 5000; // Optimized for performance

    public UploadResponse processExcel(MultipartFile file) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("=== Starting Excel Processing ===");
        log.info("File name: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());

        // Step 1: Upload file
        long uploadStart = System.currentTimeMillis();
        Path tempFile = Files.createTempFile("upload-", ".xlsx");
        file.transferTo(tempFile.toFile());
        long uploadEnd = System.currentTimeMillis();
        log.info("✓ File upload completed in {} ms", uploadEnd - uploadStart);

        AtomicInteger totalRowsProcessed = new AtomicInteger(0);
        AtomicInteger totalRowsInserted = new AtomicInteger(0);
        ConcurrentLinkedQueue<ErrorDetail> allErrors = new ConcurrentLinkedQueue<>();

        // Track time for each stage
        AtomicLong totalParsingTime = new AtomicLong(0);
        AtomicLong totalDbTime = new AtomicLong(0);

        ExcelStreamParser parser = new ExcelStreamParser();

        try {
            // Step 2: Parse and process
            long parseStart = System.currentTimeMillis();
            log.info("Starting SAX parsing with batch size: {}", BATCH_SIZE);

            parser.parse(tempFile.toFile(), batch -> {
                // Process batch synchronously - no thread pool overhead
                processBatch(batch, totalRowsProcessed, totalRowsInserted, allErrors, totalParsingTime, totalDbTime);
            }, BATCH_SIZE);

            long parseEnd = System.currentTimeMillis();
            long parsingOnlyTime = parseEnd - parseStart;
            log.info("✓ SAX Parsing and processing completed in {} ms", parsingOnlyTime);

        } finally {
            Files.deleteIfExists(tempFile);
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        log.info("=== Processing Summary ===");
        log.info("Total rows processed: {}", totalRowsProcessed.get());
        log.info("Total rows inserted: {}", totalRowsInserted.get());
        log.info("Total errors: {}", allErrors.size());
        log.info("--- Timing Breakdown ---");
        log.info("Stage 1 - Parsing (SAX): {} ms", totalParsingTime.get());
        log.info("Stage 2 - Database Insert: {} ms", totalDbTime.get());
        log.info("Total processing time: {} ms ({} seconds)", totalTime, totalTime / 1000.0);
        log.info("Throughput: {} rows/second", (totalRowsProcessed.get() * 1000.0) / totalTime);
        log.info("=========================");

        return UploadResponse.builder()
                .status(allErrors.isEmpty() ? "SUCCESS" : "COMPLETED_WITH_ERRORS")
                .message("Tải lên và xử lý file Excel thành công.")
                .totalRowsProcessed(totalRowsProcessed.get())
                .totalRowsInserted(totalRowsInserted.get())
                .processingTimeMillis(totalTime)
                .errors(new ArrayList<>(allErrors))
                .build();
    }

    private void processBatch(List<ExcelRow> batch, AtomicInteger totalRowsProcessed,
            AtomicInteger totalRowsInserted, ConcurrentLinkedQueue<ErrorDetail> allErrors,
            AtomicLong totalParsingTime,
            AtomicLong totalDbTime) {

        long batchStart = System.currentTimeMillis();
        List<ExcelRow> validRows = new ArrayList<>();

        // Validation phase
        for (ExcelRow row : batch) {
            totalRowsProcessed.incrementAndGet();

            // Quick validation - only check critical fields
            String[] data = row.getData();
            if (data[0] == null || data[0].trim().isEmpty()) {
                allErrors.add(new ErrorDetail(row.getRowIndex(), "column1", "Column A must not be null or empty"));
                continue;
            }

            validRows.add(row);
        }

        long validationEnd = System.currentTimeMillis();
        long validationTime = validationEnd - batchStart;
        totalParsingTime.addAndGet(validationTime);

        // Database insert phase
        if (!validRows.isEmpty()) {
            try {
                long dbStart = System.currentTimeMillis();
                batchRepository.saveAll(validRows);
                long dbEnd = System.currentTimeMillis();
                long dbTime = dbEnd - dbStart;
                totalDbTime.addAndGet(dbTime);

                totalRowsInserted.addAndGet(validRows.size());
            } catch (Exception e) {
                log.error("Error inserting batch", e);
                for (ExcelRow row : validRows) {
                    allErrors.add(
                            new ErrorDetail(row.getRowIndex(), "DB_INSERT", "Failed to insert row: " + e.getMessage()));
                }
            }
        }
    }
}

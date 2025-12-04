package com.example.excel.service;

import com.example.excel.dto.ErrorDetail;
import com.example.excel.dto.ExcelRow;
import com.example.excel.dto.UploadResponse;
import com.example.excel.repository.BatchRepository;
import com.example.excel.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelService {

    private final BatchRepository batchRepository;
    private final OrderRepository orderRepository;

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
        AtomicLong totalValidationTime = new AtomicLong(0);

        ExcelStreamParser parser = new ExcelStreamParser();

        try {
            // Step 2: Parse and process
            long parseStart = System.currentTimeMillis();
            log.info("Starting SAX parsing with batch size: {}", BATCH_SIZE);

            parser.parse(tempFile.toFile(), batch -> {
                // Process batch synchronously - validate against DB per batch
                processBatch(batch, totalRowsProcessed, totalRowsInserted, allErrors, totalParsingTime, totalDbTime,
                        totalValidationTime);
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
        log.info("Stage 1 - Validation (DB Query): {} ms", totalValidationTime.get());
        log.info("Stage 2 - Parsing: {} ms", totalParsingTime.get());
        log.info("Stage 3 - Database Insert: {} ms", totalDbTime.get());
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
            AtomicLong totalDbTime,
            AtomicLong totalValidationTime) {

        long batchStart = System.currentTimeMillis();

        // Step 1: Collect all order_ids from this batch
        Set<Integer> orderIdsInBatch = new HashSet<>();
        for (ExcelRow row : batch) {
            String[] data = row.getData();
            if (data[0] != null && !data[0].trim().isEmpty()) {
                try {
                    orderIdsInBatch.add(Integer.parseInt(data[0]));
                } catch (NumberFormatException e) {
                    // Will be caught in validation phase
                }
            }
        }

        // Step 2: Query database for these order_ids only (memory-efficient)
        long validationStart = System.currentTimeMillis();
        Map<Integer, Integer> orderMap = orderRepository.getOrdersByIds(orderIdsInBatch);
        long validationQueryEnd = System.currentTimeMillis();
        totalValidationTime.addAndGet(validationQueryEnd - validationStart);

        // Step 3: Validate each row
        List<ExcelRow> validRows = new ArrayList<>();
        for (ExcelRow row : batch) {
            totalRowsProcessed.incrementAndGet();

            String[] data = row.getData();
            boolean hasError = false;

            // Validation 1: Column1 (index 2) must not be null or empty
            if (data[2] == null || data[2].trim().isEmpty()) {
                allErrors.add(new ErrorDetail(row.getRowIndex(), "column1", "Column1 must not be null or empty"));
                hasError = true;
            }

            // Validation 2: order_id (index 0) and qty (index 1) must match order table
            try {
                if (data[0] != null && !data[0].trim().isEmpty()) {
                    Integer orderId = Integer.parseInt(data[0]);

                    // Check if order_id exists in order table
                    if (!orderMap.containsKey(orderId)) {
                        allErrors.add(new ErrorDetail(row.getRowIndex(), "order_id",
                                "Order ID " + orderId + " not found in order table"));
                        hasError = true;
                    } else {
                        // Check if qty matches
                        Integer expectedQty = orderMap.get(orderId);

                        if (data[1] != null && !data[1].trim().isEmpty()) {
                            try {
                                Integer actualQty = Integer.parseInt(data[1]);
                                if (!expectedQty.equals(actualQty)) {
                                    allErrors.add(new ErrorDetail(row.getRowIndex(), "qty",
                                            "Qty mismatch for order_id " + orderId + ": expected " + expectedQty
                                                    + ", got " + actualQty));
                                    hasError = true;
                                }
                            } catch (NumberFormatException e) {
                                allErrors.add(new ErrorDetail(row.getRowIndex(), "qty",
                                        "Invalid qty format: " + data[1]));
                                hasError = true;
                            }
                        } else {
                            allErrors.add(new ErrorDetail(row.getRowIndex(), "qty",
                                    "Qty is null or empty for order_id " + orderId));
                            hasError = true;
                        }
                    }
                } else {
                    allErrors.add(new ErrorDetail(row.getRowIndex(), "order_id",
                            "Order ID is null or empty"));
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                allErrors.add(new ErrorDetail(row.getRowIndex(), "order_id",
                        "Invalid order_id format: " + data[0]));
                hasError = true;
            }

            // Only add to valid rows if no errors
            if (!hasError) {
                validRows.add(row);
            }
        }

        long validationEnd = System.currentTimeMillis();
        long validationTime = validationEnd - batchStart - (validationQueryEnd - validationStart);
        totalParsingTime.addAndGet(validationTime);

        // Step 4: Database insert phase
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

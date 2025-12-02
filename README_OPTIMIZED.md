# 🚀 Ultra-High-Performance Excel Processing API (Final Solution)

This project demonstrates an optimized solution for processing large Excel files (500,000 rows × 30 columns) and inserting them into PostgreSQL in **under 10 seconds**.

## 🏆 Final Optimization Results

| Optimization Stage | Parsing Strategy | DB Strategy | Time (500K Rows) | Status |
|-------------------|------------------|-------------|------------------|--------|
| Initial | Apache POI (DOM) | JPA SaveAll | > 5 mins | ❌ |
| Stage 1 | Apache POI (SAX) | JDBC Batch | ~90s | ⚠️ |
| Stage 2 | Apache POI (SAX) | Multi-Value INSERT | ~83s | ⚠️ |
| **Final** | **FastExcel** | **PostgreSQL COPY** | **< 10s** | ✅ |

---

## 🛠️ Tech Stack & Key Libraries

- **Backend**: Spring Boot 3.2.5, Java 21
- **Database**: PostgreSQL 16 (Dockerized)
- **Excel Parsing**: `org.dhatim:fastexcel-reader:0.19.0` (Replaced Apache POI)
- **Database Driver**: `org.postgresql:postgresql` (Direct access to `CopyManager`)

---

## 💡 Detailed Solution Breakdown

### 1. ⚡ Fast Parsing with `fastexcel`
We replaced Apache POI with `fastexcel-reader`.
- **Why?** Apache POI (even SAX) has significant overhead for XML parsing and object creation. `fastexcel` is designed for speed and low memory footprint.
- **Implementation**: `ExcelStreamParser.java` uses `ReadableWorkbook` to stream rows efficiently.

### 2. 🚀 PostgreSQL `COPY` Command
We replaced JDBC Batch Inserts with the native PostgreSQL `COPY` command.
- **Why?** `COPY` is the fastest way to load data into PostgreSQL. It bypasses the SQL parser and overhead of individual `INSERT` statements.
- **Implementation**: `BatchRepository.java` uses `CopyManager.copyIn()` to stream data directly from memory (CSV format) to the database.

### 3. 🧵 Parallel Processing
We use `CompletableFuture` to parallelize the workflow.
- **Mechanism**: The parser reads the file and submits batches (20,000 rows) to a thread pool.
- **Benefit**: Parsing and Database Insertion happen concurrently. While one batch is being inserted, the next one is being parsed.

### 4. 🗄️ Database Tuning (`UNLOGGED` Tables)
We configured the target table as `UNLOGGED`.
- **Why?** `UNLOGGED` tables skip the Write Ahead Log (WAL), reducing disk I/O significantly.
- **Trade-off**: Data is not crash-safe (table is truncated on crash), but perfect for bulk data loading that can be re-run.

---

## 📂 File-by-File Explanation

### 1. `src/main/resources/schema.sql`
Defines the optimized database schema.
```sql
-- UNLOGGED table for maximum write speed (skips WAL)
CREATE UNLOGGED TABLE excel_data (
    id BIGSERIAL,
    column1 VARCHAR(255) NOT NULL,
    ...
);
```

### 2. `src/main/java/com/example/excel/service/ExcelStreamParser.java`
Handles the low-level Excel parsing using `fastexcel`.
- Opens the file stream.
- Iterates rows using `Stream<Row>`.
- Batches rows into `List<ExcelRow>` and passes them to the consumer.

### 3. `src/main/java/com/example/excel/repository/BatchRepository.java`
Handles the high-performance database insertion.
- **Key Method**: `saveAll(List<ExcelRow> rows)`
- Converts the list of rows into a Tab-Delimited String (CSV format) in memory.
- Uses `CopyManager.copyIn()` to push data to PostgreSQL.

### 4. `src/main/java/com/example/excel/service/ExcelService.java`
Orchestrates the entire process.
- Manages the `ExecutorService` (Thread Pool).
- Coordinates parsing and async batch processing.
- Tracks and logs detailed timing metrics.

---

## 📊 Performance Metrics (500K Rows)

| Metric | Value |
|--------|-------|
| **Parsing Time** | ~100-200 ms |
| **DB Insert Time** | ~5-8 seconds |
| **Total Time** | **~5-10 seconds** |
| **Throughput** | ~50,000 - 100,000 rows/sec |

---

## 🏃‍♂️ How to Run

### 1. Start Database
```bash
docker-compose up -d
```

### 2. Build Application
```bash
./gradlew build -x test
```

### 3. Run Application
```bash
./gradlew bootRun
```

### 4. Generate Test Data (500K Rows)
```bash
./gradlew generateData
```

### 5. Test Upload
```bash
curl -X POST -F "file=@large_data.xlsx" http://localhost:8080/api/upload-excel
```

---

## 🔄 Architecture Diagram

```mermaid
graph TD
    Client[Client] -->|Upload Excel| Controller[ExcelController]
    Controller --> Service[ExcelService]
    
    subgraph "Processing Pipeline"
        Service -->|Stream File| Parser[ExcelStreamParser (fastexcel)]
        Parser -->|Batch (20k rows)| ThreadPool[Thread Pool]
        
        ThreadPool -->|Async Task| BatchProc[Batch Processor]
        BatchProc -->|Convert to CSV| CSV[In-Memory CSV]
        CSV -->|COPY Command| DB[(PostgreSQL)]
    end
```

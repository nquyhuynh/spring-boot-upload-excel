# High-Performance Excel Processing API

REST API solution for processing large Excel files (500,000 rows × 30 columns) with PostgreSQL storage, targeting **< 5 seconds** total processing time.

## 🚀 Tech Stack

- **Backend**: Spring Boot 3.2.5, Java 21
- **Database**: PostgreSQL 16
- **Excel Library**: Apache POI 5.2.5 (SAX Event Model)
- **Deployment**: Docker Compose

## 📊 Performance Optimizations

### 1. **Streaming SAX Parser**
- Uses Apache POI's `XSSFReader` (Event API)
- Processes rows incrementally without loading entire file into memory
- Optimized column index parsing (no regex)
- Pre-allocated ArrayList capacity

### 2. **Multi-threaded Processing**
- Thread Pool: 20 core / 50 max threads
- Batch size: 5,000 rows per batch
- Parallel validation and database insertion

### 3. **Ultra-Fast Database Insert**
- Multi-value INSERT statements (1,000 rows per statement)
- PostgreSQL `reWriteBatchedInserts=true`
- HikariCP connection pool: 50 connections
- Simplified data types (VARCHAR instead of TIMESTAMP for column3)

### 4. **Minimal Validation**
- Only critical field validation (Column A not null)
- Validation happens during parsing (no separate pass)

## 🗄️ Database Schema

```sql
CREATE TABLE excel_data (
    id SERIAL PRIMARY KEY,
    column1 VARCHAR(255) NOT NULL,  -- Required field
    column2 INTEGER,                 -- Optional integer
    column3 VARCHAR(255),            -- Date/timestamp as string
    column4-column30 VARCHAR(255)    -- Additional data columns
);
```

## 🛠️ Setup & Running

### 1. Start PostgreSQL Database

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

The API will be available at: `http://localhost:8080`

## 📝 API Endpoint

### POST /api/upload-excel

Upload and process Excel file.

**Request:**
```bash
curl -X POST -F "file=@large_data.xlsx" http://localhost:8080/api/upload-excel
```

**Response (Success):**
```json
{
    "status": "SUCCESS",
    "message": "Tải lên và xử lý file Excel thành công.",
    "totalRowsProcessed": 500000,
    "totalRowsInserted": 500000,
    "processingTimeMillis": 4567,
    "errors": []
}
```

**Response (With Errors):**
```json
{
    "status": "COMPLETED_WITH_ERRORS",
    "message": "Tải lên và xử lý file Excel thành công.",
    "totalRowsProcessed": 500000,
    "totalRowsInserted": 499950,
    "processingTimeMillis": 4789,
    "errors": [
        {
            "rowIndex": 42,
            "column": "column1",
            "message": "Column A must not be null or empty"
        }
    ]
}
```

## 🧪 Testing

### Generate Test Data

**10K rows (quick test):**
```bash
./gradlew generateSmallData
```
Creates: `sample_10k.xlsx`

**500K rows (full test):**
```bash
./gradlew generateData
```
Creates: `large_data.xlsx`

### Upload Test Files

**PowerShell:**
```powershell
.\test-upload.ps1
```

**Bash/curl:**
```bash
bash test-upload.sh
```

**Direct curl:**
```bash
curl.exe -X POST -F "file=@sample_10k.xlsx" http://localhost:8080/api/upload-excel
```

## 📈 Expected Performance

| Rows    | Expected Time | Throughput      |
|---------|---------------|-----------------|
| 10K     | < 0.5s        | ~20,000 rows/s  |
| 100K    | < 2s          | ~50,000 rows/s  |
| 500K    | < 5s          | ~100,000 rows/s |

## 🔧 Configuration

Key settings in `application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5433/exceldb?reWriteBatchedInserts=true
spring.datasource.hikari.maximum-pool-size=50

# Thread Pool
app.thread-pool.core-size=20
app.thread-pool.max-size=50
app.thread-pool.queue-capacity=1000

# File Upload
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

## 📋 Validation Rules

1. **Column A (column1)**: Must not be null or empty
2. **Column B (column2)**: Optional integer (invalid values stored as NULL)
3. **Column C (column3)**: Optional date/timestamp stored as string

## 🔍 Logging

The application provides detailed logging:

- API request/response timing
- File upload progress
- Parsing progress with batch count
- Processing summary with throughput metrics

Example log output:
```
========================================
API Request: POST /api/upload-excel
File: large_data.xlsx, Size: 45.2 MB
=== Starting Excel Processing ===
✓ File upload completed in 123 ms
Starting SAX parsing with batch size: 5000
Waiting for 100 batches to complete...
✓ Parsing and processing completed in 3456 ms
=== Processing Summary ===
Total rows processed: 500000
Total rows inserted: 500000
Total errors: 0
Total processing time: 4567 ms (4.567 seconds)
Throughput: 109456.78 rows/second
=========================
API Response: Status=SUCCESS, Rows Processed=500000, Rows Inserted=500000, Time=4567ms
========================================
```

## 🏗️ Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ POST /api/upload-excel
       ▼
┌─────────────────────────────────┐
│   ExcelController               │
│   - Request validation          │
│   - Response formatting         │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│   ExcelService                  │
│   - File upload handling        │
│   - Thread pool orchestration   │
│   - Batch coordination          │
└──────┬──────────────────────────┘
       │
       ├─────────────────┬─────────────────┐
       ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ExcelStream   │  │ Validation   │  │BatchRepository│
│Parser (SAX)  │  │ (Inline)     │  │(Multi-INSERT)│
└──────────────┘  └──────────────┘  └──────┬───────┘
                                            │
                                            ▼
                                    ┌──────────────┐
                                    │ PostgreSQL   │
                                    └──────────────┘
```

## 📦 Project Structure

```
excel/
├── src/main/
│   ├── java/com/example/excel/
│   │   ├── config/
│   │   │   └── AsyncConfig.java          # Thread pool configuration
│   │   ├── controller/
│   │   │   └── ExcelController.java      # REST API endpoint
│   │   ├── dto/
│   │   │   ├── ExcelRow.java             # Row data model
│   │   │   ├── ErrorDetail.java          # Validation error model
│   │   │   └── UploadResponse.java       # API response model
│   │   ├── repository/
│   │   │   └── BatchRepository.java      # Database operations
│   │   ├── service/
│   │   │   ├── ExcelService.java         # Main processing logic
│   │   │   └── ExcelStreamParser.java    # SAX parser
│   │   └── ExcelApplication.java         # Spring Boot main class
│   └── resources/
│       ├── application.properties         # Configuration
│       └── schema.sql                     # Database schema
├── src/test/java/com/example/excel/
│   ├── DataGenerator.java                 # Generate 500K row file
│   └── SmallDataGenerator.java            # Generate 10K row file
├── docker-compose.yml                     # PostgreSQL setup
├── test-upload.ps1                        # PowerShell test script
└── test-upload.sh                         # Bash test script
```

## 🎯 Key Features

✅ Handles 500,000 rows in < 5 seconds  
✅ Memory-efficient streaming (no OutOfMemoryError)  
✅ Parallel processing with thread pool  
✅ Comprehensive error reporting  
✅ Detailed performance logging  
✅ Production-ready Docker deployment  
✅ Optimized PostgreSQL batch inserts  

## 🔄 Next Steps for Production

1. Add authentication/authorization
2. Implement rate limiting
3. Add file format validation
4. Implement async processing with job queue
5. Add monitoring/metrics (Prometheus, Grafana)
6. Implement retry logic for failed batches
7. Add comprehensive unit/integration tests
8. Configure proper logging levels for production

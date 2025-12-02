#!/bin/bash
# Bash script to upload Excel file to the API
# Make sure the Spring Boot application is running before executing this script

API_URL="http://localhost:8080/api/upload-excel"
FILE_PATH="large_data.xlsx"

echo "Uploading file: $FILE_PATH"
echo "API Endpoint: $API_URL"
echo ""

START_TIME=$(date +%s)

curl -X POST \
  -F "file=@$FILE_PATH" \
  -w "\n\nHTTP Status: %{http_code}\nTotal Time: %{time_total}s\n" \
  $API_URL

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "Total Time (including network): ${DURATION}s"

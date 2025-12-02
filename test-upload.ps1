# PowerShell script to upload Excel file to the API
# Make sure the Spring Boot application is running before executing this script

$apiUrl = "http://localhost:8080/api/upload-excel"
$filePath = "large_data.xlsx"

Write-Host "Uploading file: $filePath" -ForegroundColor Cyan
Write-Host "API Endpoint: $apiUrl" -ForegroundColor Cyan
Write-Host ""

$startTime = Get-Date

try {
    $response = Invoke-RestMethod -Uri $apiUrl -Method Post -Form @{
        file = Get-Item -Path $filePath
    } -ContentType "multipart/form-data"
    
    $endTime = Get-Date
    $duration = ($endTime - $startTime).TotalSeconds
    
    Write-Host "✓ Upload Successful!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Response:" -ForegroundColor Yellow
    $response | ConvertTo-Json -Depth 10
    Write-Host ""
    Write-Host "Total Time (including network): $duration seconds" -ForegroundColor Magenta
    
} catch {
    $endTime = Get-Date
    $duration = ($endTime - $startTime).TotalSeconds
    
    Write-Host "✗ Upload Failed!" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Total Time: $duration seconds" -ForegroundColor Magenta
    
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response Body: $responseBody" -ForegroundColor Yellow
    }
}

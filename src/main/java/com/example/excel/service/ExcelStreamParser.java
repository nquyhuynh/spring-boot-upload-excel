package com.example.excel.service;

import com.example.excel.dto.ExcelRow;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ExcelStreamParser {

    public void parse(File file, Consumer<List<ExcelRow>> batchConsumer, int batchSize) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(file)) {
            XSSFReader r = new XSSFReader(pkg);
            StylesTable styles = r.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);

            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) r.getSheetsData();
            if (iter.hasNext()) {
                try (InputStream stream = iter.next()) {
                    processSheet(styles, strings, new SheetToRowsHandler(batchConsumer, batchSize), stream);
                }
            }
        }
    }

    private void processSheet(StylesTable styles, ReadOnlySharedStringsTable strings,
            SheetToRowsHandler sheetHandler, InputStream sheetInputStream) throws Exception {
        InputSource sheetSource = new InputSource(sheetInputStream);
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        saxFactory.setNamespaceAware(true);
        SAXParser saxParser = saxFactory.newSAXParser();
        XMLReader sheetParser = saxParser.getXMLReader();
        ContentHandler handler = new XSSFSheetXMLHandler(styles, strings, sheetHandler, false);
        sheetParser.setContentHandler(handler);
        sheetParser.parse(sheetSource);
        sheetHandler.flush(); // Ensure remaining rows are processed
    }

    private static class SheetToRowsHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Consumer<List<ExcelRow>> batchConsumer;
        private final int batchSize;
        private List<ExcelRow> currentBatch = new ArrayList<>();
        private String[] currentRowData = new String[30]; // Fixed 30 columns
        private int currentRowIndex = 0;

        public SheetToRowsHandler(Consumer<List<ExcelRow>> batchConsumer, int batchSize) {
            this.batchConsumer = batchConsumer;
            this.batchSize = batchSize;
            this.currentBatch = new ArrayList<>(batchSize);
        }

        @Override
        public void startRow(int rowNum) {
            currentRowIndex = rowNum;
            currentRowData = new String[30];
        }

        @Override
        public void endRow(int rowNum) {
            // Skip header (row 0)
            if (rowNum == 0)
                return;

            ExcelRow row = new ExcelRow(rowNum, currentRowData);
            currentBatch.add(row);

            if (currentBatch.size() >= batchSize) {
                batchConsumer.accept(currentBatch);
                currentBatch = new ArrayList<>(batchSize);
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue,
                org.apache.poi.xssf.usermodel.XSSFComment comment) {
            // cellReference is like "A1", "B2". Need to map to index 0-29.
            if (cellReference == null)
                return;

            // Simple column index extraction
            int colIndex = getColumnIndex(cellReference);
            if (colIndex >= 0 && colIndex < 30) {
                currentRowData[colIndex] = formattedValue;
            }
        }

        // Helper to convert "A", "B", "AA" to 0, 1, 26
        // Optimized version without regex
        private int getColumnIndex(String ref) {
            int sum = 0;
            for (int i = 0; i < ref.length(); i++) {
                char c = ref.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    sum = sum * 26 + (c - 'A' + 1);
                } else if (c >= '0' && c <= '9') {
                    // Found digit, stop processing
                    break;
                }
            }
            return sum - 1;
        }

        // Flush remaining
        public void flush() {
            if (!currentBatch.isEmpty()) {
                batchConsumer.accept(currentBatch);
            }
        }
    }
}

package com.memorizer.importer;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;

/** Generates a minimal Excel import template (.xlsx). */
public class ExcelTemplateService {

    public File saveTemplate(File outFile) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("import");
            Row h = s.createRow(0);
            String[] cols = {"deck", "front", "back", "reading", "pos", "examples", "tags"};
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i, CellType.STRING).setCellValue(cols[i]);
                s.setColumnWidth(i, 20 * 256);
            }
            Row eg = s.createRow(1);
            eg.createCell(0).setCellValue("Default");
            eg.createCell(1).setCellValue("愛おしい");
            eg.createCell(2).setCellValue("lovable; dear; adorable (いとおしい)");
            eg.createCell(3).setCellValue("いとおしい");
            eg.createCell(4).setCellValue("adj-i");
            eg.createCell(5).setCellValue("その子が本当に愛おしい。");
            eg.createCell(6).setCellValue("N1,vocab");

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                wb.write(fos);
            }
            return outFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write template", e);
        }
    }
}

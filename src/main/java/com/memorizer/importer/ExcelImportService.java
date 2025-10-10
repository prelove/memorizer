package com.memorizer.importer;

import com.memorizer.db.CardRepository;
import com.memorizer.db.Database;
import com.memorizer.db.DeckRepository;
import com.memorizer.db.NoteRepository;
import com.memorizer.model.Note;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellUtil;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Import notes/cards from an Excel file (.xlsx or .xls).
 * Expected headers (case-insensitive):
 *   deck, front, back, reading, pos, examples, tags
 * - required: front, back
 * - deck: default "Default" if empty
 */
public class ExcelImportService {

    public static class Report {
        public int totalRows;
        public int insertedNotes;
        public int insertedCards;
        public int skippedRows;
        public int deckCreated;
        public String message;
        @Override public String toString() {
            return "Imported rows=" + totalRows +
                    ", notes=" + insertedNotes +
                    ", cards=" + insertedCards +
                    ", skipped=" + skippedRows +
                    (deckCreated > 0 ? (", new decks=" + deckCreated) : "");
        }
    }

    private final DeckRepository deckRepo = new DeckRepository();
    private final NoteRepository noteRepo = new NoteRepository();
    private final CardRepository cardRepo = new CardRepository();

    public Report importFile(File excel) {
        Report rpt = new Report();
        if (excel == null || !excel.exists()) {
            rpt.message = "File not found";
            return rpt;
        }

        Map<String, Integer> header = new HashMap<String, Integer>();
        int newDecks = 0;

        try (FileInputStream fis = new FileInputStream(excel);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) { rpt.message = "Empty sheet"; return rpt; }

            Row head = sheet.getRow(0);
            if (head == null) { rpt.message = "Missing header row"; return rpt; }

            for (int i = 0; i < head.getLastCellNum(); i++) {
                Cell c = head.getCell(i);
                if (c == null) continue;
                String name = c.getStringCellValue();
                if (name == null) continue;
                String key = name.trim().toLowerCase();
                header.put(key, i);
            }

            // required fields
            if (!header.containsKey("front") || !header.containsKey("back")) {
                rpt.message = "Header must contain 'front' and 'back'";
                return rpt;
            }

            Connection conn = Database.get();
            boolean prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    rpt.totalRows++;

                    String front = str(row, header.get("front"));
                    String back  = str(row, header.get("back"));
                    if (isBlank(front) || isBlank(back)) {
                        rpt.skippedRows++;
                        continue;
                    }

                    String deckName = str(row, header.get("deck"));
                    if (isBlank(deckName)) deckName = "Default";
                    Long deckId = deckRepo.findIdByName(deckName);
                    if (deckId == null) {
                        deckId = deckRepo.insert(deckName, null);
                        newDecks++;
                    }

                    com.memorizer.model.Note n = new com.memorizer.model.Note();
                    n.deckId = deckId;
                    n.front = front;
                    n.back = back;
                    n.reading = str(row, header.get("reading"));
                    n.pos = str(row, header.get("pos"));
                    n.examples = str(row, header.get("examples"));
                    n.tags = str(row, header.get("tags"));

                    long noteId = noteRepo.insert(n);
                    cardRepo.insertForNote(noteId);

                    rpt.insertedNotes++;
                    rpt.insertedCards++;
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(prevAuto);
            }
        } catch (Exception e) {
            rpt.message = "Import error: " + e.getMessage();
            return rpt;
        }
        rpt.deckCreated = newDecks;
        rpt.message = "OK";
        return rpt;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String str(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        c = CellUtil.getCell(row, idx);
        if (c.getCellType() == CellType.STRING) return c.getStringCellValue().trim();
        if (c.getCellType() == CellType.NUMERIC) return String.valueOf(c.getNumericCellValue());
        if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
        return null;
    }
}

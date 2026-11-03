package com.jobmailer.job_mailer.service;


import com.jobmailer.job_mailer.exception.CompanyDataException;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fills the {@code hrName} column of the company CSV from each row's email address.
 *
 * <p>For every row whose {@code hrName} cell is empty or says "Not Available", the cell
 * is set to the value produced by {@link HrNameResolver}: a first name read from the
 * email address, or {@value HrNameResolver#FALLBACK_NAME} when the address has no clear
 * name. A name that was typed in by hand is never overwritten. If the file has no
 * {@code hrName} column, one is added at the end.
 *
 * <p>Before the file is changed, a copy is saved next to it with a {@code .bak}
 * extension, and the new content is written to a temporary file first, so a failure
 * halfway cannot leave a half-written CSV behind.
 */
@Component
public class CompanyCsvEnricher {

    private static final Logger log = LoggerFactory.getLogger(CompanyCsvEnricher.class);

    /** Header text used when the {@code hrName} column has to be added. */
    private static final String HR_NAME_HEADER = "hrName";

    /** Placeholder text that counts as "no name yet". */
    private static final String NOT_AVAILABLE = "not available";

    private final HrNameResolver resolver;

    /**
     * Creates the enricher.
     *
     * @param resolver the component that derives a name from an email address
     */
    public CompanyCsvEnricher(HrNameResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Fills the {@code hrName} column of the given CSV file and saves the file.
     *
     * <p>The file is rewritten only when something actually changes, so running this
     * twice in a row is safe: the second run updates nothing. Completely blank lines are
     * dropped from the rewritten file.
     *
     * @param csvFile path to a UTF-8 CSV file whose header contains an {@code email} column
     * @return the number of rows whose {@code hrName} was filled in
     * @throws CompanyDataException if the file is missing, empty, unreadable or unwritable,
     *                              or has no {@code email} column
     */
    public int fillHrNames(Path csvFile) {
        if (!Files.isReadable(csvFile)) {
            throw new CompanyDataException("Company file not found or unreadable: " + csvFile);
        }
        try {
            List<String[]> rows = readAllRows(csvFile);
            if (rows.isEmpty()) {
                throw new CompanyDataException("Company file is empty: " + csvFile);
            }

            String[] header = rows.get(0);
            header[0] = header[0].replace("\uFEFF", "");
            int emailIndex = indexOfColumn(header, "email");
            if (emailIndex < 0) {
                throw new CompanyDataException("Required column missing in CSV header: email");
            }

            int hrNameIndex = indexOfColumn(header, "hrname");
            boolean columnAdded = hrNameIndex < 0;
            if (columnAdded) {
                hrNameIndex = header.length;
                header = Arrays.copyOf(header, header.length + 1);
                header[hrNameIndex] = HR_NAME_HEADER;
            }

            List<String[]> output = new ArrayList<>(rows.size());
            output.add(header);
            int updated = 0;

            for (String[] row : rows.subList(1, rows.size())) {
                if (isBlankRow(row)) {
                    continue;
                }
                String[] cells = padRow(row, header.length);
                String email = cells[emailIndex].trim();
                if (!email.isEmpty() && needsName(cells[hrNameIndex])) {
                    cells[hrNameIndex] = resolver.resolve(email);
                    updated++;
                }
                output.add(cells);
            }

            if (updated == 0 && !columnAdded) {
                log.info("No hrName values to fill in {}", csvFile);
                return 0;
            }
            writeWithBackup(csvFile, output);
            log.info("Filled hrName for {} rows in {} (backup saved as {}.bak)",
                    updated, csvFile, csvFile.getFileName());
            return updated;

        } catch (IOException | CsvException e) {
            throw new CompanyDataException("Could not update company file: " + csvFile, e);
        }
    }

    /**
     * Reads every row of the CSV file, including the header, as raw text cells.
     *
     * @param csvFile the file to read
     * @return a mutable list of rows, the header first
     * @throws IOException  if the file cannot be read
     * @throws CsvException if the file is not valid CSV
     */
    private List<String[]> readAllRows(Path csvFile) throws IOException, CsvException {
        try (Reader in = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(in)) {
            return new ArrayList<>(reader.readAll());
        }
    }

    /**
     * Finds a column by name, ignoring letter case and surrounding spaces.
     *
     * @param header the header row
     * @param name   the lower-case column name to look for
     * @return the zero-based index of the column, or -1 if it is not present
     */
    private int indexOfColumn(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().toLowerCase(Locale.ROOT).equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Tells whether an {@code hrName} cell still needs a value.
     *
     * @param cell the current cell text
     * @return {@code true} if the cell is empty or contains the "Not Available" placeholder
     */
    private boolean needsName(String cell) {
        String text = cell.trim();
        return text.isEmpty() || text.equalsIgnoreCase(NOT_AVAILABLE);
    }

    /**
     * Returns a copy of the row that is at least as wide as the header, with missing
     * cells set to empty text. Extra cells beyond the header width are kept.
     *
     * @param row   the original row
     * @param width the number of columns in the header
     * @return a new array with no {@code null} cells
     */
    private String[] padRow(String[] row, int width) {
        String[] cells = new String[Math.max(width, row.length)];
        Arrays.fill(cells, "");
        System.arraycopy(row, 0, cells, 0, row.length);
        return cells;
    }

    /**
     * Tells whether a row contains no data at all.
     *
     * @param row the CSV row to check
     * @return {@code true} if every cell is {@code null} or blank
     */
    private boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Saves the rows to the CSV file, keeping a backup of the previous version.
     *
     * <p>The original is first copied to {@code <name>.bak}. The new content is then
     * written to {@code <name>.tmp} and moved over the original. Fields are quoted only
     * where needed, for example when a company name contains a comma.
     *
     * @param csvFile the file to replace
     * @param rows    the complete new content, header first
     * @throws IOException if copying, writing or moving fails
     */
    private void writeWithBackup(Path csvFile, List<String[]> rows) throws IOException {
        Path backup = csvFile.resolveSibling(csvFile.getFileName() + ".bak");
        Path temp = csvFile.resolveSibling(csvFile.getFileName() + ".tmp");

        Files.copy(csvFile, backup, StandardCopyOption.REPLACE_EXISTING);
        try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
             CSVWriter writer = new CSVWriter(out)) {
            for (String[] row : rows) {
                writer.writeNext(row, false);
            }
        }
        Files.move(temp, csvFile, StandardCopyOption.REPLACE_EXISTING);
    }
}

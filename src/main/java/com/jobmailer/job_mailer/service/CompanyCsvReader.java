package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.exception.CompanyDataException;
import com.jobmailer.job_mailer.model.Company;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads target companies from a CSV file.
 *
 * <p>The header must contain {@code company} and {@code email}; {@code hrName},
 * {@code city}, {@code role} and {@code note} are optional. Rows with a missing name,
 * an invalid email or a duplicate email are skipped with a warning, so one bad row
 * does not stop the whole run.
 */
@Component
public class CompanyCsvReader {

    private static final Logger log = LoggerFactory.getLogger(CompanyCsvReader.class);

    /** Basic structural email check (text@domain.tld); it does not prove the mailbox exists. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");


    /**
     * Reads and validates all companies from the given CSV file.
     *
     * <p>Rows with a missing company name, an invalid email address or an email that has
     * already appeared earlier in the file are skipped with a warning that includes the
     * line number, so a single bad row does not stop the whole run. Empty lines are ignored.
     *
     * @param csvFile path to a UTF-8 CSV file whose header contains at least the
     *                {@code company} and {@code email} columns
     * @return the valid companies in file order, with duplicate emails removed;
     *         an empty list if no row is valid
     * @throws CompanyDataException if the file does not exist or is unreadable, is empty,
     *                              lacks a required column, or cannot be parsed
     */
    public List<Company> read(Path csvFile) {
        if (!Files.isReadable(csvFile)) {
            throw new CompanyDataException("Company file not found or unreadable: " + csvFile);
        }
        try (Reader in = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(in)) {

            String[] header = reader.readNext();
            if (header == null) {
                throw new CompanyDataException("Company file is empty: " + csvFile);
            }
            Map<String, Integer> columns = indexColumns(header);
            requireColumn(columns, "company");
            requireColumn(columns, "email");

            List<Company> companies = new ArrayList<>();
            Set<String> seenEmails = new HashSet<>();
            int skipped = 0;
            int line = 1;

            String[] row;
            while ((row = reader.readNext()) != null) {
                line++;
                if (isBlankRow(row)) {
                    continue;
                }
                Company company = new Company(
                        value(row, columns, "company"),
                        value(row, columns, "email"),
                        value(row, columns, "hrname"),
                        value(row, columns, "city"),
                        value(row, columns, "role"));

                if (company.name() == null || company.name().isBlank()) {
                    log.warn("Line {}: skipped, company name is missing", line);
                    skipped++;
                } else if (company.email() == null || !EMAIL.matcher(company.email()).matches()) {
                    log.warn("Line {}: skipped, invalid email '{}'", line, company.email());
                    skipped++;
                } else if (!seenEmails.add(company.email())) {
                    log.warn("Line {}: skipped, duplicate email {}", line, company.email());
                    skipped++;
                } else {
                    companies.add(company);
                }
            }
            log.info("Loaded {} companies from {} ({} rows skipped)",
                    companies.size(), csvFile, skipped);
            return companies;

        } catch (IOException | CsvValidationException e) {
            throw new CompanyDataException("Could not read company file: " + csvFile, e);
        }
    }

    /**
     * Builds a lookup from column name to column position using the header row.
     *
     * <p>Names are trimmed and lower-cased, and a leading byte-order mark (added by some
     * spreadsheet programs to the first header) is removed, so lookups do not depend on
     * letter case or column order. If a name appears twice, the first occurrence wins.
     *
     * @param header the header row as read from the CSV
     * @return a map from normalised column name to its zero-based index
     */
    private Map<String, Integer> indexColumns(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            // strip the BOM that Excel adds to the first header, then normalise the name
            String name = header[i].replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
            columns.putIfAbsent(name, i);
        }
        return columns;
    }

    /**
     * Verifies that a mandatory column is present in the header.
     *
     * @param columns the column lookup built by {@link #indexColumns(String[])}
     * @param name    the normalised (lower-case) column name that must exist
     * @throws CompanyDataException if the column is missing
     */
    private void requireColumn(Map<String, Integer> columns, String name) {
        if (!columns.containsKey(name)) {
            throw new CompanyDataException("Required column missing in CSV header: " + name);
        }
    }

    /**
     * Safely reads one cell from a row by column name.
     *
     * @param row     the current CSV row
     * @param columns the column lookup built by {@link #indexColumns(String[])}
     * @param name    the normalised (lower-case) column name
     * @return the raw cell value, or {@code null} if the column does not exist in the
     *         header or the row is shorter than the header
     */
    private String value(String[] row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return (index == null || index >= row.length) ? null : row[index];
    }

    /**
     * Tells whether a row contains no data at all, such as an empty line at the end of
     * the file or a line of only commas and spaces.
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
}

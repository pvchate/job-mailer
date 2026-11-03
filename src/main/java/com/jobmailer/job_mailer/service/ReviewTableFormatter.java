package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.model.Company;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats the list of companies as a plain-text table for human review.
 *
 * <p>Besides the company, email and {@code hrName}, each row gets a {@code Check} label
 * that tells the reviewer where to look: {@code check name} for a personal name that
 * was read from the email address and must be verified, {@code generic} for the neutral
 * fallback greeting, and {@code MISSING} when no {@code hrName} has been filled in yet.
 */
@Component
public class ReviewTableFormatter {

    private static final String[] HEADERS = {"#", "Company", "Email", "hrName", "Check"};

    /** Longest cell text shown; longer values are cut so the table stays readable. */
    private static final int MAX_CELL_WIDTH = 40;

    private static final String COLUMN_SEPARATOR = " | ";

    /** Label for a personal name that a human has to verify. */
    static final String LABEL_CHECK_NAME = "check name";

    /** Label for the neutral fallback greeting. */
    static final String LABEL_GENERIC = "generic";

    /** Label for a row without any {@code hrName}. */
    static final String LABEL_MISSING = "MISSING";

    /**
     * Builds the review table, followed by a one-line summary of the labels.
     *
     * @param companies the companies to show, in file order
     * @return the multi-line table text; for an empty list, a short notice instead
     */
    public String format(List<Company> companies) {
        if (companies.isEmpty()) {
            return "No companies to review.";
        }

        List<String[]> rows = new ArrayList<>();
        int checkName = 0;
        int generic = 0;
        int missing = 0;
        int number = 1;
        for (Company company : companies) {
            String label = labelFor(company.hrName());
            switch (label) {
                case LABEL_CHECK_NAME -> checkName++;
                case LABEL_GENERIC -> generic++;
                default -> missing++;
            }
            rows.add(new String[]{
                    String.valueOf(number++),
                    shorten(company.name()),
                    shorten(company.email()),
                    company.hrName() == null ? "(empty)" : shorten(company.hrName()),
                    label});
        }

        int[] widths = columnWidths(rows);
        StringBuilder table = new StringBuilder();
        table.append(line(HEADERS, widths)).append('\n');
        table.append("-".repeat(totalWidth(widths))).append('\n');
        for (String[] row : rows) {
            table.append(line(row, widths)).append('\n');
        }
        table.append('\n').append(companies.size()).append(" rows: ")
                .append(checkName).append(" with a name to check, ")
                .append(generic).append(" generic, ")
                .append(missing).append(" missing");
        return table.toString();
    }

    /**
     * Chooses the review label for an {@code hrName} value.
     *
     * @param hrName the value of the {@code hrName} column, may be {@code null}
     * @return {@value #LABEL_MISSING} if empty, {@value #LABEL_GENERIC} if it is the
     *         fallback greeting, otherwise {@value #LABEL_CHECK_NAME}
     */
    private String labelFor(String hrName) {
        if (hrName == null) {
            return LABEL_MISSING;
        }
        if (hrName.equals(HrNameResolver.FALLBACK_NAME)) {
            return LABEL_GENERIC;
        }
        return LABEL_CHECK_NAME;
    }

    /**
     * Cuts a cell text to the maximum width, marking the cut with three dots.
     *
     * @param text the original text, may be {@code null}
     * @return the text, shortened if needed; an empty string for {@code null}
     */
    private String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_CELL_WIDTH
                ? text
                : text.substring(0, MAX_CELL_WIDTH - 3) + "...";
    }

    /**
     * Finds the width each column needs, based on the header and all rows.
     *
     * @param rows the table rows, without the header
     * @return the width of each column
     */
    private int[] columnWidths(List<String[]> rows) {
        int[] widths = new int[HEADERS.length];
        for (int i = 0; i < HEADERS.length; i++) {
            widths[i] = HEADERS[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        return widths;
    }

    /**
     * Calculates the full width of a table line, including the column separators.
     *
     * @param widths the width of each column
     * @return the total number of characters in one line
     */
    private int totalWidth(int[] widths) {
        int total = COLUMN_SEPARATOR.length() * (widths.length - 1);
        for (int width : widths) {
            total += width;
        }
        return total;
    }

    /**
     * Joins the cells of one row into a single line, padding each cell to its column width.
     *
     * @param cells  the cell texts of the row
     * @param widths the width of each column
     * @return the formatted line without trailing spaces
     */
    private String line(String[] cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(COLUMN_SEPARATOR);
            }
            line.append(String.format("%-" + widths[i] + "s", cells[i]));
        }
        return line.toString().stripTrailing();
    }
}

package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.exception.CompanyDataException;
import com.jobmailer.job_mailer.model.Company;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CompanyCsvReader}.
 *
 * <p>Each test writes a small CSV file into a temporary directory, so the tests never
 * depend on the real {@code data/} folder or on any network access.
 */
class CompanyCsvReaderTest {

    @TempDir
    private Path tempDir;

    private final CompanyCsvReader reader = new CompanyCsvReader();

    /**
     * Verifies that values are trimmed, emails are lower-cased, a comma inside a quoted
     * company name is preserved, empty cells and "Not Available" become {@code null}.
     */
    @Test
    void readsAndNormalisesAllColumns() throws IOException {
        Path csv = writeCsv("""
                company,email,hrName,city,role,note
                  Example Tech Pvt Ltd  , HR@Example.com ,Priya,Pune,Java Developer,
                "Demo Solutions, Pune",careers@example.net,Not Available,Pune,,
                """);

        List<Company> companies = reader.read(csv);

        assertThat(companies).hasSize(2);

        Company first = companies.get(0);
        assertThat(first.name()).isEqualTo("Example Tech Pvt Ltd");
        assertThat(first.email()).isEqualTo("hr@example.com");
        assertThat(first.hrName()).isEqualTo("Priya");
        assertThat(first.city()).isEqualTo("Pune");
        assertThat(first.role()).isEqualTo("Java Developer");

        Company second = companies.get(1);
        assertThat(second.name()).isEqualTo("Demo Solutions, Pune");
        assertThat(second.hrName()).isNull();
        assertThat(second.role()).isNull();
    }

    /**
     * Verifies that rows with a missing name, an invalid email, or an email that was
     * already seen (compared case-insensitively) are skipped without stopping the run.
     */
    @Test
    void skipsRowsWithMissingNameInvalidEmailOrDuplicateEmail() throws IOException {
        Path csv = writeCsv("""
                company,email
                Good Co,good@example.com
                ,noname@example.com
                Bad Email Co,not-an-email
                Dup Co,GOOD@example.com
                Another Co,another@example.com
                """);

        List<Company> companies = reader.read(csv);

        assertThat(companies)
                .extracting(Company::name)
                .containsExactly("Good Co", "Another Co");
    }

    /**
     * Verifies that columns are found by name, not position, and that the optional
     * columns may be absent from the header.
     */
    @Test
    void worksWithColumnsInAnyOrderAndOptionalColumnsMissing() throws IOException {
        Path csv = writeCsv("""
                email,company
                a@example.com,Alpha
                """);

        List<Company> companies = reader.read(csv);

        assertThat(companies).hasSize(1);
        Company company = companies.get(0);
        assertThat(company.name()).isEqualTo("Alpha");
        assertThat(company.email()).isEqualTo("a@example.com");
        assertThat(company.hrName()).isNull();
        assertThat(company.city()).isNull();
        assertThat(company.role()).isNull();
    }

    /**
     * Verifies that empty lines and lines containing only whitespace are ignored and
     * are not reported as invalid rows.
     */
    @Test
    void ignoresBlankLines() throws IOException {
        Path csv = writeCsv("company,email\n\nAlpha,a@example.com\n   \nBeta,b@example.com\n");

        List<Company> companies = reader.read(csv);

        assertThat(companies).extracting(Company::name).containsExactly("Alpha", "Beta");
    }

    /**
     * Verifies that a byte-order mark at the start of the file, as added by some
     * spreadsheet programs, does not break detection of the first column.
     */
    @Test
    void handlesByteOrderMarkInHeader() throws IOException {
        Path csv = writeCsv("\uFEFFcompany,email\nAlpha,a@example.com\n");

        List<Company> companies = reader.read(csv);

        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).name()).isEqualTo("Alpha");
    }

    /** Verifies that a missing file fails fast with a clear message. */
    @Test
    void throwsWhenFileDoesNotExist() {
        Path missing = tempDir.resolve("missing.csv");

        assertThatThrownBy(() -> reader.read(missing))
                .isInstanceOf(CompanyDataException.class)
                .hasMessageContaining("not found");
    }

    /** Verifies that a completely empty file is rejected. */
    @Test
    void throwsWhenFileIsEmpty() throws IOException {
        Path csv = writeCsv("");

        assertThatThrownBy(() -> reader.read(csv))
                .isInstanceOf(CompanyDataException.class)
                .hasMessageContaining("empty");
    }

    /** Verifies that a header without the mandatory {@code email} column is rejected. */
    @Test
    void throwsWhenRequiredColumnIsMissing() throws IOException {
        Path csv = writeCsv("""
                company,city
                Alpha,Pune
                """);

        assertThatThrownBy(() -> reader.read(csv))
                .isInstanceOf(CompanyDataException.class)
                .hasMessageContaining("email");
    }

    /**
     * Writes the given text to a new CSV file inside the temporary directory.
     *
     * @param content the exact file content
     * @return the path of the created file
     * @throws IOException if the file cannot be written
     */
    private Path writeCsv(String content) throws IOException {
        return Files.writeString(tempDir.resolve("companies.csv"), content);
    }
}

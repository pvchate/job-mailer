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
 * Unit tests for {@link CompanyCsvEnricher}.
 *
 * <p>Each test writes a small CSV file into a temporary directory, runs the enricher,
 * and checks the result by reading the file back with {@link CompanyCsvReader}.
 */
class CompanyCsvEnricherTest {

    @TempDir
    private Path tempDir;

    private final CompanyCsvEnricher enricher = new CompanyCsvEnricher(new HrNameResolver());
    private final CompanyCsvReader reader = new CompanyCsvReader();

    /**
     * Verifies that empty cells and "Not Available" cells are filled, with a first name
     * where the address has one and with the fallback otherwise.
     */
    @Test
    void fillsEmptyAndNotAvailableCellsFromEmail() throws IOException {
        Path csv = writeCsv("""
                company,email,hrName,city
                Alpha,priya.sharma@alpha.com,Not Available,Pune
                Beta,careers@beta.com,,Pune
                """);

        int updated = enricher.fillHrNames(csv);

        assertThat(updated).isEqualTo(2);
        List<Company> companies = reader.read(csv);
        assertThat(companies).extracting(Company::hrName)
                .containsExactly("Priya", HrNameResolver.FALLBACK_NAME);
    }

    /** Verifies that a name typed in by hand is never overwritten. */
    @Test
    void keepsManuallyEnteredNamesAndDoesNotRewriteFile() throws IOException {
        Path csv = writeCsv("""
                company,email,hrName
                Alpha,careers@alpha.com,Neha
                """);

        int updated = enricher.fillHrNames(csv);

        assertThat(updated).isZero();
        assertThat(reader.read(csv).get(0).hrName()).isEqualTo("Neha");
        assertThat(backupOf(csv)).doesNotExist();
    }

    /** Verifies that a missing {@code hrName} column is added and filled. */
    @Test
    void addsHrNameColumnWhenMissing() throws IOException {
        Path csv = writeCsv("""
                company,email
                Alpha,priya.sharma@alpha.com
                """);

        int updated = enricher.fillHrNames(csv);

        assertThat(updated).isEqualTo(1);
        assertThat(reader.read(csv).get(0).hrName()).isEqualTo("Priya");
    }

    /** Verifies that the previous version of the file is saved as a {@code .bak} copy. */
    @Test
    void savesBackupOfOriginalFile() throws IOException {
        String original = """
                company,email,hrName
                Alpha,careers@alpha.com,
                """;
        Path csv = writeCsv(original);

        enricher.fillHrNames(csv);

        assertThat(backupOf(csv)).exists();
        assertThat(Files.readString(backupOf(csv))).isEqualTo(original);
    }

    /** Verifies that running the enricher a second time changes nothing. */
    @Test
    void secondRunUpdatesNothing() throws IOException {
        Path csv = writeCsv("""
                company,email,hrName
                Alpha,careers@alpha.com,
                """);

        enricher.fillHrNames(csv);
        int secondRun = enricher.fillHrNames(csv);

        assertThat(secondRun).isZero();
    }

    /** Verifies that a comma inside a quoted company name survives the rewrite. */
    @Test
    void preservesQuotedCommaInCompanyName() throws IOException {
        Path csv = writeCsv("""
                company,email,hrName
                "Demo Solutions, Pune",careers@demo.com,
                """);

        enricher.fillHrNames(csv);

        assertThat(reader.read(csv).get(0).name()).isEqualTo("Demo Solutions, Pune");
    }

    /** Verifies that a missing file fails with a clear message. */
    @Test
    void throwsWhenFileDoesNotExist() {
        Path missing = tempDir.resolve("missing.csv");

        assertThatThrownBy(() -> enricher.fillHrNames(missing))
                .isInstanceOf(CompanyDataException.class)
                .hasMessageContaining("not found");
    }

    /** Verifies that an empty file is rejected. */
    @Test
    void throwsWhenFileIsEmpty() throws IOException {
        Path csv = writeCsv("");

        assertThatThrownBy(() -> enricher.fillHrNames(csv))
                .isInstanceOf(CompanyDataException.class)
                .hasMessageContaining("empty");
    }

    /** Verifies that a header without an {@code email} column is rejected. */
    @Test
    void throwsWhenEmailColumnIsMissing() throws IOException {
        Path csv = writeCsv("""
                company,city
                Alpha,Pune
                """);

        assertThatThrownBy(() -> enricher.fillHrNames(csv))
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

    /**
     * Returns the path where the enricher saves the backup of the given file.
     *
     * @param csv the CSV file that was enriched
     * @return the path of the matching {@code .bak} file
     */
    private Path backupOf(Path csv) {
        return csv.resolveSibling(csv.getFileName() + ".bak");
    }
}

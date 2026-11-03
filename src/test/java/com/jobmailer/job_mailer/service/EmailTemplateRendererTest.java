package com.jobmailer.job_mailer.service;


import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.exception.EmailTemplateException;
import com.jobmailer.job_mailer.model.Company;
import com.jobmailer.job_mailer.model.EmailMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EmailTemplateRenderer}.
 *
 * <p>Most tests give the renderer a small template as plain text, so each behavior
 * is checked in isolation. One test loads the real template file from the classpath
 * to make sure it exists and is correctly formatted.
 */
class EmailTemplateRendererTest {

    private final MailerProperties props = new MailerProperties(
            "Test User", "test@example.com", "9999999999",
            "https://test/linkedin.com",
            Path.of("resume.pdf"), Path.of("companies.csv"), Path.of("sent-log.csv"),
            25, 45, true);

    /** A small template that uses every blank the renderer knows. */
    private static final String SIMPLE_TEMPLATE = """
            Subject: Application for {{company}}

            Dear {{hrName}},

            I am applying at {{company}}.

            Regards,
            {{senderName}}
            {{senderEmail}}
            {{senderContact}}
            {{senderLinkedIn}}
            """;

    /** Verifies that every blank in the subject and the body is replaced by its value. */
    @Test
    void fillsBlanksInSubjectAndBody() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(props, SIMPLE_TEMPLATE);

        EmailMessage message = renderer.render(company("Alpha Tech", "priya.sharma@alpha.com", "Priya"));

        assertThat(message.subject()).isEqualTo("Application for Alpha Tech");
        assertThat(message.body()).isEqualTo("""
                Dear Priya,

                I am applying at Alpha Tech.

                Regards,
                Test User
                test@example.com
                9999999999
                https://test/linkedin.com
                """);
    }

    /** Verifies that the email is addressed to the company's email address. */
    @Test
    void addressesMessageToCompanyEmail() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(props, SIMPLE_TEMPLATE);

        EmailMessage message = renderer.render(company("Alpha Tech", "hr@alpha.com", "Priya"));

        assertThat(message.to()).isEqualTo("hr@alpha.com");
    }

    /** Verifies that a company without an hrName is greeted with the neutral fallback. */
    @Test
    void usesFallbackNameWhenHrNameIsMissing() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(props, SIMPLE_TEMPLATE);

        EmailMessage message = renderer.render(company("Beta Systems", "careers@beta.com", null));

        assertThat(message.body()).startsWith("Dear Sir/Ma'am,");
    }

    /**
     * Verifies that characters with a special meaning in text replacement, such as
     * {@code $} and a backslash, are inserted exactly as they are.
     */
    @Test
    void insertsSpecialCharactersLiterally() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(props, """
                Subject: At {{company}}

                Hello {{company}}.
                """);

        EmailMessage message = renderer.render(company("R$D \\ Labs", "hr@rd.com", "Sir/Ma'am"));

        assertThat(message.subject()).isEqualTo("At R$D \\ Labs");
        assertThat(message.body()).isEqualTo("Hello R$D \\ Labs.\n");
    }

    /** Verifies that a template saved with Windows line endings works the same way. */
    @Test
    void handlesWindowsLineEndings() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(props,
                "Subject: Hi {{company}}\r\n\r\nDear {{hrName}},\r\n");

        EmailMessage message = renderer.render(company("Alpha", "hr@alpha.com", "Priya"));

        assertThat(message.subject()).isEqualTo("Hi Alpha");
        assertThat(message.body()).isEqualTo("Dear Priya,\n");
    }

    /** Verifies that a misspelled blank stops the render and names the bad blank. */
    @Test
    void rejectsUnknownBlank() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(props, """
                Subject: Hi

                Hello at {{compnay}}.
                """);

        assertThatThrownBy(() -> renderer.render(company("Alpha", "hr@alpha.com", "Priya")))
                .isInstanceOf(EmailTemplateException.class)
                .hasMessageContaining("compnay");
    }

    /**
     * Verifies that unfinished {@code [square bracket]} text stops the render, so a
     * template that still has its example placeholders can never be sent.
     */
    @Test
    void refusesUnfinishedSquareBracketText() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(props, """
                Subject: Hi

                - [Highlight 1 from your resume]
                """);

        assertThatThrownBy(() -> renderer.render(company("Alpha", "hr@alpha.com", "Priya")))
                .isInstanceOf(EmailTemplateException.class)
                .hasMessageContaining("unfinished text")
                .hasMessageContaining("[Highlight 1 from your resume]");
    }

    /** Verifies that a template whose first line is not a subject line is rejected. */
    @Test
    void rejectsTemplateWithoutSubjectLine() {
        assertThatThrownBy(() -> new EmailTemplateRenderer(props, "Dear {{hrName}},\n\nBody\n"))
                .isInstanceOf(EmailTemplateException.class)
                .hasMessageContaining("Subject:");
    }

    /** Verifies that a template without a blank line after the subject is rejected. */
    @Test
    void rejectsTemplateWithoutBlankLineAfterSubject() {
        assertThatThrownBy(() -> new EmailTemplateRenderer(props, "Subject: Hi\nDear {{hrName}},\n"))
                .isInstanceOf(EmailTemplateException.class);
    }

    /** Verifies that an empty subject is rejected. */
    @Test
    void rejectsEmptySubject() {
        assertThatThrownBy(() -> new EmailTemplateRenderer(props, "Subject:   \n\nBody\n"))
                .isInstanceOf(EmailTemplateException.class)
                .hasMessageContaining("subject");
    }

    /**
     * Verifies that the real template file in {@code src/main/resources/templates}
     * exists and has the correct format. It does not render the template, because the
     * example brackets may still be in it.
     */
    @Test
    void realTemplateFileLoads() {
        assertThatCode(() -> new EmailTemplateRenderer(props)).doesNotThrowAnyException();
    }

    /**
     * Builds a company for a test, leaving the optional columns empty.
     *
     * @param name   the company name
     * @param email  the company email address
     * @param hrName the greeting name, or {@code null} for none
     * @return the company
     */
    private Company company(String name, String email, String hrName) {
        return new Company(name, email, hrName, null, null);
    }
}

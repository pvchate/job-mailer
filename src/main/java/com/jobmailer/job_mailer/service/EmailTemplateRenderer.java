package com.jobmailer.job_mailer.service;

import com.jobmailer.job_mailer.config.MailerProperties;
import com.jobmailer.job_mailer.exception.EmailTemplateException;
import com.jobmailer.job_mailer.model.Company;
import com.jobmailer.job_mailer.model.EmailMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the email template into a ready-to-send {@link EmailMessage} for one company.
 *
 * <p>The template is a text file whose first line is {@code Subject: ...}, followed by a
 * blank line and the body. Blanks written as {@code {{name}}} are replaced with values:
 * {@code company}, {@code hrName}, {@code senderName} and {@code senderEmail}.
 *
 * <p>The renderer refuses to produce an email if the template uses an unknown blank or
 * still contains unfinished {@code [square bracket]} text, so a mistake in the template
 * is caught before anything is sent.
 */
@Component
public class EmailTemplateRenderer {

    /** Location of the template file on the classpath. */
    private static final String TEMPLATE_PATH = "templates/application-email.txt";

    /** The text that must start the first line of the template. */
    private static final String SUBJECT_PREFIX = "Subject:";

    /** Matches a blank such as {@code {{company}}} and captures its name. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    /** Matches unfinished text such as {@code [Highlight 1 from your resume]}. */
    private static final Pattern UNFINISHED_TEXT = Pattern.compile("\\[[^\\]]*]");

    private final MailerProperties props;
    private final String subjectTemplate;
    private final String bodyTemplate;

    /**
     * Creates the renderer and loads the template file from the classpath.
     *
     * @param props the application settings, which supply the sender name and email
     * @throws EmailTemplateException if the template file is missing or badly formatted
     */
    @Autowired
    public EmailTemplateRenderer(MailerProperties props) {
        this(props, loadTemplate());
    }

    /**
     * Creates the renderer from template text. Used by tests to supply a template
     * without a file.
     *
     * @param props        the application settings, which supply the sender name and email
     * @param templateText the full template text, subject line first
     * @throws EmailTemplateException if the template is badly formatted
     */
    EmailTemplateRenderer(MailerProperties props, String templateText) {
        this.props = props;
        String text = templateText.replace("\r\n", "\n");
        int blankLine = text.indexOf("\n\n");
        if (!text.startsWith(SUBJECT_PREFIX) || blankLine < 0) {
            throw new EmailTemplateException("The template must start with a line '"
                    + SUBJECT_PREFIX + " ...', followed by a blank line and the body");
        }
        String subject = text.substring(SUBJECT_PREFIX.length(), blankLine).trim();
        if (subject.isEmpty() || subject.contains("\n")) {
            throw new EmailTemplateException("The subject must be a single non-empty line");
        }
        this.subjectTemplate = subject;
        this.bodyTemplate = text.substring(blankLine + 2).strip() + "\n";
    }

    /**
     * Builds the email for one company by filling in the blanks of the template.
     *
     * <p>If the company has no {@code hrName}, the neutral name
     * {@value HrNameResolver#FALLBACK_NAME} is used.
     *
     * @param company the company to write to; its name, email and hrName are used
     * @return the email with its recipient, subject and body filled in
     * @throws EmailTemplateException if the template contains unfinished
     *                                {@code [square bracket]} text or an unknown blank
     */
    public EmailMessage render(Company company) {
        rejectUnfinishedText(subjectTemplate);
        rejectUnfinishedText(bodyTemplate);

        Map<String, String> values = Map.of(
                "company", company.name(),
                "hrName", company.hrName() != null ? company.hrName() : HrNameResolver.FALLBACK_NAME,
                "senderName", props.senderName(),
                "senderEmail", props.senderEmail(),
                "senderContact", props.senderContact(),
                "senderLinkedIn", props.senderLinkedIn());

        return new EmailMessage(
                company.email(),
                fill(subjectTemplate, values),
                fill(bodyTemplate, values));
    }

    /**
     * Replaces every {@code {{name}}} blank in the text with its value.
     *
     * @param text   the text containing blanks
     * @param values the value for each known blank name
     * @return the text with all blanks filled in
     * @throws EmailTemplateException if the text uses a blank name that has no value
     */
    private String fill(String text, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.get(name);
            if (value == null) {
                throw new EmailTemplateException("Unknown blank {{" + name
                        + "}} in the template. Known blanks: " + values.keySet());
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Fails if the text still contains unfinished {@code [square bracket]} text.
     *
     * @param text the template part to check
     * @throws EmailTemplateException if any square-bracket text is found
     */
    private void rejectUnfinishedText(String text) {
        Matcher matcher = UNFINISHED_TEXT.matcher(text);
        if (matcher.find()) {
            throw new EmailTemplateException("The template still contains unfinished text "
                    + matcher.group() + ". Replace it with your real details before sending.");
        }
    }

    /**
     * Reads the template file from the classpath.
     *
     * @return the full template text
     * @throws EmailTemplateException if the file is missing or cannot be read
     */
    private static String loadTemplate() {
        try {
            return new ClassPathResource(TEMPLATE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EmailTemplateException("Could not read the email template: " + TEMPLATE_PATH, e);
        }
    }
}

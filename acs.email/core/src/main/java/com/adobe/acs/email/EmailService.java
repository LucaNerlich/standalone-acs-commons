package com.adobe.acs.email;

import java.util.List;
import java.util.Map;

/**
 * Sends an email built from a JCR-based mail template ({@code Subject: ...} header + {@code ${placeholder}} body,
 * parsed via {@link com.day.cq.commons.mail.MailTemplate}) to one or more recipients.
 */
public interface EmailService {

    /**
     * @param templatePath absolute path of the template used to build the email
     * @param emailParams  replacement variables merged into the template; the reserved keys
     *                     {@link EmailConstants#SENDER_EMAIL_ADDRESS}, {@link EmailConstants#SENDER_NAME},
     *                     {@link EmailConstants#SUBJECT} and {@link EmailConstants#BOUNCE_ADDRESS} override the
     *                     corresponding email header instead of being substituted into the body
     * @param recipients   recipient email addresses; invalid addresses are skipped
     * @return the recipient addresses for which sending failed
     */
    default List<String> sendEmail(String templatePath, Map<String, String> emailParams, String... recipients) {
        return sendEmail(templatePath, emailParams, List.of(), recipients);
    }

    /**
     * Same as {@link #sendEmail(String, Map, String...)}, with attachments. Supplying any attachments forces
     * the email to be built as an HTML multipart message, regardless of the template's file extension, since
     * a plain-text email can't carry attachments.
     *
     * @param attachments attachments to add to every recipient's email; use {@link List#of()} for none
     */
    List<String> sendEmail(String templatePath, Map<String, String> emailParams, List<MailAttachment> attachments, String... recipients);
}

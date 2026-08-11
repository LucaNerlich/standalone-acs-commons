package com.adobe.acs.email;

import com.day.cq.commons.mail.MailTemplate;
import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.*;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Session;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component(service = EmailService.class, immediate = true)
public class EmailServiceImpl implements EmailService {

    // Consumers must map "acsemail.core:emailService=[<system-user>]" and grant that user jcr:read on their template paths.
    private static final String SUBSERVICE_NAME = "emailService";

    @Reference
    private MessageGatewayService messageGatewayService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    /**
     * Sends an email built from a JCR-based mail template to one or more recipients, with optional attachments.
     * The email template can include placeholders that are substituted with the values
     * provided in the email parameters. Reserved parameters in the {@code emailParams}
     * map allow overriding specific email headers such as sender address, subject, and
     * bounce address.
     *
     * @param templatePath the absolute path to the mail template in the JCR repository
     * @param emailParams  a map of placeholders and their corresponding values to be
     *                     substituted into the mail template; reserved keys such as
     *                     {@link EmailConstants#SENDER_EMAIL_ADDRESS}, {@link EmailConstants#SENDER_NAME},
     *                     {@link EmailConstants#SUBJECT}, and {@link EmailConstants#BOUNCE_ADDRESS}
     *                     override the corresponding email headers
     * @param attachments  attachments to add to every recipient's email; a non-empty list forces the email
     *                     to be built as {@link HtmlEmail}, regardless of the template's file extension
     * @param recipients   one or more recipient email addresses; invalid addresses are skipped during processing
     * @return a list of recipient email addresses for which the email sending operation failed
     * @throws IllegalArgumentException if the recipients array is null or empty
     */
    @Override
    public List<String> sendEmail(final String templatePath, final Map<String, String> emailParams,
                                  final List<MailAttachment> attachments, final String... recipients) {
        if (recipients == null || recipients.length == 0) {
            throw new IllegalArgumentException("Invalid Recipients");
        }

        final Optional<MailTemplate> mailTemplate = getMailTemplate(templatePath);
        final Class<? extends Email> mailType = attachments.isEmpty() ? getMailType(templatePath) : HtmlEmail.class;
        final MessageGateway<Email> messageGateway = mailTemplate.isPresent() ? messageGatewayService.getGateway(mailType) : null;

        // Bail early if we fail to resolve a mailtemplate or if there is no MessageGateway that can actually send this mail type
        if (messageGateway == null || !messageGateway.handles(mailType)) {
            if (mailTemplate.isPresent()) {
                log.error("No MessageGateway available for mail type [ {} ]", mailType);
            }
            return Arrays.stream(recipients)
                    .filter(recipient -> parseAddress(recipient).isPresent())
                    .toList();
        }

        final List<String> failureList = new ArrayList<>();
        for (final String recipient : recipients) {
            final Optional<InternetAddress> address = parseAddress(recipient);
            if (address.isEmpty()) {
                continue;
            }

            try {
                final Email email = mailTemplate.get().getEmail(emailParams, mailType);
                applyReservedParams(email, emailParams);
                attach(email, attachments);
                email.setTo(Collections.singleton(address.get()));
                messageGateway.send(email);
            } catch (RuntimeException | EmailException | MessagingException | IOException e) {
                failureList.add(recipient);
                log.error("Error sending email to [ {} ]", recipient, e);
            }
        }

        return Collections.unmodifiableList(failureList);
    }

    /**
     * @param email       the email to attach to; must be a {@link MultiPartEmail} (i.e. {@link HtmlEmail}) if
     *                    {@code attachments} is non-empty, since a plain-text email can't carry attachments
     * @param attachments attachments to add; a no-op if empty
     * @throws EmailException if an attachment could not be added
     */
    private void attach(final Email email, final List<MailAttachment> attachments) throws EmailException {
        if (attachments.isEmpty()) {
            return;
        }
        final MultiPartEmail multiPartEmail = (MultiPartEmail) email;
        for (final MailAttachment attachment : attachments) {
            multiPartEmail.attach(attachment.dataSource(), attachment.name(), null);
        }
    }

    /**
     * Applies reserved parameters from the email parameters map to the provided email object.
     * The reserved parameters are keys that override specific email headers instead of being
     * substituted into the email template body.
     *
     * @param email       the email object to which the reserved parameters will be applied
     * @param emailParams a map of email parameters where reserved keys such as
     *                    {@link EmailConstants#SENDER_EMAIL_ADDRESS}, {@link EmailConstants#SENDER_NAME},
     *                    {@link EmailConstants#SUBJECT}, and {@link EmailConstants#BOUNCE_ADDRESS}
     *                    control corresponding email headers
     * @throws EmailException if an error occurs while setting the email parameters
     */
    private void applyReservedParams(final Email email, final Map<String, String> emailParams) throws EmailException {
        if (emailParams.containsKey(EmailConstants.SENDER_EMAIL_ADDRESS) && emailParams.containsKey(EmailConstants.SENDER_NAME)) {
            email.setFrom(emailParams.get(EmailConstants.SENDER_EMAIL_ADDRESS), emailParams.get(EmailConstants.SENDER_NAME));
        } else if (emailParams.containsKey(EmailConstants.SENDER_EMAIL_ADDRESS)) {
            email.setFrom(emailParams.get(EmailConstants.SENDER_EMAIL_ADDRESS));
        }

        if (emailParams.containsKey(EmailConstants.SUBJECT)) {
            email.setSubject(emailParams.get(EmailConstants.SUBJECT));
        }

        if (emailParams.containsKey(EmailConstants.BOUNCE_ADDRESS)) {
            email.setBounceAddress(emailParams.get(EmailConstants.BOUNCE_ADDRESS));
        }
    }

    private Class<? extends Email> getMailType(final String templatePath) {
        return templatePath.endsWith(".html") ? HtmlEmail.class : SimpleEmail.class;
    }

    private Optional<InternetAddress> parseAddress(final String recipient) {
        try {
            return Optional.of(new InternetAddress(recipient));
        } catch (AddressException e) {
            log.warn("Invalid email address {} passed to sendEmail(). Skipping.", recipient);
            return Optional.empty();
        }
    }

    /**
     * Retrieves a MailTemplate object for the specified template path.
     *
     * @param templatePath the absolute path to the mail template in the JCR repository
     * @return the MailTemplate if it was successfully resolved, or an empty Optional if the template path
     * could not be resolved or the service resource resolver could not be obtained
     */
    private Optional<MailTemplate> getMailTemplate(final String templatePath) {
        final Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SUBSERVICE_NAME);
        try (ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final MailTemplate mailTemplate = MailTemplate.create(templatePath, resourceResolver.adaptTo(Session.class));
            if (mailTemplate == null) {
                log.error("Mail template path [ {} ] could not resolve to a valid template", templatePath);
            }
            return Optional.ofNullable(mailTemplate);
        } catch (LoginException e) {
            log.error("Unable to obtain a service resource resolver to get the Mail Template at [ {} ]", templatePath, e);
            return Optional.empty();
        }
    }
}

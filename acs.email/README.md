# acs.email

In-house replacement for ACS AEM Commons' `EmailService`. A slim, template-based email sender built directly on
AEM's native `com.day.cq.mailer.MessageGatewayService` and `com.day.cq.commons.mail.MailTemplate` APIs.

## Modules

- `core` - `com.adobe.acs.email.EmailService` / `EmailServiceImpl`: sends an email built from a JCR-based mail
  template (`Subject: ...` header + `${placeholder}` body) to one or more recipients, with optional attachments.

## Building & deploying

```bash
mvn clean install                       # build
mvn clean install -PautoInstallBundle   # build + deploy the bundle (default: localhost:4502)
```

Override `aem.host`/`aem.port` via `-D` if your instance differs from the defaults.

## Usage

```java
@Reference
private EmailService emailService;

Map<String, String> params = new HashMap<>();
params.put(EmailConstants.SUBJECT, "Your order has shipped");
params.put("orderNumber", "12345"); // substituted into the template body as ${orderNumber}

List<String> failures = emailService.sendEmail(
        "/apps/myapp/emailTemplates/orderShipped.html", params, "customer@example.com");
```

`templatePath` is an absolute JCR path resolved via a service resource resolver - see below. Reserved keys in
`emailParams` (`EmailConstants.SENDER_EMAIL_ADDRESS`, `SENDER_NAME`, `SUBJECT`, `BOUNCE_ADDRESS`) override the
corresponding email header instead of being substituted into the template body; everything else is merged into
the template as a `${key}` placeholder.

Attachments (optional, forces an HTML multipart email regardless of the template's file extension, since a
plain-text email can't carry attachments):

```java
MailAttachment attachment = new MailAttachment("invoice.pdf", myDataSource);
emailService.sendEmail(templatePath, params, List.of(attachment), "customer@example.com");
```

## Required setup: service user mapping

`EmailServiceImpl` resolves the mail template via a service resource resolver, not the calling user's session, so
consumers must map a system user for it:

```
acsemail.core:emailService=[<your-system-user>]
```

(via the Apache Sling Service User Mapper OSGi config), and grant that system user `jcr:read` on whichever template
paths you pass to `sendEmail`. Without this mapping, template resolution fails and every recipient is returned as
a failure.

# acs.email

In-house replacement for ACS AEM Commons' `EmailService`. A slim, template-based email sender built directly on
AEM's native `com.day.cq.mailer.MessageGatewayService` and `com.day.cq.commons.mail.MailTemplate` APIs. See the
[original ACS AEM Commons Email API docs](https://adobe-consulting-services.github.io/acs-aem-commons/features/e-mail/email-api/index.html)
for background on the concept this replaces.

## How it works

A "mail template" is just a JCR file (e.g. under `/apps/.../emailTemplates/`) whose first line is a
`Subject: ...` header, followed by a blank line and the plain-text or HTML body. The body may contain
`${placeholder}` tokens.

Calling `sendEmail(templatePath, emailParams, recipients...)`:

1. Resolves `templatePath` via a service resource resolver and parses it with `MailTemplate`.
2. Merges `emailParams` into the template, substituting `${key}` tokens in the body.
3. Applies any reserved keys (see [`EmailConstants`](core/src/main/java/com/adobe/acs/email/EmailConstants.java))
   as headers instead of body substitutions.
4. Sends one email per recipient through `MessageGatewayService` - which in turn uses the SMTP settings
   configured on AEM's `com.day.cq.mailer.DefaultMailService` OSGi config, same as any other AEM mail sender.

Whether the template is sent as plain text or HTML is inferred from its file extension (`.html` -> HTML,
anything else -> plain text), unless attachments are supplied, which forces HTML.

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

## Testing locally with Mailpit

`EmailServiceImpl` sends through whatever SMTP server AEM's `com.day.cq.mailer.DefaultMailService` OSGi config
points at - it doesn't do anything network-related itself. Locally, point that config at
[Mailpit](https://lucanerlich.com/aem/infrastructure/email/#alternative-mailpit) instead of a real SMTP server, so
emails sent via `sendEmail` land in a local web UI instead of going out (or failing to connect) for real:

```bash
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Then set, e.g. under `ui.config/.../osgiconfig/config.local-dev/com.day.cq.mailer.DefaultMailService.cfg.json`:

```json
{
  "smtp.host": "localhost",
  "smtp.port": "1025",
  "smtp.user": "",
  "smtp.password": "",
  "from.address": "noreply@example.com",
  "smtp.ssl": false,
  "smtp.starttls": false,
  "smtp.requiretls": false,
  "debug.email": false,
  "oauth.flow": false,
  "graph.flow": false
}
```

Emails are then visible at `http://localhost:8025` - no real mail is sent. If your AEM instance runs elsewhere
(e.g. a remote dev/author box), replace `localhost` with the host where Mailpit's SMTP port is reachable.

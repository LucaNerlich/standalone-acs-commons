# Changelog

All notable changes to this module are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.0] - 2026-08-11

### Added

- Initial standalone release: `EmailService` / `EmailServiceImpl`, a slim, template-based email sender built on
  AEM's native `MessageGatewayService` and `MailTemplate` APIs, with optional attachments (`MailAttachment`) and
  reserved header-override parameters (`EmailConstants`).

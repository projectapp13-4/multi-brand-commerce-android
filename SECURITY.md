# Security Policy

## Supported state

Security fixes are accepted against the current `main` branch. Historical
commits and archived evidence are not maintained release lines. The repository
does not currently represent a production-ready release; see the P3-16
boundary in `docs/phase3/P3-16-HANDOFF.md`.

## Reporting a vulnerability

Use GitHub private vulnerability reporting or a private security advisory for
this repository. Do not open a public issue for a suspected vulnerability and
do not include credentials, customer data, access tokens, signing material, or
working exploit details in public discussion.

Include the affected revision, component, impact, reproduction conditions,
and the smallest safe evidence needed to validate the report. Redact all
secrets and personal data.

## Scope boundaries

Do not test against production merchants, customers, orders, payments, OAuth
registrations, Firebase projects, or other live services without explicit
authorization. Do not contact or test third-party reference services. Use only
project-owned non-production environments and test data.

Public client configuration embedded in a mobile application is not a server
secret, but it must still be handled according to the repository's
configuration and redaction policy. Customer/OAuth tokens, Admin credentials,
backend secrets, private keys, signing material, and service-account
credentials must never enter source, logs, issues, or documentation.

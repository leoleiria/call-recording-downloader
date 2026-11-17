# Security policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

Please **do not** open a public issue. Report it privately through
[GitHub Security Advisories](https://github.com/leoleiria/call-recording-downloader/security/advisories/new).

Include the version, operating system, steps to reproduce and the impact. Never include a real API
key, phone number, recording or transcription. You will get an answer within 7 days, and we will
agree on a disclosure date once a fix is available.

## How the app protects your data

- **API key**: typed or pasted at every use and kept in memory only. It is never written to disk,
  shown masked by default and masked in every log line (an automated test checks the real logging
  configuration).
- **Network**: the key is sent only to the Ringover API over HTTPS. The API client does not follow
  redirects, and direct audio downloads from Ringover's CDN never include the key. Other hosts are
  refused.
- **Stored preferences**: only language, region and last folder used.
- **Downloaded files**: recordings and transcriptions are saved only to the folder you choose. You
  are responsible for storing them securely and lawfully.

## Recommendations for users

- Create a dedicated Ringover API key with **Calls → Read** only, and revoke it when no longer
  needed.
- Download the app only from this repository's
  [Releases](https://github.com/leoleiria/call-recording-downloader/releases).

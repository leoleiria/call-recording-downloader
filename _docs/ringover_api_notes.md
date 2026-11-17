# Ringover API notes

How the app uses the Ringover public API v2. The authoritative reference is the official
documentation at <https://developer.ringover.com/>. Re-check it when something stops working; this
page only summarizes the parts the app relies on. It is written in our own words and does not copy
the documentation, which is not openly licensed.

## Base URLs and authentication

| Region | Base URL |
|---|---|
| Europe | `https://public-api.ringover.com/v2` |
| United States | `https://public-api-us.ringover.com/v2` |

The API key goes in the `Authorization` header as is, with no `Bearer` prefix. The key needs the
**Calls → Read** permission. Call listing returns all team calls regardless of the key's
"Monitoring" setting.

## Limits

- **2 requests per second** per key; above that the API answers `429`.
- Call searches: the documentation states a 15-day maximum per request, but a request for 15 local
  days was **rejected** in practice. Converted to UTC, 15 local days can touch 16 calendar dates. The
  app therefore asks for **14 days** per request. Periods up to one year are split, which means at
  most 27 requests for a full year (26 × 14 = 364 days, plus one short request), plus one extra
  request per additional page of results. At 2 requests/s that takes about 15 seconds.
- Call pages hold up to 1000 entries; offset pagination stops at 9000, so the app uses the
  `last_id_returned` cursor instead.

## Endpoints used

| Purpose | Request | Notes |
|---|---|---|
| Find calls with a number | `POST /calls?expand=transcription` | Body: `filter: "ADVANCED"`, `start_date`, `end_date` (ISO-8601 UTC), `limit_count`, optional `last_id_returned`, `advanced.ext_numbers: [<E.164 digits without +>]`. `204` means no calls. |
| Download audio | `POST /cdn/download` with `{"url": "<CDN URL>"}` | Needed for private CDN links (`/v2/private/download/…`); streams the file back. |
| Transcription | `GET /transcriptions/{callId}` | Array of objects with `transcription_data.speeches[]` (`channelId` 1 = caller, 0 = callee, `start`, `text`). `404` = none. Requires the transcription feature in the team plan. |

## Fields read from each call log entry

`cdr_id`, `call_id`, `direction` (`in`/`out`), `start_time`, `total_duration`, `record` (recording
URL), `voicemail` (voicemail URL) and `transcription` (present when `expand=transcription` is
requested; `null` when the call has none).

Several entries can share a `call_id` when a call is transferred or routed through an IVR; the app
groups them into one call.

## Open questions to verify with a real account

- Whether `ext_numbers` matches both received and placed calls with the contact in every case.
- Whether some `record` / `voicemail` URLs are public links that can be downloaded without the
  proxy. The client supports both: Ringover HTTPS links without the private path are fetched
  directly, without the key.

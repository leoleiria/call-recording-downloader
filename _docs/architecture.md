# Architecture

## Modules

```
app (JavaFX UI) ──► core (domain + use cases + storage) ◄── ringover-client (HTTP adapter)
       └──────────────────────────────────────────────────────────►┘ (wired in CallRecordingApp)
```

| Module | Java module | Depends on | Responsibility |
|---|---|---|---|
| `modules/core` | `io.github.leoleiria.callrecording.core` | SLF4J API | Domain model, use cases, file layout, secret handling |
| `modules/ringover-client` | `io.github.leoleiria.callrecording.ringover` | core, `java.net.http`, Jackson | Implements the `CallGateway` port over the Ringover API |
| `modules/app` | `io.github.leoleiria.callrecording.app` | core, ringover-client, JavaFX, Logback | Screens, translations, preferences, logging |

All three are JPMS modules, which lets `jlink` build a small runtime containing only what is used.

`core` defines the `CallGateway` port and never imports HTTP or UI code. A different provider, or a
command-line front end, could reuse it unchanged.

## Main flows

### Search

`SearchCallsUseCase`:

1. Rejects ranges longer than one year, then splits the user's `DateRange` (local days) into UTC
   `TimeWindow`s of 14 days (`MAX_DAYS_PER_REQUEST`), safely inside the API limit. A full year
   takes 27 windows. See [ringover_api_notes.md](ringover_api_notes.md#limits) for why it is 14 and
   not 15.
2. Calls `CallGateway.findCallLogs` for each window. The Ringover client follows the
   `last_id_returned` cursor until a short page.
3. De-duplicates log entries by `cdr_id` and groups them by `call_id` into `Call`s: one call can
   have several legs (transfers, IVR).
4. Returns a `SearchResult` with counts of calls, audio files (recordings + voicemails) and
   transcriptions.

### Download

`DownloadFilesUseCase`:

1. Builds file names with `FileNaming` (local date-time, direction, call id).
2. Skips files that already exist; everything else is written with `AtomicFileWriter` (`.part`
   file, then an atomic rename).
3. Transcriptions: the original JSON goes to `OriginalTranscription_Json/` and a readable text file
   is produced by `TranscriptTextFormatter`.
4. A failure on one file is recorded and the run continues. An invalid key aborts. "Feature not in
   plan" disables transcriptions for the rest of the run.
5. `OriginalTranscription_Json/manifest.csv` is merged at the end, even after a cancellation.

## Ringover client

- **Rate limiting**: a shared `RateLimiter` lets one request through every 500 ms (Ringover allows
  2/s per key).
- **Retries**: `RetryPolicy` retries 429 and transient 5xx responses and network errors (4 attempts,
  exponential back-off, `Retry-After` honoured).
- **Audio**: private CDN URLs are fetched through `POST /cdn/download`. Other Ringover HTTPS links
  are downloaded directly, without the API key. Any other host is refused.
- **Lenient parsing**: JSON is read through `JsonNode`, so new or missing optional fields do not break
  the app.

See [ringover_api_notes.md](ringover_api_notes.md) for the endpoints used.

## Security of the API key

| Layer | Protection |
|---|---|
| UI | `SecretField`: masked by default, optional reveal, never pre-filled |
| Memory | `ApiKey` value object; `toString()` returns `****abcd`; `reveal()` is called only where the `Authorization` header is built |
| Storage | Never persisted. `UserSettings` stores only language, region and last folder |
| Network | Key sent only to the API base URI; the API client never follows redirects; direct CDN downloads carry no key |
| Logs | `RedactingMessageConverter` / `RedactingThrowableConverter` mask every registered key and any `Authorization` value |
| Tests | `LogRedactionTest` runs the real `logback.xml` and fails if a key reaches the log file |

## User interface

The screens are built in Java code (no FXML), which keeps the module simple and avoids reflective
access. Long operations run as `javafx.concurrent.Task`s on a single background thread, with
progress and cancellation via `CancellationToken`.

## Internationalization

`i18n/messages_<code>.properties`, one per official EU language, all UTF-8. Texts are formatted with
`MessageFormat`, so translations use the typographic apostrophe (’). `TranslationsTest` checks that
every bundle has the same keys and placeholders as the base bundle.

The translations were machine-assisted. Improvements from native speakers are welcome; see
[CONTRIBUTING.md](../CONTRIBUTING.md#translations).

# Instructions for AI coding agents

These rules apply to any automated assistant working in this repository.

## Project

Java 21 + JavaFX desktop app, Maven multi-module, JPMS modules. Read
[_docs/architecture.md](_docs/architecture.md) before changing code.

- `modules/core`: domain and use cases. No HTTP, JSON or JavaFX imports.
- `modules/ringover-client`: Ringover API adapter. The only place that calls `ApiKey.reveal()`.
- `modules/app`: JavaFX UI, translations, logging configuration.

## Commands

```bash
./mvnw verify           # build, tests, formatting check (must pass)
./mvnw spotless:apply   # format code
```

Requires JDK 21 on `JAVA_HOME`.

## Hard rules

1. Never store, print or log the API key; never log HTTP headers. Keep `LogRedactionTest` passing.
2. Never call the real Ringover API from tests; use `FakeRingoverServer` / `FakeGateway`.
3. Never commit real phone numbers, API keys, recordings or transcriptions. Use `+34666666666`.
4. Do not copy text from Ringover's documentation into the repository; summarize in your own words.
5. Every new UI text needs a key in **all 24** `messages_*.properties` files and the base file; use
   `’`, not `'`.
6. Respect the API limits: 2 requests/s (use the shared `RateLimiter`) and 14 days per call search (periods of up to one year are split; see `SearchCallsUseCase`).
7. Compiler warnings are errors; do not suppress them without a comment explaining why.
8. New runtime dependencies need a JPMS module name and an entry in `NOTICE`.

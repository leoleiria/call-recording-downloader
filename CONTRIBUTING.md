# Contributing

Thanks for helping! Bug reports, translations, documentation and code are all welcome.

## Before you start

- **Bugs and ideas**: open an [issue](https://github.com/leoleiria/call-recording-downloader/issues)
  using the templates. Search existing issues first.
- **Security problems**: do **not** open a public issue; follow [SECURITY.md](SECURITY.md).
- **Larger changes**: open an issue to discuss the approach before writing code.

Never paste API keys, phone numbers, recordings or transcriptions in issues, pull requests or test
data. Use made-up values such as `+34666666666`.

## Development

Setup, build and run instructions are in [_docs/dev_setup.md](_docs/dev_setup.md), and the design is
described in [_docs/architecture.md](_docs/architecture.md).

```bash
./mvnw verify            # must pass before you open a pull request
./mvnw spotless:apply    # fixes formatting
```

### Conventions

- **Formatting**: google-java-format, enforced by Spotless in CI.
- **Compiler warnings are errors** (`-Xlint:all -Werror`).
- **Layering**: `core` must not depend on HTTP, JSON or JavaFX. Put API details in `ringover-client`
  and UI details in `app`.
- **Secrets**: the API key only travels as `ApiKey`. Never call `reveal()` outside the HTTP client,
  never store the key and never log request headers.
- **Tests**: every behaviour change comes with a test. Tests must not call the real Ringover API;
  use `FakeRingoverServer` or `FakeGateway`.
- **Comments and Javadoc**: explain *why*, not *what*. Public types get a short Javadoc.

### Commits and branches

- Branch from `main`: `feat/…`, `fix/…`, `docs/…`, `chore/…`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):
  `feat: add Croatian translation`, `fix(client): retry on 502`.
- Keep pull requests focused; update `CHANGELOG.md` under *Unreleased*.

## Translations

Texts live in `modules/app/src/main/resources/i18n/messages_<code>.properties` (UTF-8).

- Keep every key of `messages.properties` and every placeholder (`{0}`, `{1}`…).
- Use the typographic apostrophe `’`, never `'` (texts go through `MessageFormat`).
- Keep "Call Recording Downloader" and "Ringover" untranslated.
- `./mvnw verify` runs `TranslationsTest`, which checks all of the above.

Native speakers reviewing a language are especially welcome; please mention which language you
reviewed in the pull request.

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). By participating you agree to
uphold it.

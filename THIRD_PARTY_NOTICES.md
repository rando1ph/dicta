# Third-party notices and assets

Copyright 2026 rando1ph applies to Dicta's original code. The root [LICENSE](LICENSE)
contains the complete [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).
It does not relicense third-party libraries, websites, photographs or trademarks.

## Android runtime dependencies

The 1.0.0 release resolves 70 runtime artifacts. Their published Maven POMs
identify Apache-2.0 licensing, except the small Guava ListenableFuture POM,
whose license is confirmed from the [Guava upstream license](https://github.com/google/guava/blob/v33.4.8/LICENSE).
The [versioned dependency inventory](docs/releases/v1.0.0/dependency-licenses.json)
records exact coordinates and license URLs. Build and test tools are not bundled
as app features.

| Component | Upstream | License |
| --- | --- | --- |
| AndroidX, Compose, Material 3, Room and related Android libraries | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) | Apache-2.0 |
| Kotlin standard library, coroutines, serialization and annotations | [JetBrains / Kotlin](https://github.com/JetBrains/kotlin) | Apache-2.0 |
| OkHttp and Okio | [Square / OkHttp](https://github.com/square/okhttp), [Okio](https://github.com/square/okio) | Apache-2.0 |
| Guava ListenableFuture | [Google / Guava](https://github.com/google/guava) | Apache-2.0 |
| JSpecify annotations | [JSpecify](https://github.com/jspecify/jspecify) | Apache-2.0 |

The APK bundles the complete Apache-2.0 license and the
[dependency notices](app/src/main/assets/THIRD_PARTY_NOTICES.txt).
Embedded upstream AAR/JAR license and notice entries were inspected. Their
Apache text is retained as a shared copy; the distinct OkHttp resource notice is
preserved verbatim.

### Public Suffix List data

OkHttp 4.12.0 includes unmodified `okhttp3/internal/publicsuffix/publicsuffixes.gz`.
Its upstream NOTICE states that this is compiled from the
[Public Suffix List](https://publicsuffix.org/list/public_suffix_list.dat) and is
subject to [MPL-2.0](https://www.mozilla.org/en-US/MPL/2.0/). The APK includes that
notice and a complete [MPL-2.0 license copy](app/src/main/assets/licenses/MPL-2.0.txt).
The exact upstream compiled resource is available in the
[OkHttp 4.12.0 JAR](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar);
its source project is [publicsuffix/list](https://github.com/publicsuffix/list).
Dicta makes no changes to this data.

## Fonts, icons and design references

The app uses Android's system sans/serif fonts; no third-party font files are
bundled. Navigation glyphs are drawn by Dicta's own Canvas code. The app icon is
the existing Dicta vector; the README SVG reproduces its paths and colours.
The archived Stitch designs and branding reference under `design/` are project
design materials; web fonts and remote icons referenced in exported HTML are
not copied into the production app.

The signing helper's generic checks and publication pattern were adapted from
the maintainer's Oraculum project. Provenance and the limited porting scope are
recorded in [docs/SIGNING.md](docs/SIGNING.md); signing identities are separate.

## Screenshot context

The six PNGs in `docs/assets/readme/` are unchanged, reviewed OnePlus 6T captures
downloaded from the project's Google Drive. Their source IDs, dimensions and
SHA-256 digests are recorded in [the asset manifest](docs/assets/readme/manifest.json).

BBC and The Guardian names, logos, article text and photographs belong to their
respective owners. They appear as incidental browsing context in product
screenshots, not as independently supplied news or photo assets. No affiliation
or endorsement is implied, and Dicta's code license grants no rights to them.

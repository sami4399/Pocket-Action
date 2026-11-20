# Third-party notices — Pocket Action

**Status: preliminary inventory, not a complete generated SBOM or legal clearance.**

Pocket Action's own source and branding are intended to be proprietary. This
does not change the licences or terms that apply to third-party components.
Retain the upstream notices and comply with each component's applicable terms.

## Direct dependencies declared in `app/build.gradle.kts`

| Component | Declared version | Upstream / licence note |
|---|---:|---|
| Google ML Kit Text Recognition | 16.0.1 | Google ML Kit terms and applicable SDK notices; do not assume it is Apache-2.0 |
| Google ML Kit Barcode Scanning | 17.3.0 | Google ML Kit terms and applicable SDK notices; do not assume it is Apache-2.0 |
| AndroidX Camera Core | 1.4.2 | AndroidX / Apache License 2.0 |
| AndroidX Camera Camera2 | 1.4.2 | AndroidX / Apache License 2.0 |
| AndroidX Camera Lifecycle | 1.4.2 | AndroidX / Apache License 2.0 |
| AndroidX Camera View | 1.4.2 | AndroidX / Apache License 2.0 |
| AndroidX Activity KTX | 1.10.1 | AndroidX / Apache License 2.0 |
| AndroidX Core KTX | 1.15.0 | AndroidX / Apache License 2.0 |
| JUnit | 4.13.2 (test only) | JUnit 4 is distributed under EPL 1.0; review its accompanying licence text |

## Important scope limitations

This table lists only direct dependencies visible in the source build file.
It does **not** enumerate transitive dependencies, Gradle plugins, Android SDK
components, Google Play services/model-delivery components, or assets that may
be included by the final APK. The Gradle plugin versions are declared in the
root build file; their own distribution terms must also be included in a
release inventory where required.

Before publishing:
1. Resolve the complete release runtime and build dependency graphs.
2. Generate a machine-readable SBOM (for example CycloneDX) and a human-readable
   licence report from the exact release build.
3. Inspect every dependency's POM/module metadata and bundled NOTICE/LICENSE
   files; resolve any missing or conflicting licence metadata manually.
4. Include required licence texts and notices in the app's About / Open Source
   Licences screen or an accessible bundled notices page.
5. Re-run the report whenever dependencies or versions change.

## Upstream references

- AndroidX: https://developer.android.com/jetpack/androidx
- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- Google ML Kit: https://developers.google.com/ml-kit/terms
- JUnit 4: https://junit.org/junit4/

This file is an attribution starting point, not legal advice and not proof that
all redistribution obligations have been satisfied.

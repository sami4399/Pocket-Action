# Upload Pocket Action to GitHub

1. Sign in to GitHub and create a **Private** repository named `PocketAction`.
2. On the repository's Quick setup page, choose the option to upload an existing file / upload files.
3. Extract this ZIP on your computer.
4. Open the extracted `PocketAction` folder and upload its **contents** (including the hidden `.github` folder and `.gitignore`), not the outer ZIP file.
5. Commit the upload to the `main` branch.
6. Open the repository's **Actions** tab. The `Android CI` workflow should start automatically.
7. Wait for the workflow to finish. Download the `pocket-action-debug-apk` artifact only if the workflow succeeds.

Do not upload `local.properties`, signing keys, keystores, passwords, API keys, or other secrets.

## Build verification status

The included workflow is prepared but has not been executed in this environment. A successful GitHub Actions run is required to verify Kotlin compilation, JUnit execution, Android Lint, and debug APK generation. Physical-device testing remains a separate step.

## Gradle wrapper note

The supplied source archive does not contain `gradlew`, `gradlew.bat`, or `gradle/wrapper/gradle-wrapper.jar`. No incomplete or fabricated wrapper was added. CI therefore installs the pinned Gradle 8.13 runtime directly. Add a genuine Gradle wrapper in a later verified build-tooling step if desired.

# Pocket Action — Android project

Pocket Action is a share-to-action Android utility. Users explicitly share text or images from another app, select images, paste clipboard text, or start a camera scan. The app extracts readable text and offers user-confirmed actions.

## Current home-screen design

- White-first interface with restrained pale-blue surfaces, fine blue-grey borders and soft shadows.
- Centered Pocket Action brand title with a small, reduced-opacity `v1.0.0` label directly beneath the title. The previous “Licensed App” badge is not shown.
- Hamburger navigation on the left and capture-history search on the right.
- Pale-blue hero banner with a prominent Scan button.
- Scan button opens a centered modal with QR Scan and Camera Scan choices.
- Full-width Input container with Share to App, Choose Images and Paste Text cards.
- Full-width Quick Actions container. Empty state shows six white individual icon cards with preserved individual icon colors: green Call, blue Save Contact, purple Send Email, red Maps, orange Calendar and teal Open Link.
- Workspace uses a distinctive stacked-layers icon inside its own white icon container. Review & Process opens an editor with Analyse, Save and Copy actions.
- Recent Captures uses a white list container with item loading and a View All/search flow.

## Input and sharing

- Android Sharesheet `ACTION_SEND` for `text/plain` and `image/*`.
- `ACTION_SEND_MULTIPLE` for multiple images.
- `ClipData` URI fallback for apps that supply shared URIs through ClipData.
- `ACTION_PROCESS_TEXT` for compatible Android text-selection menus.
- Image picker supports selecting multiple images; maximum 10 images per operation.
- ML Kit reads content URIs directly and processes OCR sequentially.
- Failed images do not prevent remaining images from being processed.
- Dedicated Batch Process entry in the navigation menu opens a separate multi-image workflow.
- Batch OCR processes one image at a time, shows per-image Waiting / Processing / Complete / No text found / Failed states, and reports a completion summary.
- Batch selection and processing are capped at 10 images per run. Extracted batch text is placed in the editor for review; the user must tap Save Results to store it in local history.

## Scan modes

- QR Scan uses CameraX preview and ML Kit Barcode Scanning. It returns the first detected QR payload to the editor.
- Camera Scan uses CameraX preview and on-device ML Kit Latin text recognition. It returns detected text to the editor.
- Android camera permission is requested only after the user chooses QR Scan or Camera Scan. No camera permission prompt is shown at app startup.
- Camera hardware is declared optional so the app can still install on devices without a camera; scan features require compatible camera hardware.

## Actual actions

- Phone numbers open the dialler; the app does not place calls.
- Email addresses open an email composer.
- URLs open a browser.
- Detected dates prepare a calendar insert screen for user review.
- Map search opens a map app with the supplied text as a search query.
- Captures are stored in a local SQLite database only after the user taps Save.
- External actions are launched only after the user taps the relevant action.

## Permissions and privacy

- `android.permission.CAMERA` is the only declared permission. It is used by the explicit QR and camera scan features and requested at the point of use.
- No contacts, call, SMS, call-log, location, notification-listener, accessibility, overlay or broad storage permissions.
- Android's system content picker and temporary shared-URI grants are used for image input.
- No background reading of WhatsApp, SMS, notifications or other apps.
- Shared content is processed only after the user chooses Pocket Action or starts a scan.
- Captures remain in the app's local SQLite database unless the user chooses to share them.
- OCR currently supports Latin script. Add and test other ML Kit script models separately for broader language coverage.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 36, sync Gradle, then build `app` > `assembleDebug`. The GitHub Actions workflow installs Gradle 8.13 and Android SDK 36, runs JVM unit tests and Android Lint, then builds and uploads a debug APK when all checks pass.

The current source package does not include Gradle wrapper files. CI uses the pinned Gradle runtime directly; see `GITHUB_UPLOAD_GUIDE.md` for upload and verification steps.

**Verification status:** Source files and manifest have been revised. This environment does not have Gradle or an Android SDK build setup available, so compilation, APK generation and physical-device testing have not yet been verified. Do not treat the project as a tested release until the CI build and device checks pass.

## Source-aligned corrections in this revision

- Capture history search now searches the full saved set rather than only the five home-screen rows.
- Saved captures can be deleted individually from Recent Captures and the full history view; Clear All requires confirmation.
- Image selection now uses AndroidX `PickMultipleVisualMedia` (system Photo Picker where available), limited to 10 images.
- Detected phone/email data can be passed to the system's user-confirmed contact insertion screen; no contacts permission is requested.
- Dialler, email, browser, maps, calendar, and contact actions offer a copy-to-clipboard fallback if no compatible external app can handle the action.
- Web destinations are restricted to HTTP/HTTPS URLs with a host and without embedded credentials.
- A small JVM unit-test suite covers web URL validation. GitHub Actions runs unit tests before assembling the debug APK.

These source changes preserve the existing home-screen structure, colours, spacing direction, brand header, scan popup, input cards, quick-action icon colours, workspace card, and recent-capture visual style. The added delete and fallback dialogs are supporting workflows, not a redesign.

**Verification limitation:** This environment has no Gradle installation or Android SDK, so Android compilation, APK generation, device testing, runtime permission testing, and GitHub Actions execution remain unverified. A source edit is not a verified build.


## Proprietary licensing

Pocket Action's original application source, branding and design are
proprietary. Use and distribution are governed by `LICENSE`. Third-party
components remain subject to their own licences and terms; see
`THIRD_PARTY_NOTICES.md`. See `PRIVACY.md` for the privacy notice draft.
Complete all placeholders and legal review before distribution.

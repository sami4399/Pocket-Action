# Pocket Action — Privacy Notice

**Draft for product-owner review.** This notice describes the source-level
behaviour documented in this project and known third-party SDK behaviour.
It is not a substitute for reviewing the final APK, SDK configuration, and
the requirements of each distribution jurisdiction.

**Effective date:** [COMPLETE BEFORE RELEASE]  
**Privacy contact / data controller:** [LEGAL OWNER NAME AND CONTACT]

## 1. What Pocket Action does

Pocket Action lets you intentionally provide text or images to the app by
using Android's share interface, selecting images, pasting text, or starting
a camera scan. The app can use on-device ML Kit recognition to extract text
and identify QR/barcode content. It then offers actions such as opening the
dialler, email app, browser, maps app, calendar insertion interface, or
contact insertion interface. You choose whether to use those actions.

## 2. Content you provide

Depending on the feature you use, content may include:
- text or images you explicitly share with Pocket Action;
- images you explicitly select through Android's system picker;
- text you explicitly paste after tapping the paste control;
- images captured after you start a camera scanning feature; and
- text recognised from those inputs.

The app's documented design does not read other apps' messages or
notifications in the background. It does not use an Accessibility Service
or overlay to monitor other applications.

## 3. On-device processing and local history

Image and text recognition is performed using the configured ML Kit SDKs.
Saved captures are stored in the app's local SQLite database after you
explicitly choose Save. The project manifest disables Android app backup
(`allowBackup=false`). This is a source configuration statement, not a
guarantee against every form of device-level extraction or user-initiated
sharing.

If you invoke an external action or share content, the selected information
is passed to the relevant Android application or service. That destination
then handles the information under its own privacy terms.

## 4. Google ML Kit and service communications

Although ML Kit performs recognition on-device, Google's ML Kit terms and
documentation describe service communications that may include requests for
model updates or compatibility information and collection of API performance
and utilisation metrics. Do not interpret “on-device recognition” as a
promise that no SDK-related information is ever transmitted.

Review the current ML Kit terms and disclosures before release:
https://developers.google.com/ml-kit/terms

## 5. Permissions and access

The source manifest declares the Android camera permission for user-initiated
QR and camera scanning. The app uses Android's system photo picker / shared
content URI grants for image selection and sharing. Clipboard content is
accessed only when the user invokes the paste action, according to the
documented design.

The source manifest does not declare contacts, call, SMS, call-log, location,
notification-listener, accessibility, overlay, or broad storage permissions.
Verify this against the final merged manifest for every release variant.

## 6. Retention and deletion

Saved captures are held in the app's local database until you delete them or
remove the app's data/uninstall the app, subject to Android platform behaviour.
Use the in-app delete controls where available. The project owner should
verify actual deletion behaviour and document any retained diagnostics before
release.

## 7. Diagnostics, analytics, and crash reporting

The reviewed project configuration does not document a first-party analytics
or crash-reporting integration. Third-party SDK communications, including
ML Kit behaviour described above, are separate. This statement must be
rechecked against the final dependency graph and runtime network behaviour.

## 8. Children and age restrictions

[OWNER TO COMPLETE: state whether the app is directed to children, intended
for a general audience, or subject to age restrictions. Complete applicable
store declarations and legal requirements.]

## 9. Security

The owner should maintain supported dependencies, protect release-signing
keys, minimise permissions, and avoid logging user-provided content. No
software can be guaranteed completely secure.

## 10. Changes and contact

This notice may be updated when features, SDKs, or legal requirements change.
For privacy questions or requests, contact: [LEGAL CONTACT EMAIL].

Before release, complete all bracketed fields, verify actual runtime behaviour,
and obtain privacy/legal review for the intended markets.

# ShortPlay

Android video player proof-of-concept focused on short-form, guardian-supervised viewing.

## What is enforced locally

* The media library marks every item longer than five minutes as blocked, based on Android's indexed duration.
* Playback validates duration again when the media decoder prepares the file and stops at five minutes as a final guard.
* Playback time is counted only while this app's player is running, and shown on the home screen.
* The app cannot be used until its accessibility service is enabled.
* The accessibility service checks currently visible app text for configurable adult-content warning terms. When it finds one, it stops playback and displays the guardian-verification screen.

## Guardian email verification

The app deliberately does **not** contain SMTP credentials. Doing so would expose a mailbox key to anyone who extracts the APK. The guardian setup screen, opened by Android secret dial code `*#*#73836245#*#*` on compatible dialers, stores a recipient email and HTTPS endpoint. It POSTs an `adult_content_lock` event to that endpoint. The endpoint must send a signed one-time verification link or code and provide a verification response before an app can unlock.

Android 12+ restricts secret-code broadcasts on many devices; on those systems, a device-owner, default dialer, or managed-device integration is required to launch this screen. This is an Android platform constraint, not a security bypass.

## Security boundaries

No consumer Android app can make a guarantee that a device owner with root access, a modified APK, screen capture, or another player cannot bypass a local rule. For high-assurance enforcement, deploy this app as a managed-device (Device Owner) app and enforce app allowlists at the operating-system layer. Likewise, accessibility APIs receive accessibility nodes and text—not video pixels—so production visual adult-content detection needs an on-device ML classifier plus explicit user consent, and must be evaluated for accuracy and privacy.

## Build

Install JDK 17 and Android SDK Platform 35, set `ANDROID_HOME`, then run:

```powershell
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

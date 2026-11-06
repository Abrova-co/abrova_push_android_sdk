# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.21] - 2026-11-06

### Added
- Notification channels. Define channels in Abrova Console (each with its own importance, vibration and sound) and
  send a message with `channelId`.
  - The SDK creates the project's channels at registration, so they show in the app's notification settings before
    the first message, and shows each message on its channel.
  - A `channelId` that is not a Console channel is used as a channel your app created itself; if there is none, the
    default channel is used.
  - When a channel's importance, vibration or sound changes in the Console, the SDK replaces the channel.
  - Before Android 8 the channel's sound and vibration apply to each notification.
- `AbrovaPushMessage.channelId` and `AbrovaPushMessage.channel`.

## [0.1.20] - 2026-11-01

### Fixed
- After a device restart, messages sent to this device arrive without the app being opened first.
- A message shown just before the app was closed is confirmed as delivered when it arrives again, so it is not sent repeatedly.

## [0.1.19] - 2026-10-28

### Added
- Opened, clicked and dismissed tracking: tapping a notification reports it as `opened`, tapping an action button as `clicked` (with the action's id), and swiping it away as `dismissed`. Sent in the background once per message, retried briefly when offline; opening the app never waits for it. Taps are picked up automatically for every activity that is created or resumed; an activity already in front gets the tap in `onNewIntent` — call `setIntent(intent)` there.

### Fixed
- Action buttons now open the app (or the action's link) on Android 12 and later, where the system blocked them. Action callbacks, `getClickedAction()` and A/B click tracking work as before.
- Topic names with spaces, `/` or other special characters are sent correctly in `subscribeTopic()` / `unsubscribeTopic()`.
- `unregister()` now also detaches the signed-in user from this device on the server (as on iOS), so pushes to that user no longer target the device. `getUserId()` returns `null` afterwards.
- A notification tap that launched the app before `init()` ran is now handled once `init()` runs, so `getInitialMessage()` returns it.

## [0.1.18] - 2026-10-14

### Added
- `sendDiagnostics` (default `true`): the SDK sends counts of its own errors, by error code, and of crashes in SDK code with its next request. Counts only, no personal data.

### Changed
- Inside the Flutter or React Native SDK, requests also name the native SDK version.

## [0.1.17] - 2026-10-10

### Fixed
- A crash on Android 12+ when registration finished while the app was not in the foreground. The service now starts when the app is next opened.

## [0.1.16] - 2026-10-08

### Fixed
- A crash when the connection was closed while it was still opening, for example on a device with no internet.

## [0.1.15] - 2026-10-02

### Added
- Signed user tokens (optional): `tokenProvider` in `AbrovaPushConfig`, `AbrovaPush.setTokenProvider`, `setBlockingTokenProvider` and `setUserToken`. The token is sent as `x-user-token`, refreshed before it expires and retried once when the server reports it expired.
- `AbrovaPush.setAuthErrorListener` and `AbrovaPushCallback.onAuthError` for identity errors.

Without a token provider the SDK behaves exactly as before.

## [0.1.14] - 2026-09-29

### Changed
- Faster reconnects: retry delay now capped at 60 s (was 5 min) and the SDK never stops retrying while started.
- Keepalive lowered to 30 s so dropped connections are detected sooner.
- Reconnects right away when the network comes back or changes, the screen turns on, or the app comes to the foreground.
- Requires `ir.abrova:pn-protocol` 0.2.1.

### Added
- Support for additional server-provided connection endpoints with automatic failover.

## [0.1.13] - 2026-09-18

### Added
- Optional FCM add-on (`ir.abrova:abrova-push-fcm`). A message delivered over both paths is shown once.
- `installId` on register, so a reinstall no longer leaves a stale device behind.

## [0.1.12] - 2026-09-14

### Added
- SDK identity: the SDK reports its name and version (`AbrovaPush.SDK_VERSION`).
- `autoRefresh` (default `true`): keeps a registered device up to date on launch.

### Changed
- Delivery confirmations are sent once per message and retried on network errors.

### Fixed
- `clearUserId()` failing offline could leave the previous user attached to the device.
- README: topic methods are `subscribeTopic`/`unsubscribeTopic`; removed options that don't exist.

## [0.1.11] - 2026-09-08

### Added
- Delivery receipts. The SDK now confirms to the server that a notification actually reached the device, so delivery can be told apart from "accepted by the transport".

## [0.1.10] - 2026-08-23

### Added
- App build number captured alongside app version.

## [0.1.9] - 2026-08-23

### Added
- Device attributes captured automatically for segment filters.

## [0.1.8] - 2026-08-18

### Fixed
- App crashed on start on Android <14 (`foregroundServiceType 0x00000001 is not a subset of 0x40000000`). The 0.1.7 manifest declared only `specialUse` but the code still requested `dataSync` on older OS versions. Now Android <14 calls `startForeground` without a type flag.

## [0.1.7] - 2026-08-17

### Fixed
- App crashed at boot on Android 15+ (`ForegroundServiceStartNotAllowedException: FGS type dataSync not allowed to start from BOOT_COMPLETED`). Switched the foreground service to `specialUse`.

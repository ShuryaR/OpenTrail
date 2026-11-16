# OneBook Dialer (OpenTrail) — Open-Source Alternative to Salestrail

**The open-source, self-hosted alternative to Salestrail for Android sales teams.**  
Place native cellular SIM calls, capture two-way `.m4a` call audio, show incoming CRM Caller ID, and sync call logs directly to **Frappe CRM** — with **zero middleman servers**, **zero per-seat SaaS fees**, and **100% data ownership**.

[![Automated Code Review](../../actions/workflows/code-review.yml/badge.svg)](../../actions/workflows/code-review.yml)
[![Android App Build](../../actions/workflows/android-build.yml/badge.svg)](../../actions/workflows/android-build.yml)

---

## Why OneBook Dialer as a Salestrail Alternative?

Teams searching for an **open-source alternative to Salestrail** usually hit the same blockers with proprietary SIM call trackers: per-seat monthly pricing, call recordings routed through third-party cloud dashboards, and limited control over personal vs. work SIM privacy.

**OneBook Dialer** eliminates the middleman completely. The Android app connects straight from your sales rep's phone to your own **Frappe CRM** instance.

| Capability | Salestrail (Proprietary SaaS) | OneBook Dialer (Open-Source) |
| --- | --- | --- |
| **Pricing & Licensing** | Per-user monthly subscription | **100% Free & Open Source** |
| **Middleman Cloud Dashboard** | Required (vendor-hosted relay) | **Zero middleman servers** — direct to your Frappe CRM |
| **Native SIM Calling** | Yes | **Yes** (`ACTION_CALL` via device SIM 1 / SIM 2) |
| **Two-Way Call Recording** | Proprietary APK add-on | **Built-in Accessibility + `MediaRecorder` (`.m4a` AAC)** + OEM folder watcher |
| **Private File Storage** | Vendor cloud bucket | **Frappe Private Files (`is_private = 1`)** |
| **Dual-SIM Work Filter** | Paid plan tiers | **Built-in** (*Any SIM*, *SIM 1 Only*, *SIM 2 Only*) |
| **Personal Call Privacy** | Basic exclusions | **CRM-Only mode + Personal Exclusion list** |
| **Offline Resilience** | Vendor-managed | **Local Room (SQLite) + `WorkManager` exponential backoff** |
| **Credential Security** | App storage | **Android Keystore AES-256-GCM encryption** |

> Looking to migrate your team? Read the full **[Salestrail Alternative & Migration Guide](docs/salestrail-alternative.md)**.

---

## Key Features

### 1. Thumb-First Mobile CRM Pipeline
- **4 Focused Tabs:** **Tasks (Today)**, **Leads**, **Deals** (grouped by Deal Stage), and **Contacts**.
- **One-Tap SIM Dialing:** Tap **Call** on any Lead, Deal, Task, or Contact to hand the number off to the native Android cellular dialer (`ACTION_CALL`).
- **Record Detail Timeline:** View customer fields, CRM Notes, and historical `CRM Call Log` entries with an **inline `.m4a` audio player** (supporting both local queued files and authenticated Frappe `/private/files/` playback).

### 2. Automatic Two-Way Call Recording & Telemetry
- **Bound Accessibility Recorder:** `CallRecorderAccessibilityService` pairs with `MediaRecorder` (`MPEG_4` / `AAC` 44.1 kHz) to capture cellular calls in the background with a persistent microphone foreground indicator.
- **Hardware Audio Source Selector:** Switch between `VOICE_RECOGNITION`, `VOICE_COMMUNICATION`, and `MIC` in App Settings for Samsung, Xiaomi, OnePlus, Motorola, and Pixel devices.
- **OEM Recording Folder Watcher:** Optional fallback scanner for Samsung One UI (`/Recordings/Call`), Xiaomi HyperOS (`/MIUI/sound_recorder/call_rec`), and OnePlus/Oppo native dialer recording folders.
- **System CallLog Pairing:** `CallStateReceiver` tracks `RINGING → OFFHOOK → IDLE` transitions and queries Android's `CallLog.Calls` provider for accurate call direction (`Outgoing`, `Incoming`, `Missed`), duration, timestamp, and SIM subscription slot.

### 3. Incoming CRM Caller ID & Post-Call Wrap-Up
- **Incoming Caller ID Banner:** `CallerIdOverlayService` matches incoming cellular numbers against the local Room CRM cache using `PhoneNormalizer` (stripping spaces, dashes, leading zeros, and country codes like `+91` / `+1`) and shows a compact floating banner (with high-priority notification fallback) displaying the caller's name, company, Deal stage, and last note.
- **Post-Call Quick Note Drawer:** Immediately after hang-up, a swipe-dismissible bottom sheet lets reps tap a disposition (*Connected*, *Busy*, *Callback Later*, *Not Interested*), save a call note, and schedule a follow-up `CRM Task` via a Material 3 date picker.

### 4. Privacy Guardrails & Offline-First Sync
- **Personal Call Privacy Filter:** Choose between **Record & Sync All Calls** or **Only Sync Calls Matching Frappe CRM Numbers**, plus maintain a local **Excluded Personal Numbers** blocklist.
- **Offline-First Queue:** Every call and recording writes to Room SQLite first. `CallSyncWorker` (`WorkManager`) uploads the private `.m4a` file and creates the `CRM Call Log` over `NetworkType.CONNECTED` with exponential backoff—deleting the local temporary audio file only after Frappe returns `200 OK`.
- **Health Status Banner:** Persistent top pill on the Home screen turns **Green** (`Auto-Sync & Recorder Active`) or **Red** (`Action Needed: Re-enable Accessibility`) so reps never make unrecorded calls by mistake.

---

## Documentation Index

- **[Salestrail Alternative & Migration Guide](docs/salestrail-alternative.md)** — Feature comparison, cost breakdown, and team rollout checklist.
- **[Android Client Architecture](docs/architecture.md)** — Kotlin/Compose modules, Room schema, `CallStateReceiver`, `AccessibilityService`, and `WorkManager` sync pipeline.
- **[Frappe CRM Setup & API Mapping](docs/frappe-crm-setup.md)** — API Key/Secret generation, DocType field mappings (`CRM Lead`, `CRM Deal`, `Contact`, `CRM Task`, `CRM Call Log`, `CRM Note`), and private file uploads.
- **[Device Setup & OEM Tuning Guide](docs/device-setup-oem-guide.md)** — Android 13–16+ Restricted Settings unlock, Battery Optimization exemption, VoWiFi audio warning, and Samsung/Xiaomi/OnePlus tuning.

---

## Quick Start

### 1. Build the APK Locally
Requires **JDK 21**, **Android SDK (API 36)**, and **Gradle 9.3.1**.

```bash
# Restore debug keystore (if not present)
base64 -d debug.keystore.base64 > debug.keystore

# Run unit & Robolectric tests
gradle :app:testDebugUnitTest

# Assemble sideloadable Debug APK
gradle :app:assembleDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Download from GitHub Actions CI
Every push and pull request runs:
- **Code Review Workflow** (`.github/workflows/code-review.yml`): Security/privacy static checks, Android Lint (`:app:lintDebug`), unit/Robolectric tests, and automated PR review summary comments.
- **Android Build Workflow** (`.github/workflows/android-build.yml`): Builds `onebook-dialer-debug-apk` artifacts on every commit and signed Release `.apk` / `.aab` bundles on `v*` version tags.

### 3. Sideload & Onboard Device
1. Install the APK on your sales rep's Android device (Android 10 through Android 16+).
2. Sign in with your **Frappe CRM URL** and **API Key + API Secret** (or **Email & Password**).
3. Complete the **4-Step Device Setup Checklist**:
   - Grant **Phone & Call Log** permissions.
   - Grant **Microphone & Notifications** permissions.
   - Open **App Info → ⋮ → Allow restricted settings**, then enable **OneBook Call Recorder** in **Accessibility Settings**.
   - Grant **Unrestricted Battery Usage**.

---

## GitHub Repository SEO Metadata

If you fork or publish this repository as an open-source Salestrail alternative (for example under **OpenTrail** / **OneBook Dialer**):

- **Short GitHub About Description (104 chars):**
  `The open-source, self-hosted alternative to Salestrail. Automatically log native SIM calls to Frappe CRM.`
- **Recommended GitHub Topics:**
  `salestrail-alternative` `open-source-salestrail` `sim-call-logger` `call-recording` `frappe-crm` `android-crm` `dual-sim` `kotlin` `jetpack-compose` `self-hosted`

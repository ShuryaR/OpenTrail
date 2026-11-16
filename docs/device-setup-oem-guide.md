# Device Setup & OEM Call Recording Guide

Because OneBook Dialer is distributed directly to company Android devices (Android 10 through Android 16+) via sideloaded APK or internal MDM, Android applies extra security protections around Accessibility Services and background battery usage.

Follow this guide when onboarding devices in the field.

---

## 1. The 4-Step Device Setup Checklist

### Step 1: Phone & Call Log Permissions
- Grants `CALL_PHONE`, `READ_PHONE_STATE`, `READ_CALL_LOG`, and `READ_PHONE_NUMBERS`.
- Allows OneBook Dialer to hand calls to the native SIM dialer, detect Dual-SIM slots, and read exact call duration/direction after hang-up.

### Step 2: Microphone & Notifications
- Grants `RECORD_AUDIO` and `POST_NOTIFICATIONS` (Android 13+).
- Required to capture local `.m4a` call audio and display the mandatory foreground recording notification while a call is active.

### Step 3: Allow Restricted Settings + Accessibility Service (Android 13+)
On Android 13, 14, 15, and 16, sideloaded APKs have Accessibility Services greyed out by default ("Restricted setting").
1. Tap **1. App Info** in the OneBook Permission Wizard.
2. Tap the **⋮** (three dots) menu in the top-right corner of the App Info screen.
3. Tap **Allow restricted settings** and confirm with your PIN/biometric.
4. Return to OneBook Dialer and tap **2. Accessibility**.
5. Find **OneBook Call Recorder** under Downloaded Services and toggle it **ON**.

### Step 4: Unrestricted Battery Usage
- Exempts OneBook Dialer from Doze / battery optimization so `CallStateReceiver` and `WorkManager` never miss call completion events or delay uploads.

---

## 2. VoWiFi (Wi-Fi Calling) Warning

> **Important:** On several Android chipsets (MediaTek and select Snapdragon/Exynos firmware), enabling **Wi-Fi Calling (VoWiFi)** routes the remote caller's downlink audio through a hardware IMS path that mutes the remote party in `MediaRecorder` recordings.

If recordings only capture the sales rep's voice during office calls:
- Open **Android Settings → Network & Internet / SIMs → Wi-Fi Calling** and toggle **Wi-Fi Calling OFF**.

---

## 3. OEM Audio Source & Folder Watcher Tuning

In **Sync Queue & Settings → Audio Capture Source**, you can switch audio sources per device model without reinstalling the app:

| Device / OEM | Recommended Setting | Notes |
| --- | --- | --- |
| **Google Pixel / Motorola / Stock Android** | `VOICE_RECOGNITION` | Default setting; works reliably via Accessibility Service. |
| **Samsung One UI** | `VOICE_COMMUNICATION` or **OEM Folder Watcher ON** | If Samsung's dialer records natively, enable **OEM Recording Folder Watcher** to pick up files from `/Recordings/Call`. |
| **Xiaomi / Redmi / POCO (HyperOS / MIUI)** | `VOICE_RECOGNITION` or **OEM Folder Watcher ON** | Enable Xiaomi's native call recorder and toggle **OEM Recording Folder Watcher** (`/MIUI/sound_recorder/call_rec`). Also enable **Autostart** in MIUI App Info. |
| **OnePlus / OPPO / Realme** | `VOICE_COMMUNICATION` or `MIC` | Disable "Optimize battery use" in OxygenOS/ColorOS battery settings. |

---

## 4. Incoming Caller ID Floating Banner

- Granting **Display over other apps** (`SYSTEM_ALERT_WINDOW`) lets OneBook Dialer show a floating CRM Lead/Deal banner at the top of the screen while the phone rings.
- If a rep prefers not to grant overlay permission, OneBook Dialer automatically falls back to a high-priority heads-up **Caller ID Notification** with the same Lead/Deal info.

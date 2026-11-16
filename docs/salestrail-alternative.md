# Open-Source Alternative to Salestrail

If you are evaluating a **Salestrail alternative** or looking for an **open-source alternative to Salestrail** that gives your company full ownership of call logs and audio recordings, **OneBook Dialer** is built specifically for that workflow.

---

## Why Teams Switch from Salestrail

Proprietary SIM call-tracking SaaS tools solve an important sales problem—capturing cellular SIM calls without expensive VoIP minutes—but introduce three major compromises:

1. **Recurring Per-Seat Licensing:** Costs scale linearly with every field rep added to the team.
2. **Third-Party Middleman Servers:** Call metadata and customer phone numbers pass through a vendor's cloud dashboard before reaching your CRM.
3. **Opaque Privacy Boundaries:** Reps often hesitate to install tracking apps on dual-SIM phones if personal calls aren't strictly isolated.

**OneBook Dialer** removes the middleman entirely:
- Your Android app talks **directly** to your self-hosted or cloud **Frappe CRM** server over HTTPS.
- Call audio `.m4a` recordings upload directly to Frappe as **Private Files (`is_private = 1`)**.
- Credentials are encrypted on-device inside **Android Keystore (AES-256-GCM)**.

---

## Feature-by-Feature Comparison

| Feature | Salestrail | OneBook Dialer |
| --- | --- | --- |
| **Deployment Model** | Vendor SaaS + APK | **Direct APK → Your Frappe CRM (No Middleman)** |
| **Monthly Cost per Rep** | Per-seat SaaS fee | **$0 (Open Source)** |
| **Native SIM Calling** | Yes | Yes (`ACTION_CALL` with SIM slot selection) |
| **Two-Way Audio Capture** | Separate APK | Integrated Accessibility + `MediaRecorder` (`.m4a` AAC) |
| **OEM Native Recorder Fallback** | Limited | Built-in **OEM Recording Folder Watcher** (Samsung, Xiaomi, OnePlus) |
| **Incoming Caller ID** | Yes | Floating overlay banner + notification fallback from local CRM cache |
| **Post-Call Notes & Tasks** | App prompt | Bottom sheet with disposition chips, notes, and follow-up `DatePicker` |
| **Offline Queue** | Yes | **Room SQLite + `WorkManager`** with exponential backoff |
| **Dual-SIM Filtering** | Tiered plans | Included (*Any SIM*, *SIM 1 Only*, *SIM 2 Only*) |
| **CRM-Only Privacy Mode** | Limited | Included (*Only Sync Calls Matching Frappe CRM Numbers* + Blocklist) |

---

## Migration Checklist (Salestrail → OneBook Dialer)

1. **Prepare Frappe CRM:**
   - Generate an **API Key & API Secret** for each sales rep (or allow Email & Password login) in Frappe. See [Frappe CRM Setup](frappe-crm-setup.md).
2. **Distribute the Internal APK:**
   - Download the signed APK from the repository's **Releases** page (built automatically by `.github/workflows/android-build.yml`) and install it on each rep's phone. Later releases install over the earlier one and keep the rep's data.
   - *Note:* OneBook Dialer is distributed only through GitHub Releases. Google Play does not allow Accessibility-based call recording, so the app is not published there.
3. **Run the 4-Step Device Wizard:**
   - Have each rep complete the onboarding checklist (Phone/Call Log, Mic/Notifications, Restricted Settings + Accessibility Service, Battery Unrestricted). See [Device Setup & OEM Guide](device-setup-oem-guide.md).
4. **Configure Privacy & Dual-SIM Preferences:**
   - In **Sync Queue & Settings**, set **Work SIM Slot** (e.g., `SIM 1 Only`) and optionally switch **Personal Call Privacy Filter** to **Only Sync Calls Matching Frappe CRM Numbers**.
5. **Verify Live Sync:**
   - Place a test call from the **Leads** or **Tasks** tab, hang up, submit a disposition in the **Quick Call Wrap-Up** sheet, and confirm the `CRM Call Log` + private audio player appear on the Frappe CRM timeline.

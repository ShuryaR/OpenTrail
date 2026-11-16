# Frappe CRM Setup & API Mapping

OneBook Dialer connects directly to **Frappe CRM** (and supports classic ERPNext fallback DocTypes where applicable) using standard Frappe REST APIs. No custom Frappe server app or third-party middleware is required.

---

## 1. Generating API Credentials in Frappe

Sales reps can authenticate using either **API Token** (recommended) or **Email & Password**:

### Option A: API Key & API Secret (Recommended)
1. Log in to your Frappe / Frappe CRM site as an Administrator (or the user with API access).
2. Open **User** → select the sales rep's user account.
3. Scroll to **API Access** → click **Generate Keys**.
4. Copy the generated **API Key** and **API Secret**.
5. In OneBook Dialer, enter:
   - **Frappe CRM URL:** `https://crm.yourcompany.com`
   - **API Key** & **API Secret**

### Option B: Email & Password
- Toggle **Email & Password** on the login screen.
- OneBook Dialer authenticates against `POST /api/method/login`, captures the `sid` session cookie, and encrypts it in Android Keystore.

---

## 2. REST Endpoints & DocType Mapping

| App Feature | Frappe Endpoint | DocTypes Used |
| --- | --- | --- |
| **Verify Login** | `GET /api/method/frappe.auth.get_logged_user` | `User` |
| **Password Login** | `POST /api/method/login` | Session (`sid`) |
| **Leads Tab** | `GET /api/resource/CRM Lead` (fallback `Lead`) | `CRM Lead` / `Lead` |
| **Deals Tab** | `GET /api/resource/CRM Deal` (fallback `Opportunity`) | `CRM Deal` / `Opportunity` |
| **Contacts Tab** | `GET /api/resource/Contact` | `Contact` |
| **Tasks Tab** | `GET /api/resource/CRM Task` (fallback `ToDo`) | `CRM Task` / `ToDo` |
| **Mark Task Done** | `PUT /api/resource/CRM Task/{name}` (fallback `ToDo/{name}`) | `CRM Task` / `ToDo` |
| **Private Audio Upload** | `POST /api/method/upload_file` (`is_private=1`) | `File` |
| **Log Call Record** | `POST /api/resource/CRM Call Log` | `CRM Call Log` |
| **Add / Fetch Notes** | `GET/POST /api/resource/CRM Note` | `CRM Note` |
| **Schedule Follow-Up** | `POST /api/resource/CRM Task` (fallback `ToDo`) | `CRM Task` / `ToDo` |

---

## 3. Private Call Recording Uploads

Every `.m4a` recording uploaded by `CallSyncWorker` uses `POST /api/method/upload_file` with:
- `is_private = 1` (ensures recordings are served via `/private/files/...` and require Frappe authentication—never publicly accessible).
- `folder = "Home/Call Recordings"`
- `doctype` and `docname` attached when the phone number matched a `CRM Lead`, `CRM Deal`, or `Contact`.

The returned `file_url` is immediately linked to the `recording_url` field on the newly created **CRM Call Log** so the recording plays inline directly inside Frappe CRM's activity timeline.

---

## 4. Permissions Required on Frappe User Roles

Ensure your Sales Rep role in Frappe has:
- **Read** permission on `CRM Lead`, `CRM Deal`, `Contact`, `CRM Task`, `CRM Call Log`, `CRM Note`.
- **Create / Write** permission on `CRM Call Log`, `CRM Note`, `CRM Task` (and `File` uploads).

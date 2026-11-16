# Releasing a New Version

OneBook Dialer is distributed **only as a signed APK on GitHub Releases**. There is no Play Store listing and no `.aab` bundle.

Reps install the APK once. Every later release installs over it and keeps their data, **as long as every release is signed with the same key**. That key is the one thing you must never lose.

---

## One-time setup: the signing key

The release key lives on the maintainer's machine, outside the repository (for example `~/.onebook-release/`):

| File | What it is |
| --- | --- |
| `onebook-release.jks` | The signing keystore (alias `upload`) |
| `keystore_password` | Its password (also the key password) |
| `keystore_base64` | The keystore as one line of text, for the GitHub secret |

Generate it once (skip this if you already have one):

```bash
D="$HOME/.onebook-release"; mkdir -p "$D" && chmod 700 "$D"
openssl rand -base64 36 | tr -dc 'A-Za-z0-9' | head -c 28 > "$D/keystore_password"
keytool -genkeypair -keystore "$D/onebook-release.jks" -storetype PKCS12 \
  -storepass "$(cat $D/keystore_password)" -keypass "$(cat $D/keystore_password)" \
  -alias upload -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=OneBook Dialer,O=OneBook"
base64 < "$D/onebook-release.jks" | tr -d '\n' > "$D/keystore_base64"
chmod 600 "$D"/*
```

Then add three repository secrets (needs admin access to the repo):

```bash
D="$HOME/.onebook-release"
gh secret set RELEASE_KEYSTORE_BASE64 --repo ShuryaR/OpenTrail < "$D/keystore_base64"
gh secret set STORE_PASSWORD          --repo ShuryaR/OpenTrail < "$D/keystore_password"
gh secret set KEY_PASSWORD            --repo ShuryaR/OpenTrail < "$D/keystore_password"
```

> **Back up `onebook-release.jks` and its password somewhere safe (a password manager).**
> If the key is lost, phones refuse to update the app. Reps would have to uninstall and reinstall, which deletes their local queue of unsynced calls. Never commit these files; `.gitignore` blocks `*.jks`.

---

## Publishing a release

1. Make sure `main` is green (tests and lint pass).
2. Tag it and push the tag. The version comes from the tag, so there is nothing to edit:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

3. The **Android App Build & Release** workflow runs the tests, builds and signs the APK, checks the signature and version, and publishes a GitHub Release with:
   - `OneBook-Dialer-v1.0.0.apk`
   - `OneBook-Dialer-v1.0.0.apk.sha256`

Tags must look like `v1.2.3` (three numbers, minor and patch at most 999). Anything else fails the build with a clear message.

### How the version is set

| Tag | `versionName` | `versionCode` |
| --- | --- | --- |
| `v1.0.0` | `1.0.0` | `1000000` |
| `v1.2.3` | `1.2.3` | `1002003` |

Android only installs an update when `versionCode` is higher than the installed one. The code is derived from the tag, so each new tag must be a higher version than the last.

### What happens if the secrets are missing

The release job **fails** with `Missing repository secret(s): ...` instead of finishing green with nothing published.

### Testing the signing setup without publishing

Run the workflow manually (Actions tab, **Run workflow**). It builds a signed APK versioned `0.0.0-dev.N`, uploads it as a workflow artifact, and publishes nothing. Do not hand that file to reps; it is a lower version than any real release.

---

## What about debug builds?

Pull requests attach a debug APK for testing. It is signed with a throwaway key that changes on every CI run, so it cannot update another build and should never be given to reps.

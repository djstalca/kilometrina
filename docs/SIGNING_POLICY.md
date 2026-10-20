# Kilometrina signing policy

This repository has one approved long-term release signing identity.

## Approved certificate

SHA-256 fingerprint:

`FA:9A:23:43:5C:52:A9:DA:DB:4E:D2:27:08:6F:C3:EF:7E:F6:1C:03:E4:8C:29:4E:EC:1F:5D:46:B8:76:67:15`

The canonical machine-readable pin is `docs/release-certificate-sha256.txt`. This identity begins with version **1.1.7 (117)**, explicitly authorized by the owner on 20 October 2026 after loss of earlier private keys.

Historical repository pin: `AB175B9564EF6DE688416C0FDFD59A8AEE258A01396A449D5976FCE954F9F593`. The installed debug-signed 1.1.5 uses `DF61AE8E46E010E5841F865739259F9504F403B18DD34696F1F83CCC142CB516`. Neither private key was available. The new identity cannot update those installations in place: migration requires a verified current backup and restore. Do not uninstall or clear data before that backup is verified.

## Mandatory release rules

1. Every APK or AAB delivered as a release must be signed with the approved certificate above.
2. Unsigned release artifacts and debug APKs are CI/test artifacts only and must not be delivered as update packages.
3. Before a signed release is uploaded, CI must verify the keystore certificate fingerprint.
4. After the build, CI must verify the signing certificate of both APK and AAB and fail if it differs from the approved fingerprint.
5. Every update must keep `applicationId = si.lukabencina.kilometrina` and use a `versionCode` greater than the previously released version.
6. The keystore, passwords, Base64 keystore content, and private key must never be committed to this public repository. They belong only in secure GitHub Actions Secrets and the owner's offline backup.
7. If the approved signing key is unavailable, do not generate or deliver a replacement-key release. Stop and restore the approved key instead.

## Required GitHub Actions secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The certificate fingerprint itself is public metadata and is intentionally stored in the repository so CI and future maintainers can verify release identity.

## Local release builds

Keep the owner's signing directory outside the checkout. It contains `kilometrina-release.jks`, `signing.json`, the Windows-user-encrypted `password.dpapi`, and a portable `recovery-password.txt`. Its ACL grants access only to the current user and SYSTEM. Never upload that directory with release artifacts.

Set `JAVA_HOME` (JDK 17) and `ANDROID_HOME`, then run:

```powershell
.\tools\build-signed-release.ps1
```

The script reuses the existing identity, rejects a mismatched key, runs unit tests and release lint, builds APK/AAB, verifies both signatures, and copies versioned packages into the owner's Downloads/Kilometrina-releases directory. Optional `-SigningDirectory` and `-OutputDirectory` parameters override the defaults. Gradle also checks the certificate whenever signing credentials are provided.

Copy the keystore and recovery password to independent secure offline storage; a folder on the same PC is not a disaster-recovery backup. Store the recovery password in a password manager or separately from the offline keystore. DPAPI alone cannot recover the password on another Windows account/computer. The local plaintext recovery file is private secret material and must remain protected.

GitHub Actions signing remains unavailable until the owner provisions the four secrets listed above. The local key has not been uploaded automatically. Future versions must reuse this key and increase versionCode. Losing it again prevents in-place updates; restore it instead of silently rotating.

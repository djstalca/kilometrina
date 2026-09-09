# Kilometrina signing policy

This repository has one approved long-term release signing identity.

## Approved certificate

SHA-256 fingerprint:

`AB:17:5B:95:64:EF:6D:E6:88:41:6C:0F:DF:D5:9A:8A:EE:25:8A:01:39:6A:44:9D:59:76:FC:E9:54:F9:F5:93`

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

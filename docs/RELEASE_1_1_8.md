# Kilometrina 1.1.8

The monthly PDF keeps its compact portrait layout. Its summary now has a subtle background and a larger final amount. A completed trip with exactly zero recorded distance and positive parking or toll costs displays `Samo stroški` in place of the route and a dash in the kilometres column. This is a presentation label; stored trips, original routes, CSV fields, and monetary calculations are unchanged.

The per-kilometre tariff and application version remain hidden in the PDF. The existing CSV format is unchanged.

New reverse-geocoded start/end locations and stops request Slovenian names consistently, independently of the phone's language. Saved-place names and existing trip addresses remain intact. No historical addresses are rewritten or inferred from text.

Version code 118 uses the same approved signing identity as 1.1.7. Build with `tools/build-signed-release.ps1` and retain the signing material outside Git as described in `SIGNING_POLICY.md`.

Validation on 1 November 2026: debug build and unit tests passed. The native export matrix ran on the disposable API 35 emulator, and independent checks passed for all 18 PDF/CSV cases and six ZIP cases. Rendered eight-trip, toll-only, and multipage-summary examples were visually reviewed. No real phone was modified.

The signed APK and AAB build passed, with 49 passing unit tests and release lint reporting zero errors (19 existing advisory warnings). APK/AAB signatures matched the approved certificate.
An emulator installation of the signed production 1.1.7 was updated in place to the signed 1.1.8; installation and launch succeeded, and the package reported versionCode 118.

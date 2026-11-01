# Kilometrina 1.1.10: precise new locations, preserved history

New start, end and automatic-stop labels share one address formatter. Saved-place names retain priority. Otherwise the app prefers street plus house number and city, uses the provider's full address line when separate street fields are missing, and falls back to a city only when no more precise address is supplied. Domestic labels omit the country; foreign street addresses retain the country when available.

Existing trip addresses and manually edited stop names are not automatically re-geocoded, renamed or backfilled. Automatic historical route refreshing has been removed from startup and backup restoration: older records do not reliably distinguish manual labels from generated ones. Finishing an active trip also retains its stored departure label. Any correction to an old coarse label requires an explicit user edit.

Display formatting no longer drops everything after the second comma. Historical strings remain stored as provided; normal PDF width limits still apply, while CSV keeps the full route text.

Room/backup schema remains 7. Version code 120 uses the release certificate introduced in 1.1.7.

The private backup regression test accepts its fixture through the `backupFixture` instrumentation argument. The fixture is copied into the disposable emulator's private app directory; private trip data is never embedded in the test APK or committed to Git. Tests compare trips, GPS points, saved places, vehicles, settings and converted expense entries across restore and re-export. A separate history test checks that completing a trip cannot rewrite an existing departure or historical manual labels, including legacy records with route detection version 0.

Validation on 1 November 2026: **58 unit tests passed**. The historical-label test, private-backup restore/re-export test and native export matrix passed on the emulator. Independent JSON comparison confirmed that all original fields of 16 trips, 16,687 GPS points, saved places, vehicles and settings remained equal, and that the source backup was unchanged. All 18 PDF/CSV cases and six ZIP cases passed. Signed APK/AAB verification passed; release lint reported zero errors and 19 advisory warnings. The signed 1.1.9→1.1.10 update installed and launched successfully on the disposable emulator. No physical phone was modified.

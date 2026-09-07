# Kilometrina

Native Android aplikacija za beleženje službenih voženj, dejansko GPS kilometrino, stroške in poročila.

## Kilometrina 1.0

- uporabniško sproženo GPS beleženje dejansko prevožene poti,
- foreground location service z vidnim obvestilom tudi pri ugasnjenem zaslonu,
- GPS diagnostika in filtriranje slabih/skokovitih meritev,
- ročni vnos in popravljanje voženj,
- recovery nedokončane vožnje,
- priljubljene lokacije in pametno prepoznavanje shranjenih ciljev,
- opcijska zaznava verjetne vožnje (privzeto izključena; vožnje nikoli ne začne samodejno),
- več vozil z zgodovinskim snapshotom vozila na posamezni vožnji,
- parkirnine in cestnine,
- mesečni PDF in CSV obračuni,
- letna statistika, top relacije in pregled po vozilih,
- lokalni prikaz shranjene GPS trase,
- JSON backup/restore ter Android Auto Backup/device transfer,
- Material 3, light/dark in Material You,
- lokalna obdelava brez uporabniškega računa, oglasov ali analitičnega SDK-ja.

## Stack

- Kotlin 2.3.21
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose BOM 2026.08.00
- Material 3
- Room 2.8.4
- DataStore Preferences 1.2.0
- Google Play services Location 21.4.0
- compileSdk 37 / targetSdk 36 / minSdk 26
- Java/Kotlin bytecode 17

## Razvoj in preverjanje

Za realen test kilometrine uporabi fizični telefon z natančno lokacijo. Aplikacija ne zahteva `ACCESS_BACKGROUND_LOCATION`: uporabnik začne vožnjo na vidnem zaslonu, nato sledenje teče kot `location` foreground service z vidnim obvestilom.

GitHub Actions ob vsakem pushu na `main` in `feature/**` preveri:

- debug APK,
- JVM unit teste,
- Android lint,
- optimiziran release AAB z R8 in resource shrinkingom.

## Release signing

Zasebni signing/upload key se ne hrani v repozitoriju. Workflow **Build signed release** uporablja samo GitHub Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Ko so secrets nastavljeni, ročni workflow izdela in preveri podpisana `app-release.apk` in `app-release.aab` ter shrani R8 `mapping.txt`.

## Play Store

Priprava za Play Store je v mapi `play-store/`:

- slovenski listing,
- Data Safety delovni list,
- foreground-service/location deklaracija in scenarij za demo video,
- release checklist in načrt screenshotov.

Politika zasebnosti je v [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Zasebnost

GPS točke, relacije, vozila, stroški in nastavitve se obdelujejo lokalno. Aplikacija nima lastnega strežnika, oglasov, analitike ali uporabniškega računa. Uporabnik lahko sam ustvari JSON backup; Android Auto Backup/device transfer sta podprta kot sistemski funkciji.

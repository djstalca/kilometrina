# Kilometrina

Android aplikacija za hitro beleženje službenih poti in obračun kilometrine.

## V0.1

- začetek vožnje z eno potezo,
- GPS beleženje dejansko prevožene poti v foreground location servisu,
- zaključek vožnje s shranjeno končno lokacijo,
- namen poti,
- nastavljiva postavka €/km (privzeto 0,43),
- lokalna Room baza,
- mesečni pregled kilometrov in zneska z listanjem med meseci,
- CSV izvoz izbranega meseca, prilagojen odpiranju v Excelu,
- brez uporabniškega računa in strežniške baze,
- Material 3 / Jetpack Compose UI,
- light/dark in Material You dinamične barve.

## Stack

- Kotlin 2.3.21
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose BOM 2026.08.00
- Material 3
- Room 2.8.4
- Google Play services Location 21.4.0
- DataStore Preferences

## Razvoj

Odpri projekt v aktualnem Android Studiu, počakaj na Gradle sync in zaženi `app` na fizičnem telefonu. Za realen test kilometrine uporabi fizični telefon z vklopljenim GPS.

Aplikacija zahteva natančno lokacijo. Background location permission ni zahtevana: uporabnik začne vožnjo na vidnem zaslonu, nato sledenje teče kot location foreground service z vidnim obvestilom.

## Naslednji koraki pred Play Store izdajo

- instrumentacijski testi na več Android različicah,
- test voženj v mestu, na avtocesti, v predoru in ob slabem GPS signalu,
- urejanje že zaključene vožnje,
- parkirnine in cestnine v UI,
- PDF mesečni potni nalog,
- varnostna kopija/obnova lokalnih podatkov,
- Play Store privacy/data-safety dokumentacija,
- podpisan AAB in release CI.

## Build osnova

- Android Gradle Plugin 9.4 z vgrajenim Kotlinom
- Kotlin/Compose compiler 2.3.21
- compileSdk 37, targetSdk 36, minSdk 26
- Java/Kotlin bytecode 17


## Preverjeno v tem paketu

- GPS filtrirna logika je preverjena z lokalnim Kotlin testom.
- CSV izračun in format sta preverjena z lokalnim Kotlin testom.
- Android manifest in XML viri so sintaktično preverjeni.
- Celoten Android build v tem okolju ni bil izveden, ker Android SDK in Gradle runtime nista nameščena. Prvi pravi build naj se izvede v Android Studiu z Android SDK 37.

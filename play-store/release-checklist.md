# Kilometrina 1.0.0 – Play Store release checklist

## Tehnični paket

- [x] applicationId `si.lukabencina.kilometrina`
- [x] versionName `1.0.0`
- [x] versionCode `100`
- [x] targetSdk `36` (Android 16)
- [x] compileSdk `37`
- [x] minSdk `26`
- [x] R8 minification in resource shrinking za release
- [x] release lint gate
- [x] unit testi
- [x] unsigned release AAB se preverja v vsakem CI buildu
- [x] signed release workflow uporablja samo GitHub Secrets
- [ ] dodaj signing/upload key v GitHub Secrets
- [ ] poženi `Build signed release`
- [ ] shrani upload keystore izven repozitorija na vsaj dve varni lokaciji

## GitHub Secrets za signed release

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keystore se nikoli ne commita v repository.

## Play Console – App content

- [ ] Privacy policy URL
- [ ] Data safety
- [ ] Foreground service `location` declaration
- [ ] FGS demo video
- [ ] Precise location declaration, ko jo Play Console za račun/verzijo zahteva
- [ ] Ads: No
- [ ] App access: all functionality available without login
- [ ] Target audience: odrasli/splošna poslovna uporaba; aplikacija ni namenjena otrokom
- [ ] Content rating questionnaire
- [ ] Data deletion/account section: aplikacija nima uporabniškega računa

## Store listing

- [x] Slovenski naslov in opis pripravljen v `listing-sl.md`
- [x] politika zasebnosti pripravljena
- [x] ikona/splash sta vključena v aplikacijo
- [ ] feature graphic 1024 × 500 px
- [ ] vsaj 2 telefonska screenshota
- [ ] priporočeno 5 screenshotov iz spodnjega načrta
- [ ] developer contact email v Play Console

## Predlagani screenshoti

1. **Domov – Začni vožnjo**  
   Besedilo: »Začni službeno pot z enim dotikom«

2. **Aktivna vožnja + GPS status**  
   Besedilo: »Dejanska GPS razdalja tudi z ugasnjenim zaslonom«

3. **Podrobnosti vožnje + GPS trasa**  
   Besedilo: »Preglej traso, stroške in vozilo«

4. **Poročila in letna statistika**  
   Besedilo: »PDF, CSV in letni pregled na enem mestu«

5. **Nastavitve – vozila in pametna zaznava**  
   Besedilo: »Več vozil in opcijski opomnik za vožnjo«

Za screenshote uporabi realistične testne podatke brez dejanskih osebnih naslovov, registrskih oznak ali zgodovine uporabnika.

## Testing track

Če je Play developer račun osebni in je bil ustvarjen po 13. novembru 2023, preveri, ali zanj velja zahteva closed testinga z najmanj 12 testerji, ki so vključeni neprekinjeno najmanj 14 dni, preden lahko zaprosiš za Production access.

## Zadnji smoke test pred uploadom

- [ ] clean install signed release APK
- [ ] upgrade iz prejšnje verzije z istim signing keyem
- [ ] dovoljenja: precise location / notifications / activity recognition
- [ ] 10 min mirovanja brez lažnih kilometrov
- [ ] kratka 2–5 km GPS vožnja
- [ ] screen off med vožnjo
- [ ] stop iz aplikacije
- [ ] stop iz notification action
- [ ] process-kill recovery
- [ ] ročna vožnja
- [ ] več vozil
- [ ] smart saved-place recognition
- [ ] PDF in CSV
- [ ] backup + restore
- [ ] dark mode
- [ ] airplane/offline pregled zgodovine in trase

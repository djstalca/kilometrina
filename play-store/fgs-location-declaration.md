# Play Console – Foreground service: location

## Foreground service type

`location`

Manifest:

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_LOCATION`
- service `android:foregroundServiceType="location"`

Aplikacija ne zahteva `ACCESS_BACKGROUND_LOCATION`.

## Opis funkcije za Play Console

**Funkcija:** beleženje dejansko prevožene razdalje službene vožnje.

Uporabnik v aplikaciji izrecno pritisne »Začni vožnjo«. Aplikacija nato začne foreground location service in prikaže trajno Android obvestilo »Kilometrina se beleži«. GPS točke se uporabljajo za sprotno merjenje dejanske poti, tudi če uporabnik ugasne zaslon ali med vožnjo uporablja drugo aplikacijo. Ko uporabnik pritisne »Končaj vožnjo« v aplikaciji ali obvestilu, se lokacijski foreground service takoj ustavi.

## Zakaj mora opravilo začeti takoj

Če bi sistem začetek sledenja odložil, bi manjkala začetna GPS pot in kilometrina ne bi predstavljala dejansko prevožene razdalje. To je glavna funkcija aplikacije in je neposredno sprožena z dejanjem uporabnika.

## Kaj se zgodi ob prekinitvi

Če sistem med aktivno vožnjo prekine proces, aplikacija ohrani že shranjene GPS točke in ob naslednjem odprtju uporabniku pokaže recovery možnost za nadaljevanje ali zaključek vožnje. Med prekinitvijo manjkajočega odseka ni mogoče zanesljivo rekonstruirati.

## Predlog 30-sekundnega demo videa

1. Odpri Kilometrino na zavihku Domov.
2. Pokaži vnesen namen poti.
3. Pritisni »Začni vožnjo« in odobri natančno lokacijo, če je prvi zagon.
4. Pokaži aktivno kartico »Vožnja v teku« ter GPS status.
5. Odpri notification shade in pokaži trajno obvestilo »Kilometrina se beleži«.
6. Vrni se v aplikacijo oziroma pokaži, da obvestilo ostane med uporabo druge aplikacije.
7. Pritisni »Končaj vožnjo«.
8. Pokaži, da trajno GPS obvestilo izgine in se zaključena vožnja prikaže v zgodovini.

## Precise location utemeljitev

Use case: **Live tracking / continuous measurement**.

`ACCESS_COARSE_LOCATION` ni dovolj natančen za izračun dejansko prevožene razdalje po zaporednih GPS odsekih. Enkratni location picker oziroma location button prav tako ne omogoča neprekinjenega merjenja poti med uporabniško sproženo vožnjo. Natančna lokacija se ne uporablja za oglase, analitiko ali prodajo podatkov.

## Store listing uskladitev

Opis aplikacije mora jasno omenjati GPS sledenje dejanske poti in dejstvo, da sledenje med aktivno vožnjo lahko deluje z ugasnjenim zaslonom. To je že vključeno v `play-store/listing-sl.md`.

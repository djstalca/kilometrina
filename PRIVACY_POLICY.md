# Politika zasebnosti – Kilometrina

**Velja od:** 7. september 2026  
**Aplikacija:** Kilometrina  
**Razvijalec:** Luka Benčina

Kilometrina je aplikacija za lokalno beleženje službenih voženj, kilometrine in povezanih stroškov. Zasnovana je tako, da osebni podatki ostanejo na uporabnikovi napravi in se ne pošiljajo razvijalcu.

## Katere podatke aplikacija uporablja

Aplikacija lahko glede na vključene funkcije uporablja oziroma lokalno shrani:

- natančno lokacijo med aktivnim beleženjem vožnje,
- GPS točke in izračunano razdaljo vožnje,
- čas začetka in konca vožnje,
- naslove oziroma uporabniško poimenovane lokacije,
- namen poti,
- podatke o vozilu in registrski oznaki,
- parkirnine, cestnine in izračune povračila,
- ime voznika in podjetja, če ju uporabnik vnese,
- podatke o prepoznani aktivnosti vožnje, če uporabnik prostovoljno vključi funkcijo pametnega zaznavanja vožnje.

## Lokacija

Natančna lokacija je potrebna za glavno funkcijo aplikacije: merjenje dejansko prevožene razdalje. Beleženje se začne šele po dejanju uporabnika. Med aktivno vožnjo aplikacija uporablja Android foreground service z vidnim trajnim obvestilom, zato lahko GPS beleženje nadaljuje tudi, ko je zaslon ugasnjen ali je druga aplikacija v ospredju.

Aplikacija ne uporablja dovoljenja `ACCESS_BACKGROUND_LOCATION` in lokacije ne uporablja za oglaševanje, profiliranje ali analitiko.

## Pametna zaznava vožnje

Pametna zaznava vožnje je privzeto izključena. Če jo uporabnik vključi, aplikacija uporablja Androidovo dovoljenje za prepoznavanje aktivnosti, da zazna verjeten začetek oziroma konec vožnje. Zaznava sama nikoli ne ustvari službene vožnje; uporabniku le prikaže predlog oziroma obvestilo.

## Kam se podatki shranjujejo

Podatki aplikacije se shranjujejo lokalno v zasebnem prostoru aplikacije na napravi. Aplikacija nima lastnega strežnika, uporabniškega računa, oglasnega SDK-ja ali analitičnega SDK-ja.

Aplikacija podpira Android Auto Backup in prenos podatkov med napravami, če ima uporabnik te sistemske funkcije omogočene. Upravljanje in infrastrukturo takšnega sistemskega backupa zagotavlja Android oziroma ponudnik uporabnikovega sistema/računa, ne razvijalec aplikacije Kilometrina.

Uporabnik lahko tudi sam ustvari JSON varnostno kopijo in izbere mesto, kamor jo želi shraniti. Takšna datoteka lahko vsebuje zgodovino GPS lokacij, zato jo mora uporabnik varovati kot občutljiv dokument.

## Deljenje in prodaja podatkov

Razvijalec aplikacije ne prejema, ne prodaja in ne deli uporabnikovih lokacij, zgodovine voženj ali drugih podatkov iz aplikacije.

## Hramba in izbris

Podatki ostanejo na napravi, dokler jih uporabnik ne izbriše v aplikaciji, ne odstrani podatkov aplikacije oziroma ne odstrani aplikacije. Ročne varnostne kopije ostanejo na lokaciji, ki jo je izbral uporabnik, in jih mora uporabnik izbrisati sam.

## Dovoljenja

Aplikacija lahko zahteva naslednja Android dovoljenja:

- **natančna/približna lokacija:** za GPS merjenje poti,
- **obvestila:** za prikaz aktivnega GPS sledenja in opcijskih predlogov vožnje,
- **prepoznavanje aktivnosti:** samo, če uporabnik vključi pametno zaznavo vožnje,
- **foreground service – location:** za uporabniško sproženo neprekinjeno merjenje poti.

## Otroci

Aplikacija ni namenjena otrokom in ni zasnovana kot izdelek za otroke.

## Spremembe politike

Ob večjih spremembah načina obdelave podatkov bo ta dokument posodobljen skupaj z datumom veljavnosti.

## Kontakt

Za vprašanja glede zasebnosti uporabite kontaktne podatke razvijalca, navedene na strani aplikacije Kilometrina v Google Play.

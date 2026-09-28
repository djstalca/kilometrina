# Google Play Data Safety – delovni list

Ta dokument opisuje dejansko vedenje aplikacije Kilometrina 1.1.0 in služi kot osnova pri izpolnjevanju obrazca Data safety v Play Console.

## Povzetek

- Aplikacija nima lastnega backend strežnika.
- Aplikacija nima analitike, oglasov ali crash-reporting SDK-ja, ki bi pošiljal podatke razvijalcu.
- Lokacija, GPS točke, vožnje, stroški, vozila in profil se obdelujejo lokalno.
- Lokalni crash zapis ostane na napravi in se ne pošilja razvijalcu.
- Ročni JSON backup ustvari uporabnik in sam izbere cilj datoteke.
- Fotografije/PDF priloge k vožnjam se hranijo lokalno in so vključene v backup oziroma mesečni paket samo po uporabnikovem dejanju.
- Opcijsko branje koledarja se uporablja samo lokalno za predlog namena poti.
- Android Auto Backup/device transfer upravlja operacijski sistem oziroma uporabnikov sistemski ponudnik backupa.

## Podatki, ki jih aplikacija uporablja lokalno

### Location

**Precise location:** DA – uporablja se za merjenje dejansko prevožene GPS poti.  
**Approximate location:** Android jo lahko ponudi skupaj z location permission, vendar aplikacija za zanesljivo kilometrino zahteva natančno lokacijo.  
**Poslano razvijalcu:** NE.  
**Deljeno s tretjimi osebami s strani aplikacije:** NE.

### App activity / Physical activity

**Activity recognition:** samo če uporabnik prostovoljno vključi funkcijo Pametna zaznava vožnje.  
Uporablja se samo za lokalni predlog »verjetno si začel/končal vožnjo«.  
**Poslano razvijalcu:** NE.

### Personal info

Uporabnik lahko lokalno vnese ime voznika in podjetje za poročila.  
**Poslano razvijalcu:** NE.

### Financial info

Aplikacija lokalno hrani zneske kilometrine, parkirnin in cestnin. Ne obdeluje plačilnih kartic, bančnih računov ali transakcij.  
**Poslano razvijalcu:** NE.

## Collect / Share interpretacija

Po trenutni implementaciji aplikacija ne prenaša zgornjih uporabniških podatkov na strežnik razvijalca. Pri izpolnjevanju Play Console obrazca preveri aktualno Google definicijo »collected« in morebitne izjeme za obdelavo izključno na napravi ter sistemske backup storitve.

## Security practices

- Podatki so v zasebnem app storage-u.
- `android:allowBackup=true` je namenoma omogočen za Android Auto Backup/device transfer.
- Uporabnik lahko izvozi lastni JSON backup; ta vsebuje lokacijsko zgodovino in ga mora uporabnik varovati.
- Ni oglasnega ID-ja.
- Ni lastnega računa ali prijave.
- Ni prodaje podatkov.

## Brisanje

Ker aplikacija nima uporabniškega računa ali strežniške kopije pri razvijalcu, strežniška zahteva za izbris ni potrebna. Uporabnik lahko:

- izbriše posamezne vožnje v aplikaciji,
- izbriše priljubljene lokacije in vozila,
- počisti podatke aplikacije v Android nastavitvah,
- odstrani aplikacijo,
- sam izbriše ročno ustvarjene backup datoteke.

## Pred oddajo

V Play Console še enkrat preveri obrazec glede na točno verzijo AAB-ja, ker Google spreminja vprašanja in definicije. Ta dokument ne nadomešča aktualnega Data Safety obrazca.


### Calendar

**Koledarski dogodki:** samo če uporabnik prostovoljno vključi koledarske predloge in odobri READ_CALENDAR. Aplikacija lokalno uporabi naslov časovno ustreznega dogodka kot možen namen službene poti.  
**Poslano razvijalcu:** NE.

### Files / attachments

Uporabnik lahko izbere fotografijo ali PDF računa in jo pripne vožnji. Kopija ostane lokalno. Uporabnik lahko sam ustvari ZIP mesečni paket ali ga deli prek sistemskega Android share dialoga.  
**Samodejno poslano razvijalcu:** NE.

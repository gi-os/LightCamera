## LightCamera v2.40 — Reading the words in a photograph

Open a photograph on the roll and there is a **TEXT** button in the chrome next to the star. Press
it and the words in the picture come up in a sheet: a business card, a menu, a receipt, a poster,
a page of a book you do not want to type out.

### It is the QR sheet, and that is the point

The addresses, phone numbers and links found on the page arrive in the same sheet a scanned QR
code arrives in, with the same verbs. That is not a resemblance — it is the same code. Everything
that decides what a payload *is* already lives in `qr/Codes.kt`, and everything that opens or
copies one already lives in `qr/Handoff.kt`, both written and tested for QR mode. The new part
only has to lift the scraps off the page and hand them over in the shape a code would have had: a
photographed number becomes `tel:`, an address becomes `mailto:`, a bare host becomes `https://`.
After that one line, a number off a business card and a number inside a QR code are the same
thing, so they should not land in two different screens.

Several things usually come off one page, so the top of the sheet is a short list and the actions
follow whichever row is selected. OPEN reads CALL for a number and WRITE for an address, because
it is worth saying what a button will do before it does it. Beneath the list is the whole page,
with the line breaks exactly where the recogniser found them — a receipt's line breaks are its
only structure, and re-flowing them into prose throws away the thing that makes the copy useful.

### Nothing is spell-corrected, deliberately

Recognisers confuse `O` with `0` and `l` with `1`, and those two substitutions are most of the
difference between a company's website and a domain somebody bought to catch the typo. Guessing
would make this most dangerous in exactly the case where it looks most useful — a printed URL. So
the sheet shows the reading rather than a tidied version of it, and a selected row displays the
characters as they came off the page, not the address they were turned into. Check it against the
thing in your other hand.

Two things the tests caught before the phone did: a number written `(555) 013-4567` was losing its
opening bracket, and `2019-2024` was being offered as a phone number. Eight digits in two groups
is also a real local number, so length cannot separate them — there is now a narrow guard for the
one collision that turns up on printed pages, and it does not touch numbers in general.

### Why ML Kit here and ZXing for QR

`qr/QrAnalyzer` says plainly that ML Kit is unusable on this phone, and for the reader it names,
that is still true: the unbundled models come through Play Services, which LightOS does not have,
so they bind and never answer. Text recognition has a second artifact where the model ships inside
the APK, and that is what this uses. QR stays on ZXing because ZXing is 500 kB and already worked;
there is no comparable pure-Java text recogniser worth shipping. **The APK is a few megabytes
larger for it**, which is the honest cost of the feature.

### Not on the live viewfinder

Reading is a button you press on a photograph you already took, not something that happens to
every frame. Most pictures have no writing in them, the recogniser costs a few hundred
milliseconds, and a viewfinder running one continuously would be spending the battery answering a
question nobody asked. A QR code is the opposite — you point at it and want it acted on within a
second — which is why that one is live and this one is not.

The reading is thrown away when you turn to the next photograph. Words from the last picture over
this one would be worse than nothing, because they would look correct.

### Known

Full mode is on in this app, and ML Kit finds its own implementation by class name through a
registrar that nothing in our code refers to. There are new keep rules for it. If **TEXT** answers
"No text in this one" on a photograph that plainly has words in it, that is a missing keep rule
rather than a bad photograph — shake the phone and the class name will be in the report.

## Roll v2.16 — Simple, and the camera opens on it

**Simple**

The camera now opens on Simple every time, and the mode slot reads SIMPLE · PRO · VIDEO · SELFIE. Simple
takes a photograph and does nothing else: no filter, no crop, no date, no self timer, no wheel, no grid.
Everything Roll is proud of lives in Pro, exactly as it was.

**Why it is quick, in the order that matters**

1. **There is nothing to process.** With no filter, no crop and no date, the JPEG the ISP produced *is* the
   file — it is written whole, no decode, no re-encode, EXIF intact. A filtered 12MP shot has to be decoded
   into a 48MB bitmap, pushed through a shader and encoded again; not doing that is most of a second.
2. **Twelve megapixels, not fifty.** Reading out and encoding the full sensor is most of the ISP's second
   on its own, and each step down is roughly a halving. 12MP is four times the largest print anybody makes
   from a phone.
3. **No waiting for focus.** Continuous AF is already converged on whatever you are pointing at, so a press
   means take it now. The two-stage focus-and-lock shutter is a Pro feature.

Quality is not what is traded away — the file is the ISP's own output rather than something this app
re-compressed, which if anything is the *better* image of the two.

The size is set for the duration of the shot and put back afterwards, so passing through Simple never
quietly rewrites a Pro setting. And Simple is not remembered between launches on purpose: whatever you
were doing in Pro last night, pressing the camera key today means you want to take a photograph.

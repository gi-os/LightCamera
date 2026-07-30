## Roll v2.32 — the send picker is back, and now it asks before it sends

**The picker was deleted from main by mistake.** Commit 7253955 was written from a checkout
taken before the picker landed, so it wrote the old files back over it — all of send/, the
sheet, the roll's multi-select, the contacts permission, the recents. v2.29 through v2.31 shipped
without any of it. This restores it onto current main; Dither 32, the camera-key fix and the
Purikura persistence are all untouched.

**Choosing somebody no longer sends immediately**

Tapping a name used to fire the photograph straight off, which put the one irreversible step in
the flow behind the same gesture as scrolling past a name — and there is no unsend. Now a tap
chooses, and a bar appears under the header saying who it is going to, which number it is going
to, and how many photographs. Send or Cancel from there.

The number matters more than it looks: a contact with a mobile and an old landline is exactly
the case a confirmation step earns its keep, and that line is what catches it.

The list stays live underneath, so picking somebody else moves the choice rather than making you
cancel first, and the chosen row carries the same mark a picked frame does in the roll. Back
steps out of the choice before it steps out of the picker.


## Roll v2.38 — send a photo to a group chat

**What's new:** the send picker can now send a photo to a group chat, not just to one person.

### How it works

Tap send on a photo and you get Roll's own picker — recent recipients, then your contacts,
searchable. Group chats now sit at the top of that list, above the contacts: five of them at
rest, and all of them once you start typing a name. Pick one and the photo lands in that thread.

Long-press a frame in the roll to select several photos and send them together, as before.

Two details you may notice:

- **Typing a digit hides the groups.** In the contacts half of the list, digits search phone
  numbers. A group has no number, so a query with a digit in it can only mean a contact.
- **Groups don't show up in Recents.** They already sort themselves by last activity, which is a
  better signal than a six-slot recents list — and every group in recents would push a contact
  out of it.

### What you need

**[LightChat](https://github.com/gi-os/LightChat) v1.2 or newer.** Roll reads your groups from
LightChat and sends to them through LightChat, so both halves of this feature ship in that
release. Without LightChat, or on an older build, there are simply no groups in the list and the
picker is exactly what it was before. Nothing else changes and nothing breaks.

### Why this needed a release at all

Sending to a person is easy: a contact has a phone number, and a number goes into a standard
Android intent that any messaging app understands. A group chat has neither. It is a thread
living on your Mac, identified by a string like `iMessage;+;chat684…` — no contact row, no phone
number to address it with. So there was nothing for the picker to show and nothing in the intent
to carry it. Sending to a group used to mean handing the photos to LightChat with no destination
attached, then finding the right thread by hand.

Two things fixed that:

1. **LightChat now publishes its group threads,** read-only, for other apps to read: name,
   member count, last activity, newest first, with the names already resolved through the
   BlueBubbles address book — so a group reads the same in Roll as it does in LightChat.
2. **Roll sends to a group down its own path.** The group's id travels in a `chat_guid` extra
   addressed to LightChat and nowhere else, with **no fallback to other apps**. Any other app
   would ignore that extra and drop your photos into whichever thread it happened to have open,
   which is worse than not sending at all. If LightChat isn't installed, the picker says so
   rather than sending your photo somewhere you didn't pick.

Sending to a *person* is untouched — still a standard `ACTION_SEND` with the address attached,
still able to fall through to any messaging app that handles it.

### Also in this release

Documentation only, no code: the README is rewritten for someone who has never seen the app —
screenshots, a step-by-step sideload starting from enabling USB debugging, what Roll does that
the stock camera doesn't, and the questions worth asking before installing an unofficial APK.

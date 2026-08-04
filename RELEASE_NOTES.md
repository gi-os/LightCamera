## Roll v2.38 — the send picker can reach a group

*Docs only in this build: the README is now written for someone who has never seen the app —
screenshots, a step-by-step sideload, an honest list of what it does that the stock camera
doesn't, and the questions people actually ask before installing something unofficial on their
phone. No code changed.*


**"Who is this photograph for?" had one answer it could never give.** Groups now sit at the
top of the send picker, above your contacts, and a photograph sent to one lands in the actual
thread.

The picker owns the address book on purpose — the system chooser asks which app, which on a
phone with three of them is a question with an obvious answer wrapped in a grid of icons. But
an address book knows about people, and a group iMessage is not a person: it is a chat room
living on the Mac, identified by a guid like `iMessage;+;chat684…`, with no phone number and no
contact row. There was nothing in the picker to select and nothing in the intent to carry it,
so sending to a group meant handing the photographs to LightChat unaddressed and finding the
thread by hand.

LightChat now publishes its group conversations read-only, and Roll reads them: name, member
count, last activity, ordered newest first, with the names already resolved through the
BlueBubbles address book so a group reads here the same as it does over there. Five at rest —
they sit above the contact list and each one is a contact pushed off the screen — and every
match once you start typing. A query with a digit in it excludes them all, because in the
contact half digits search phone numbers, and a group has no number to search.

Sending picks its own path. A person goes out as `ACTION_SEND` with the AOSP `address` extra,
exactly as before, and can fall through to any messaging app that understands it. A group goes
to LightChat and nowhere else, with the guid in a `chat_guid` extra, and **there is no chooser
fallback** — every other receiver would ignore the extra and drop the photographs into a thread
the user never chose, which is the same lie the address path was fixed for and worse, because a
group has no address for the receiving app to fall back on. If LightChat isn't there to take
it, the picker says so and nothing happens.

Groups aren't recorded as recents. Recents are an address-book idea and groups already sort by
their own last activity, which is a better signal and doesn't spend one of six slots.

On a phone without LightChat — or with a build older than v1.2 — there are no groups and this
screen is exactly what it was.

**Needs LightChat v1.2 or newer.** The provider Roll reads and the extra it sends both land in
that release.

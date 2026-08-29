# Contributing

Adding a bank means creating one directory: a build file naming it, an icon, and
a class that reads the mail. No registration and no list to edit.

**The trade this makes, so you know it up front: a bank ships with the app.**
Sources used to be separate APKs, published to their own repository and
installed on their own. Nothing is published now, so a new bank reaches a user
in the next app release and not before. That costs a wait and buys the deletion
of an installer, a trust store, a signature check, an update notifier and a
second repository — for four sources and one author, that was machinery guarding
against a problem this project does not have.

## Add a source

```
source/lib/id/jago/
    build.gradle.kts
    src/main/res/mipmap-<density>/ic_launcher.png
    src/main/kotlin/dev/achmad/finbox/source/id/jago/Jago.kt
    src/test/kotlin/dev/achmad/finbox/source/id/jago/JagoTest.kt
    src/test/resources/jago/*.txt
```

`id` is the ISO 3166-1 alpha-2 country the bank operates in, and `jago` is the
source's id. The directory is how a source is found: its Gradle path, its
namespace and its resource prefix all come from where it sits, so a bank that
moves country moves directory and nothing else has to agree. Nothing registers
it — every directory under `source/lib/<country>/` is a module.

**1. Declare it** in `build.gradle.kts`:

```kotlin
plugins {
    id("finbox.source")
}

source {
    id = "jago"
    name = "Bank Jago"
}
```

`finbox.source` is what makes the module an Android library and wires the rest.
`id` and `name` are both required, and the build fails without them. `id` has to match the
directory name, which the build checks: the directory is how a source is found,
the id is what the database stores on every transaction and in `account_source`,
and two words for one thing is how they drift. Renaming it costs a reimport.

Everything else about being a source — the namespace, the resource prefix, the
icon, the processor, the test dependencies — is the plugin's job, so the rest of
this file is only ever what this one bank needs on top.

**2. Add the icon** at `src/main/res/mipmap-<density>/ic_launcher.png`. This is
the ordinary launcher-icon layout, and the same one an extension in Tachiyomi's
`extensions-source` uses, so a set copies in unchanged. It is required: a source
with no icon fails the build rather than showing a blank row.

You never reference it, and it is never read where you put it. Every source's
resources merge into one app and `ic_launcher` is the name the app's own
launcher icon already answers to — an app resource beats a library one, so four
sources reading it would every one of them get finbox's icon. The build copies
yours to `drawable-<density>/<id>_icon.png` and the generated registry points at
that. Anything *else* you put under `src/main/res/` has to carry the `<id>_`
prefix, which AGP warns about when it does not.

**3. Write the class:**

```kotlin
package dev.achmad.finbox.source.id.jago

@SourceEntrypoint
class Jago : EmailSource {

    override val query = EmailQuery.from("noreply@jago.com")

    override suspend fun parse(email: Email): List<ParsedTransaction> { /* ... */ }
}
```

That is the registration. A KSP processor collects every `@SourceEntrypoint`
into the list the app reads, across module boundaries, so there is no list to
edit and no way to write a source that quietly never runs. Exactly one per
module — two would both be valid and the module would really be two modules.

The class carries no id, no name and no icon. Those are the build file's, and a
class that also declared them would be a second place for them to be wrong. What
is left is the reading of one bank's mail, which is the only part that needs a
person.

**What you implement is what you declare.** `EmailSource` carries
`@SourceProvider`, which is how a source kind says the app has something that can
drive it. Capabilities are worked out by asking the class, not by reading a list
you wrote, and the build fails if a `@SourceEntrypoint` implements no
`@SourceProvider` interface — a source the app could never ask for anything is a
build error, not a quiet no-op.

Email is the only kind so far. When a bank publishes receipts some other way,
that is one more interface extending `Source`, annotated `@SourceProvider`, and
implementing it is the whole of declaring it.

### The two members

`query` is what the app asks Gmail for. Narrow it to the sender the bank
notifies from, and never put dates in it — the app adds its own window. It only
decides what gets *downloaded*: fetching one message costs twenty quota units
against five for listing five hundred ids, so a whole mailbox is expensive.
`EmailQuery.raw()` takes anything Gmail's search box accepts.

**You must name senders.** There is no way to ask for everything, deliberately:
every enabled source's query is merged into one search per account, so a single
source opting out of narrowing spends every other source's user their Gmail
quota, and nobody can tell which source did it. If a bank really does send from
unpredictable addresses, name the ones you know and let `parse()` disown the
rest — which is the real safety net, and always was.

`parse()` reads the email, or returns an empty list. Empty is how you disown
one, and it covers both cases the app cares about: mail from another bank, and
this bank's own statements, OTPs and promotions, which arrive from the same
address as its receipts. Guard on something a receipt always has and an advert
never does — a reference number, a summary table — and return empty again if the
amount cannot be read. The app then offers the email to the next source, so a
wrong guess costs nothing but a wasted call.

`amount`, `currency`, `date` and `direction` are required: a transaction missing
any of them is not worth storing, so return nothing instead. The rest are
optional because banks genuinely differ. When the receipt states no time of its
own, pass `email.date`.

**`amount` is in minor units** — cents, sen — always positive. **If you are
writing an Indonesian source, do nothing about this and do not divide by 100.**
ISO 4217 assigns the rupiah two minor digits for a sen that has not priced
anything in decades, so finbox treats IDR as having none: Rp13.000 is `13000`,
which is what `receipt.amount()` already returns. Minor units exist so that a
currency with real cents can be represented at all — SGD 12.50 is `1250` and has
no other honest form.

`direction` is `INCOMING` or `OUTGOING`, and it is the only thing the app itself
knows about how the money moved. Read it from the bank's own wording.

#### Two slots, two different jobs

Beyond the amount and the direction, most receipts state two things, and each
has exactly one home:

| The receipt says | Goes in | For example |
|---|---|---|
| who the other side was | `merchant` | "Nama Merchant", "Penerima", a partner |
| what was typed about *this* one | `description` | "Catatan: kopi" |

`description` is the one that goes wrong. The test is whether two transactions
of the same kind could carry different text there. **Never put the email subject
in it, and never the bank's word for the kind of movement — "QRIS Bayar"
describes the template, not this transaction, and a phrase repeated on every
receipt tells the app nothing.**

That is not a tidiness rule. The app reads `merchant` and `description` as its
evidence that a transaction's purpose is knowable at all, and it deliberately
knows nothing about any bank, so it cannot tell your boilerplate from a real
merchant name. A subject repeated on four hundred receipts arrives looking like
four hundred descriptions, and rows with nothing to go on get categorized by
guesswork instead of being marked unknown.

Leave a field null when the receipt does not state it. A gap is a fact about the
receipt and the app handles it; something invented to fill the gap is not.

### What belongs here, and what doesn't

A source holds the knowledge of one bank's emails. It never fetches, never
schedules, never touches a token or an HTTP client — the app owns all of that,
and hands over an `Email`. `:source:core` is a plain Kotlin module with no
Android on its classpath, so most of that is a compiler fact rather than a rule.

Conversely the app holds no opinion about parsing. It passes the body exactly as
it arrived, html included; turning markup into readable lines is your call, and
`Receipt` does it.

## Use the receipt helper

`source/core`'s `util/Receipt.kt` is shared by every source. It covers what banks
have in common, and nothing that belongs to one of them:

```kotlin
val receipt = Receipt.of(email)     // flattens html to one line per row

receipt.field("Nomor Referensi", "No. Ref")   // label → value, same line or next
receipt.amount("Nominal")                     // "Rp 1.151.800" → 1151800
receipt.date("Tanggal Transaksi")             // honours a stated WIB/WITA/WIT
receipt.splitDate()                           // a day and a clock on two rows (BNI, Mandiri)
receipt.statedAmount()                        // the first "Rp …" in prose
```

Classifying is not in there, deliberately. Which way the money went comes from
the bank's own wording, so it belongs in your source as a `when` over that
wording. A shared lookup table would have to know every bank's phrasing, and
would quietly mis-file the first one it didn't.

Two layouts are already handled: label and value on one line (BRI, BNI, Mandiri)
and on two, with or without a colon (Jago). If a new bank breaks something in
`Receipt`, fix it there — that is why it is shared and not copied into each
source. And "shared" means shared: one bank's vocabulary in it is one every
other source has to carry and none of them can correct.

## Test

```bash
./gradlew :source:lib:id:jago:test
```

Plain JUnit on the JVM, no Android, no instrumentation.

Save a real email as `src/test/resources/<id>/<case>.txt`, flattened the way the
app hands it over — one line per table row — **with names, account numbers and
card numbers redacted**, and assert the parsed amount, date, direction and
merchant. `BriTest` and `JagoTest` are worth copying. Cover each distinct layout
the bank sends, and one email that must *not* be claimed.

The fixtures are examples of a layout, not a list of what the source supports,
and redacting them is not optional: the app stores real email bodies on the
device and none of that, nor anything derived from it, is committed here.

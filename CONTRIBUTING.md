# Contributing

Adding a bank means writing one class and adding one line to a list. It lives in
`extension/`, a plain Kotlin module with no Android in it, so `./gradlew
:extension:test` is the whole loop.

**The trade this makes, so you know it up front: a bank now ships with the app.**
Extensions used to be separate APKs, published to their own repository and
installed on their own. Nothing is published now, so a new bank reaches a user
in the next app release and not before. That costs a wait and buys the deletion
of an installer, a trust store, a signature check, an update notifier and a
second repository — for four extensions and one author, that was machinery
guarding against a problem this project does not have.

## Add an extension

**1. Write the class** in `extension/src/main/kotlin/dev/achmad/finbox/extension/<id>/`,
named after the bank and nothing else:

```kotlin
package dev.achmad.finbox.extension.jago

class Jago : EmailSource {

    override val query = EmailQuery.from("noreply@jago.com")

    override suspend fun parse(email: Email): List<ParsedTransaction> { /* ... */ }
}
```

A package per bank, so a bank that grows a helper puts it beside its own reader
instead of into a namespace all four share.

**2. Register it** in `Extensions.kt`:

```kotlin
Extension(id = "jago", name = "Bank Jago", source = Jago()),
```

The `id` is short, lowercase, and chosen once: it names the package, the test
resources and the icon, and it is stored on every transaction and in
`account_extension`. Renaming one costs a reimport.

**3. Add an icon** at `app/src/main/res/drawable-<density>/ic_extension_<id>.png`,
and a branch in `extensionIcon()`. The `when` is deliberate — a lookup by name
is reflection R8 cannot see through, and a typo becomes a blank row at runtime.

**What you implement is what you declare.** `EmailSource` is one of a set of
interfaces the app recognises, and it works out what your extension can read by
asking the class, not by reading a list you wrote. Email is the only one so far.
When a bank publishes receipts some other way, that is another interface to
implement, and implementing it is the whole of declaring it.

### The two members

`query` is what the app asks Gmail for. Narrow it to the sender the bank
notifies from, and never put dates in it — the app adds its own window. It only
decides what gets *downloaded*: fetching one message costs twenty quota units
against five for listing five hundred ids, so a whole mailbox is expensive.
`EmailQuery.raw()` takes anything Gmail's search box accepts.

**You must name senders.** There is no way to ask for everything, deliberately:
every enabled extension's query is merged into one search per account, so a
single extension opting out of narrowing spends every other extension's user
their Gmail quota, and nobody can tell which extension did it. If a bank really
does send from unpredictable addresses, name the ones you know and let `parse()`
disown the rest — which is the real safety net, and always was.

`parse()` reads the email, or returns an empty list. Empty is how you disown
one, and it covers both cases the app cares about: mail from another bank, and
this bank's own statements, OTPs and promotions, which arrive from the same
address as its receipts. Guard on something a receipt always has and an advert
never does — a reference number, a summary table — and return empty again if the
amount cannot be read. The app then offers the email to the next extension, so a
wrong guess costs nothing but a wasted call.

`amount`, `currency`, `date` and `direction` are required: a transaction missing
any of them is not worth storing, so return nothing instead. The rest are
optional because banks genuinely differ. When the receipt states no time of its
own, pass `email.date`.

**`amount` is in minor units** — cents, sen — always positive. **If you are
writing an Indonesian extension, do nothing about this and do not divide by
100.** ISO 4217 assigns the rupiah two minor digits for a sen that has not
priced anything in decades, so finbox treats IDR as having none: Rp13.000 is
`13000`, which is what `receipt.amount()` already returns. Minor units exist so
that a currency with real cents can be represented at all — SGD 12.50 is `1250`
and has no other honest form.

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

An extension holds the knowledge of one bank's emails. It never fetches, never
schedules, never touches a token or an HTTP client — the app owns all of that,
and hands over an `Email`. The module is plain Kotlin on purpose: there is no
Android on its classpath, so this is a compiler fact rather than a rule.

Conversely the app holds no opinion about parsing. It passes the body exactly as
it arrived, html included; turning markup into readable lines is your call, and
`lib/` does it.

## Use the receipt library

`lib/Receipt.kt` is shared by every extension. It covers what banks have in
common, and nothing that belongs to one of them:

```kotlin
val receipt = Receipt.of(email)     // flattens html to one line per row

receipt.field("Nomor Referensi", "No. Ref")   // label → value, same line or next
receipt.amount("Nominal")                     // "Rp 1.151.800" → 1151800
receipt.date("Tanggal Transaksi")             // honours a stated WIB/WITA/WIT
receipt.splitDate()                           // a day and a clock on two rows (BNI, Mandiri)
receipt.statedAmount()                        // the first "Rp …" in prose
```

Classifying is not in there, deliberately. Which way the money went comes from
the bank's own wording, so it belongs in your extension as a `when` over that
wording. A shared lookup table would have to know every bank's phrasing, and
would quietly mis-file the first one it didn't.

Two layouts are already handled: label and value on one line (BRI, BNI, Mandiri)
and on two, with or without a colon (Jago). If a new bank breaks something here,
fix it here — that is why it is a library and not copied into each extension.

And "shared" means shared: one bank's vocabulary in `lib/` is one every other
extension has to carry and none of them can correct.

## Test

```bash
./gradlew :extension:test
```

Save a real email as `extension/src/test/resources/<id>/<case>.txt`, flattened
the way the app hands it over — one line per table row — **with names, account
numbers and card numbers redacted**, and assert the parsed amount, date,
direction and merchant. `BriTest` and `JagoTest` are worth copying. Cover each
distinct layout the bank sends, and one email that must *not* be claimed.

The fixtures are examples of a layout, not a list of what the extension
supports, and redacting them is not optional: the app stores real email bodies
on the device and none of that, nor anything derived from it, is committed here.

Plain JUnit on the JVM, no Android, no instrumentation.

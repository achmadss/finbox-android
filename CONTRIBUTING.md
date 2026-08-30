# Contributing

## Add a source

```
source/lib/id/jago/
    build.gradle.kts
    src/main/res/mipmap-<density>/ic_launcher.png
    src/main/kotlin/dev/achmad/finbox/source/id/jago/Jago.kt
    src/test/kotlin/dev/achmad/finbox/source/id/jago/JagoTest.kt
    src/test/emails/*.txt
```

`id` is the ISO 3166-1 alpha-2 country the bank operates in, and `jago` is the
source's id. Every directory under `source/lib/<country>/` is a module, and
nothing registers it.

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

The `id` must match the directory name, and the build checks this. Everything
else about being a source is the plugin's job.

**2. Add the icon** at `src/main/res/mipmap-<density>/ic_launcher.png`. The
icon is required, and the build copies it to `drawable-<density>/<id>_icon.png`
for the app. Anything else under `src/main/res/` must carry the `<id>_` prefix.

**3. Write the class:**

```kotlin
package dev.achmad.finbox.source.id.jago

@SourceEntrypoint
class Jago : EmailSource {

    override val query = EmailQuery.from("noreply@jago.com")

    override suspend fun parse(email: Email): List<ParsedTransaction> { /* ... */ }
}
```

A KSP processor collects every `@SourceEntrypoint` into the list the app
reads. Use exactly one per module.

### `query`

Narrow `query` to the senders the bank notifies from, and never include dates
in it. The app adds its own window. You must name senders, because the app
merges every enabled source's query into one Gmail search per account.
`EmailQuery.raw()` takes anything Gmail's search box accepts.

### `parse()`

In `parse()`, read the email and return the transactions you recognize, or
return an empty list to disown one. An empty list covers mail from other banks
and this bank's own statements, OTPs, and promotions. Match on something a
receipt always has and an ad never does, such as a reference number or a
summary table.

- `amount`, `currency`, `date`, and `direction` are required. If any is
  missing, return nothing.
- `amount` is in minor units and always positive. **For Indonesian sources, do
  not divide by 100.** finbox treats IDR as having no minor digits, so
  Rp13.000 is `13000`, which is what `receipt.amount()` returns.
- `direction` is `INCOMING` or `OUTGOING`. Read it from the bank's own
  wording.
- Put the counterparty in `merchant`. Put anything the user typed about this
  one transaction in `description`. Never put the email subject or the bank's
  word for the kind of movement ("QRIS Bayar") in either. Leave a field null
  when the receipt does not state it.
- When the receipt states no time of its own, pass `email.date`.

A source never fetches, schedules, or touches a token. The app hands over an
`Email`, and the body arrives exactly as it arrived, html included.

## Use the receipt helper

The `Receipt` helper in `source/core` is shared by every source:

```kotlin
val receipt = Receipt.of(email)     // flattens html to one line per row

receipt.field("Nomor Referensi", "No. Ref")   // label to value, same line or next
receipt.amount("Nominal")                     // "Rp 1.151.800" returns 1151800
receipt.date("Tanggal Transaksi")             // honors a stated WIB/WITA/WIT
receipt.splitDate()                           // a day and a clock on two rows (BNI, Mandiri)
receipt.statedAmount()                        // the first "Rp" amount in prose
```

The helper does not classify transactions. Direction comes from the bank's
wording, so write it in your source as a `when` over that wording. If a new
bank breaks something in `Receipt`, fix it there, but keep one bank's
vocabulary out of it.

## Test

To run the tests:

```bash
./gradlew :source:lib:id:jago:test
```

Tests run as plain JUnit on the JVM. Save a real email as
`src/test/emails/<case>.txt`, flattened to one line per table row, **with
names, account numbers, and card numbers redacted**. Redaction is not
optional. `BriTest` and `JagoTest` are worth copying. Cover each distinct
layout the bank sends, plus one email that the source must not claim.

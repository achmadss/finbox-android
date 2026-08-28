# finbox

A personal spending ledger, fed by the receipts your bank already emails you.

finbox reads a Gmail mailbox, hands each email to the sources it ships with,
and records what they recognise as transactions. No bank credentials, no screen
scraping, no SMS permissions — just the notification mails you already get,
turned into a month-by-month view of what went out and what came in.

It is a ledger, not a parsing dashboard. Emails nothing recognises are dropped
quietly; there is no review queue to work through.

## How it works

1. **Connect a mailbox.** Google OAuth, `gmail.readonly`, no password stored.
2. **Switch on the banks you use.** Every source ships inside the app and is on
   by default; the sources screen is a list with a switch each.
3. **Import.** The first sync walks the mailbox for mail the enabled sources
   claim as theirs. Every sync after that asks Gmail only what changed, so a
   refresh with nothing new costs a single API call.
4. **Read the ledger.** One month at a time, out / in / net at the top,
   transactions grouped by day. Multiple mailboxes merge into one ledger.

Parsing lives entirely in `source/`. The app fetches mail and keeps the ledger;
it holds no knowledge of any bank's email format.

## Banks it reads

| Provider | Emails it reads |
|---|---|
| Bank BNI | wondr receipts — QRIS, transfers, TapCash top ups |
| Bank BRI | BRImo receipts — QRIS, transfers, BRIZZI top ups |
| Bank Jago | payments, transfers, Jago Partner, debit card purchases |
| Bank Mandiri | Livin' receipts — QR payments, e-money top ups, SBN orders |

Adding one is a directory with a class and an icon in it — no build file, no
registration, and the list the app reads is generated at compile time. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## What it keeps

Email bodies are stored, deliberately. A source taught to read something in a
later release re-reads mail already in the database instead of paying Gmail
twenty quota units a message again. It is the most sensitive thing the app
holds: it never leaves the device, and nothing derived from it is committed to
this repository. Everything else stays on the device too — finbox has no backend
and sends nothing anywhere. Transactions are editable and soft-deletable, and
can be exported to CSV or backed up as a file.

Currently IDR only, and sync is manual pull-to-refresh.

## Modules

| Path | Purpose |
|---|---|
| `app/` | UI, Gmail client, sync |
| `data/` | SQLDelight database, repositories, export and backup |
| `source/core/` | The contract every bank reader implements, plus `Receipt` |
| `source/lib/<country>/<bank>/` | One bank reader each |

`source/core/` is a plain Kotlin module — no Android on its classpath, so a
source cannot fetch, schedule, or reach a token even by accident. A bank module
is an Android library only because it carries its own icon, and it has no build
file: everything about it is derived from where it sits. `data/` knows source
ids only as strings.

The KSP processor that assembles the source list lives in `source/core/` too, in
a source set of its own so that it can name the contract as classes without
riding into the APK.

## Status

A personal project, built for its author's own mailbox. `gmail.readonly` is a
restricted scope, so without Google's security assessment this stays a
sideloaded app for accounts you add to your own OAuth client rather than
something distributable.

Building it needs a Google OAuth client id in `local.properties`:

```properties
oauthClientIdDebug=<android client id>
oauthClientIdRelease=<android client id>
```

Both are Android OAuth clients — package name `dev.achmad.finbox`, your signing
certificate's SHA-1, and "Custom URI scheme" enabled.

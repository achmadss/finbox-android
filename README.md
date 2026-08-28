# finbox

A personal spending ledger, fed by the receipts your bank already emails you.

finbox reads a Gmail mailbox, hands each email to the extensions you have
installed, and records what they recognise as transactions. No bank
credentials, no screen scraping, no SMS permissions — just the notification
mails you already get, turned into a month-by-month view of what went out and
what came in.

It is a ledger, not a parsing dashboard. Emails nothing recognises are dropped
quietly; there is no review queue to work through.

## How it works

1. **Connect a mailbox.** Google OAuth, `gmail.readonly`, no password stored.
2. **Install extensions.** Each provider — BRI, Jago — is a small separate APK
   from [finbox-extension](https://github.com/achmadss/finbox-extension),
   verified by hash and loaded in-process. Installing one is what teaches the
   app to read that bank.
3. **Import.** The first sync walks the mailbox for mail the installed extensions
   claim as theirs. Every sync after that asks Gmail only what changed, so a
   refresh with nothing new costs a single API call.
4. **Read the ledger.** One month at a time, out / in / net at the top,
   transactions grouped by day. Multiple mailboxes merge into one ledger.

Parsing lives entirely in the extensions. The app fetches mail and keeps the
ledger; it holds no knowledge of any bank's email format, and gains support for
a new one without an app release.

## What it keeps

Email bodies are never stored. An import downloads a message, offers it to the
extensions, writes what they found and drops it — the database keeps message ids
and the transactions themselves, nothing else. Everything stays on the device;
finbox has no backend and sends nothing anywhere. Transactions are editable and
soft-deletable, and can be exported to CSV or backed up as a file.

Currently IDR only, and sync is manual pull-to-refresh.

## Modules

| Path | Purpose |
|---|---|
| `app/` | UI, Gmail client, extension loading, sync |
| `data/` | SQLDelight database, repositories, export and backup |
| `extension-api/` | The types extensions implement |

`:extension-api` is what extensions compile against. JitPack builds it on demand
from any tag or commit (see `jitpack.yml`), published as
`com.github.achmadss:finbox-android`, so finbox-extension can be cloned and
built by anyone — no checkout of this repo, no account, no token.

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

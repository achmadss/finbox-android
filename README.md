# finbox

finbox turns the receipts your bank already emails you into a personal
spending ledger. It reads a Gmail mailbox, matches each email against built-in
bank readers, and records what they recognize as transactions.

## Supported banks

| Provider | Emails the reader handles |
|---|---|
| Bank BNI | wondr receipts: QRIS, transfers, and TapCash top ups |
| Bank BRI | BRImo receipts: QRIS, transfers, and BRIZZI top ups |
| Bank Jago | payments, transfers, Jago Partner, and debit card purchases |
| Bank Mandiri | Livin' receipts: QR payments, e-money top ups, and SBN orders |

## Building

To build finbox, add a Google OAuth client id to `local.properties`:

```properties
oauthClientIdDebug=<android client id>
oauthClientIdRelease=<android client id>
```

Both clients use the package name `dev.achmad.finbox`, your signing
certificate's SHA-1, and the "Custom URI scheme" option.

finbox is a personal project. `gmail.readonly` is a restricted scope, so it
stays a sideloaded app for accounts you add to your own OAuth client.

To add a bank, see [CONTRIBUTING.md](CONTRIBUTING.md).

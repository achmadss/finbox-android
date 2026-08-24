package dev.achmad.finbox.util.preview

import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.InstalledParser
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.core.parser.AvailableParser

/**
 * Stand-in records for `@Preview`.
 *
 * Kept in one place so a preview is a literal rather than a fake repository,
 * and so a model gaining a field breaks here once instead of in every screen.
 */

/** A fixed instant, so a preview renders the same picture every time. */
const val PREVIEW_TIMESTAMP = 1_700_000_000_000L

fun previewTransaction(
    index: Int = 0,
    amount: Long = 125_000,
    direction: TransactionDirection = TransactionDirection.OUTGOING,
    description: String? = "Coffee and a croissant",
    merchant: String? = "Kopi Kenangan",
    category: String? = "Food",
    type: String? = "QRIS",
    date: Long = PREVIEW_TIMESTAMP,
) = Transaction(
    accountId = "preview-account",
    parserId = 1L,
    emailMessageId = "message-$index",
    index = index,
    threadId = "thread-$index",
    reference = "REF$index",
    date = date,
    amount = amount,
    currency = "IDR",
    direction = direction,
    type = type,
    category = category,
    description = description,
    merchant = merchant,
    createdAt = PREVIEW_TIMESTAMP,
    updatedAt = PREVIEW_TIMESTAMP,
    deleted = false,
)

fun previewAccount(
    id: String = "preview-account",
    email: String = "someone@example.com",
    displayName: String? = "Someone",
    enabled: Boolean = true,
    lastSyncAt: Long? = PREVIEW_TIMESTAMP,
) = EmailAccount(
    id = id,
    email = email,
    displayName = displayName,
    authTokenRef = null,
    enabled = enabled,
    createdAt = PREVIEW_TIMESTAMP,
    updatedAt = PREVIEW_TIMESTAMP,
    lastSyncAt = lastSyncAt,
)

fun previewInstalledParser(
    pkg: String = "dev.achmad.parser.jago",
    name: String = "Jago",
    provider: String = "Bank Jago",
    versionCode: Int = 3,
    versionName: String = "1.2.0",
    enabled: Boolean = true,
) = InstalledParser(
    pkg = pkg,
    provider = provider,
    name = name,
    file = "/data/parsers/$pkg.apk",
    versionCode = versionCode,
    versionName = versionName,
    libVersion = "1.0",
    sha256 = "0".repeat(64),
    parserIds = listOf(1L),
    enabled = enabled,
)

fun previewAvailableParser(
    pkg: String = "dev.achmad.parser.bri",
    name: String = "BRI",
    provider: String = "Bank BRI",
    versionCode: Int = 5,
    versionName: String = "2.0.0",
) = AvailableParser(
    name = name,
    provider = provider,
    pkg = pkg,
    versionCode = versionCode,
    versionName = versionName,
    libVersion = 1.0,
    apkUrl = "https://example.com/$pkg.apk",
    sha256 = "0".repeat(64),
    iconUrl = null,
)

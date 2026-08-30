#!/usr/bin/env python3
"""What the signature design is worth on a real ledger.

Reads the app's database — from a connected device by default, or from a file
with --db — and prints: how many transactions collapse into how many
signatures, how many name no merchant to file, which groups are big enough to
be worth getting right, and what the once-seen tail is made of.

Nothing here uploads anything. The database holds real mail and real money, so
the copy stays where you point it and no output should be committed.
"""
import argparse
import collections
import pathlib
import re
import sqlite3
import subprocess
import sys
import tempfile

PACKAGE = "dev.achmad.finbox"
WHITESPACE = re.compile(r"\s+")


def normalize(value):
    """Trim, collapse whitespace, uppercase; blank is nothing.

    Mirrors normalizeForSignature in the app. If one of them changes, this
    report stops describing what the app actually does.
    """
    if value is None:
        return None
    return WHITESPACE.sub(" ", value.strip()).upper() or None


def pull() -> pathlib.Path:
    """Copy the database off a connected device. Needs a debuggable build."""
    out = pathlib.Path(tempfile.mkdtemp()) / "finbox.db"
    try:
        data = subprocess.run(
            ["adb", "exec-out", "run-as", PACKAGE, "cat", "databases/finbox.db"],
            check=True, capture_output=True,
        ).stdout
    except FileNotFoundError:
        sys.exit("adb is not on PATH.")
    except subprocess.CalledProcessError as error:
        sys.exit(f"Could not read the database: {error.stderr.decode().strip()}")
    if not data:
        sys.exit("The database came back empty. Is a debuggable build installed?")
    out.write_bytes(data)
    return out


def signature(row) -> tuple:
    """The tuple a classifier sees. Amount stays out — it is unique per row.

    The merchant is the whole text input: description was dropped from the
    signature in the app, so this must match or the report stops describing
    what the app does. Direction is included because same-merchant money in
    one direction and out the other is a different classification question.
    """
    return (
        normalize(row["merchant"]),
        row["direction"],
    )


def named(sig) -> bool:
    """Whether the receipt named a counterparty."""
    return sig[0] is not None


def sendable(sig) -> bool:
    """Whether the app would file it, which is any merchant at all.

    Mirrors Signature.isComplete: description is gone from the app's
    signature, so a merchant-less row is UNKNOWN regardless of its note.
    """
    return named(sig)


def coverage(rows):
    groups = collections.Counter(signature(r) for r in rows)
    empty = [s for s in groups if not sendable(s)]
    identified = [s for s in groups if named(s)]
    rows_in = lambda sigs: sum(groups[s] for s in sigs)

    print(f"transactions            {len(rows)}")
    print(f"distinct signatures     {len(groups)}")
    print(f"rows per signature      {len(rows) / len(groups):.1f}x")
    print(f"classifier calls saved  {100 * (1 - len(groups) / len(rows)):.0f}%")
    print()
    print("What the file screen is being asked:")
    print(f"  a merchant to file            {rows_in(identified):>4} rows in {len(identified):>3} signatures")
    print(f"  no merchant, stays UNKNOWN    {rows_in(empty):>4} rows in {len(empty):>3} signatures")
    once = [s for s in identified if groups[s] == 1]
    print(f"  identified and seen once      {len(once):>4} signatures")
    print()
    print("The second row is the ceiling. Those receipts name no counterparty, so no")
    print("filing is possible and UNKNOWN is the honest answer — Jago states none on")
    print("much of its mail, and that is the bank never writing it down rather than")
    print("anything to fix here.")
    print()
    print("The biggest groups decide most of the ledger. One wrong answer here")
    print("is wrong for every row under it, so these are the ones to check:")
    for sig, count in groups.most_common(10):
        merchant, direction = sig
        share = 100 * count / len(rows)
        flag = "  <- no counterparty named" if not named(sig) else ""
        print(f"  {count:>4} rows ({share:>4.1f}%)  {str(merchant)[:40]:<40} | {direction}{flag}")


def once_seen(groups):
    """What the tail actually is: singleton signatures, grouped by first token.

    If they are variations on a few shops (SHOPEE ID, SHOPEEPAY, SHOPEE -
    4471), keyword rules absorb them all. If they are fifty genuinely distinct
    merchants, nothing helps; each is seen once either way and the tail stops
    being worth thinking about.
    """
    singletons = [s for s, count in groups.items() if count == 1]
    by_first_token = collections.Counter(
        s[0].split()[0] if s[0] else "(no merchant)"
        for s in singletons
    )
    print(f"once-seen signatures   {len(singletons)}")
    print("grouped by first token:")
    for token, count in by_first_token.most_common(15):
        print(f"  {count:>4}  {token[:48]}")
    print()
    print("Both numbers matter: a small set of first tokens means keyword rules")
    print("cover the tail; a wide spread means there is nothing to generalize.")

    print("The once-seen signatures themselves:")
    for merchant, direction in sorted(singletons, key=lambda s: s[0] or ""):
        print(f"  {str(merchant or '(none)')[:48]:<48} | {direction}")


def main() -> int:
    extension = argparse.ArgumentParser(description=__doc__)
    extension.add_argument("--db", type=pathlib.Path, help="a database file instead of a device")
    args = extension.parse_args()

    path = args.db or pull()
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    rows = list(connection.execute("SELECT * FROM transactions WHERE deleted = 0"))
    if not rows:
        sys.exit("No transactions in that database.")

    print("== coverage ==\n")
    coverage(rows)
    print("\n== once-seen tail ==\n")
    once_seen(collections.Counter(signature(r) for r in rows))
    return 0


if __name__ == "__main__":
    sys.exit(main())

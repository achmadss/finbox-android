#!/usr/bin/env python3
"""What the signature design is worth on a real ledger, and how a classifier is doing.

Reads the app's database — from a connected device by default, or from a file
with --db — and prints two reports:

  Coverage    needs no labels at all. How many transactions collapse into how
              many signatures, how many carry nothing to classify with, and
              which groups are big enough to be worth getting right.

  Agreement   needs no labelling *chore*. The labels are the categories the user
              set by hand, which ordinary use of the app produces. It reports
              how often the classifier agreed with them.

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
    """The tuple a classifier sees. Amount stays out — it is unique per row."""
    return (
        normalize(row["merchant"]),
        normalize(row["description"]),
        row["direction"],
        normalize(row["method"] if "method" in row.keys() else row["type"]),
    )


def named(sig) -> bool:
    """Whether the receipt named a counterparty or said anything about this one."""
    return sig[0] is not None or sig[1] is not None


def sendable(sig) -> bool:
    """Whether the app would send it, which is any text at all including a method.

    Mirrors Signature.isComplete. Whether a method alone is enough to tell what
    money was for is a question about the world, so the app does not answer it —
    it asks, and the classifier may reply that the receipt does not say.
    """
    return named(sig) or sig[3] is not None


def coverage(rows):
    groups = collections.Counter(signature(r) for r in rows)
    empty = [s for s in groups if not sendable(s)]
    method_only = [s for s in groups if sendable(s) and not named(s)]
    identified = [s for s in groups if named(s)]
    rows_in = lambda sigs: sum(groups[s] for s in sigs)

    print(f"transactions            {len(rows)}")
    print(f"distinct signatures     {len(groups)}")
    print(f"rows per signature      {len(rows) / len(groups):.1f}x")
    print(f"classifier calls saved  {100 * (1 - len(groups) / len(rows)):.0f}%")
    print()
    print("What the classifier is being asked:")
    print(f"  a counterparty or a note   {rows_in(identified):>4} rows in {len(identified):>3} signatures")
    print(f"  only how the money moved   {rows_in(method_only):>4} rows in {len(method_only):>3} signatures")
    print(f"  nothing at all, not sent   {rows_in(empty):>4} rows in {len(empty):>3} signatures")
    once = [s for s in identified if groups[s] == 1]
    print(f"  identified and seen once   {len(once):>4} signatures")
    print()
    print("The middle row is the one to watch. Those receipts name no counterparty,")
    print("so the honest answer is usually UNKNOWN — if the classifier invents a")
    print("category for them instead, it is guessing and the prompt needs work.")
    print()
    print("The biggest groups decide most of the ledger. One wrong answer here")
    print("is wrong for every row under it, so these are the ones to check:")
    for sig, count in groups.most_common(10):
        merchant, description, direction, method = sig
        share = 100 * count / len(rows)
        flag = "  <- no counterparty named" if not named(sig) else ""
        print(f"  {count:>4} rows ({share:>4.1f}%)  {str(merchant)[:24]:<24} | "
              f"{str(description)[:38]:<38} | {direction} {method}{flag}")


def agreement(rows):
    """Score the classifier against the categories the user set by hand.

    Corrections are a biased sample — people fix what looks wrong and leave
    what looks right — so this reads low rather than high. That makes it a
    reasonable regression signal and a poor accuracy claim.
    """
    by_user = [r for r in rows if r["category_source"] == "USER" and r["category"]]
    if not by_user:
        print("No hand-set categories yet, so there is nothing to score against.")
        print("This fills in on its own: every category set by hand becomes a label.")
        return

    answers = {}
    for row in rows:
        if row["category_source"] == "AI" and row["category"]:
            answers[signature(row)] = row["category"]

    checked = [(r, answers[signature(r)]) for r in by_user if signature(r) in answers]
    if not checked:
        print(f"{len(by_user)} hand-set categories, none of them on a signature the")
        print("classifier has also answered, so there is nothing to compare yet.")
        return

    agreed = [r for r, guess in checked if guess == r["category"]]
    print(f"hand-set categories     {len(by_user)}")
    print(f"also answered by AI     {len(checked)}")
    print(f"agreed                  {len(agreed)} ({100 * len(agreed) / len(checked):.0f}%)")
    print()
    confusion = collections.Counter(
        (guess, r["category"]) for r, guess in checked if guess != r["category"]
    )
    if confusion:
        print("Where it disagreed — the model said, you said:")
        for (guess, actual), count in confusion.most_common(10):
            print(f"  {count:>3}  {guess} -> {actual}")


def main() -> int:
    extension = argparse.ArgumentExtension(description=__doc__)
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
    print("\n== agreement ==\n")
    if "category_source" in rows[0].keys():
        agreement(rows)
    else:
        print("This database predates category_source, so there is nothing to score.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

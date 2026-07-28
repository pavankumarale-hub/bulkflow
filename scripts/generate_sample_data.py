#!/usr/bin/env python3
"""Generate BulkFlow demo sample data — 10,000 accounts (9,988 valid + 12 invalid), 5,000 transactions."""

import csv
import json
import random
from pathlib import Path

random.seed(42)
OUT = Path(__file__).parent.parent / "sample-data"
OUT.mkdir(exist_ok=True)

FIRST_NAMES = [
    "Alice", "Bob", "Carol", "David", "Eve", "Frank", "Grace", "Hank",
    "Iris", "Jack", "Karen", "Liam", "Mia", "Noah", "Olivia", "Paul",
    "Quinn", "Rachel", "Sam", "Tina", "Uma", "Victor", "Wendy", "Xander",
    "Yara", "Zoe", "Aaron", "Bella", "Chris", "Diana", "Ethan", "Fiona",
    "George", "Hannah", "Ivan", "Julia", "Kevin", "Laura", "Marcus", "Nina",
]
LAST_NAMES = [
    "Smith", "Jones", "Brown", "Wilson", "Taylor", "Davies", "Evans",
    "Thomas", "Roberts", "Johnson", "Lewis", "Walker", "Robinson",
    "White", "Thompson", "Martin", "Garcia", "Martinez", "Harris", "Clark",
    "Rodriguez", "Jackson", "Lee", "Perez", "Hall", "Young", "Allen",
    "Sanchez", "Wright", "King", "Scott", "Green", "Baker", "Adams",
]
STATUSES   = ["ACTIVE"] * 6 + ["INACTIVE"] * 2 + ["PENDING"] * 2
CURRENCIES = ["USD"] * 8 + ["EUR"] * 15 + ["GBP"] * 5
TX_TYPES   = ["CREDIT", "DEBIT", "DEBIT", "DEBIT", "REFUND", "FEE", "TRANSFER"]
DESCRIPTIONS = [
    "Direct deposit", "POS purchase", "ATM withdrawal", "Online transfer",
    "Subscription fee", "Refund credit", "Wire transfer", "ACH payment",
    "Bill payment", "Payroll deposit", "Insurance reimbursement", "Consulting invoice",
]

FIELDNAMES = [
    "account_id", "email", "first_name", "last_name", "status",
    "date_of_birth", "phone", "credit_limit", "currency",
]


def rand_dob():
    y = random.randint(1955, 1998)
    m = random.randint(1, 12)
    d = random.randint(1, 28)
    return f"{y}-{m:02d}-{d:02d}"


def rand_phone():
    return f"+1-{random.randint(200, 999)}-{random.randint(200, 999)}-{random.randint(1000, 9999)}"


def rand_limit():
    return round(random.uniform(1000, 50000), 2)


def make_account(i):
    fn = random.choice(FIRST_NAMES)
    ln = random.choice(LAST_NAMES)
    return {
        "account_id":    f"acc_{i:05d}",
        "email":         f"{fn.lower()}.{ln.lower()}{i}@example.com",
        "first_name":    fn,
        "last_name":     ln,
        "status":        random.choice(STATUSES),
        "date_of_birth": rand_dob(),
        "phone":         rand_phone(),
        "credit_limit":  rand_limit(),
        "currency":      random.choice(CURRENCIES),
    }


def write_accounts():
    rows = [make_account(i) for i in range(1, 9989)]  # 9,988 valid

    # Inject exactly 12 bad rows (7 invalid_email, 3 missing_field, 2 duplicate)
    bad_rows = [
        # 7 invalid_email
        {**make_account(20001), "email": "not-an-email"},
        {**make_account(20002), "email": "missing@"},
        {**make_account(20003), "email": "@nodomain.com"},
        {**make_account(20004), "email": "no-at-sign"},
        {**make_account(20005), "email": "spaces in@email.com"},
        {**make_account(20006), "email": "double@@at.com"},
        {**make_account(20007), "email": ""},
        # 3 missing_field
        {**make_account(20008), "first_name": ""},
        {**make_account(20009), "last_name": ""},
        {**make_account(20010), "account_id": ""},
        # 2 duplicate (same account_id as existing valid rows → caught as duplicate_in_batch)
        {**make_account(1), "account_id": "acc_00001"},
        {**make_account(2), "account_id": "acc_00002"},
    ]

    # Scatter bad rows among valid rows so they're not clustered at the end
    positions = sorted(random.sample(range(len(rows)), len(bad_rows)))
    for pos, bad in zip(positions, bad_rows):
        rows.insert(pos, bad)

    out_file = OUT / "accounts_bulk.csv"
    with open(out_file, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Generated {len(rows):,} account rows → {out_file}")
    print(f"  Breakdown: 9,988 valid | 7 invalid_email | 3 missing_field | 2 duplicate")
    return len(rows)


def write_transactions():
    out_file = OUT / "transactions_bulk.jsonl"
    count = 0
    with open(out_file, "w") as f:
        for i in range(1, 5001):
            account_id = f"acc_{random.randint(1, 9988):05d}"
            tx = {
                "transactionId":   f"txn_{i:06d}",
                "accountId":       account_id,
                "amount":          round(random.uniform(1.0, 5000.0), 2),
                "currency":        random.choice(["USD"] * 8 + ["EUR"] * 2),
                "transactionType": random.choice(TX_TYPES),
                "transactionDate": (
                    f"2024-{random.randint(1, 12):02d}-{random.randint(1, 28):02d}"
                ),
                "description": random.choice(DESCRIPTIONS),
            }
            f.write(json.dumps(tx) + "\n")
            count += 1

    print(f"Generated {count:,} transaction records → {out_file}")
    return count


if __name__ == "__main__":
    print("BulkFlow — generating demo sample data")
    print("=" * 50)
    account_count = write_accounts()
    tx_count = write_transactions()
    print("=" * 50)
    print(f"Complete: {account_count:,} accounts | {tx_count:,} transactions")
    print()
    print("Expected demo output:")
    print("  Batch complete: 9,988 succeeded, 12 failed")
    print("    7 invalid_email | 3 missing_field | 2 duplicate_in_batch")

"""A simple data-processing script for fixture purposes."""

import csv
import sys
from pathlib import Path


def summarize(rows):
    total = 0
    count = 0
    for row in rows:
        try:
            total += float(row["value"])
            count += 1
        except (KeyError, ValueError):
            continue
    return total / count if count else 0.0


def load_rows(path):
    with path.open("r", encoding="utf-8", newline="") as fh:
        return list(csv.DictReader(fh))


def main(argv):
    if len(argv) < 2:
        print("usage: summarize.py <csv>", file=sys.stderr)
        return 2
    rows = load_rows(Path(argv[1]))
    mean = summarize(rows)
    print(f"mean={mean:.4f} n={len(rows)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

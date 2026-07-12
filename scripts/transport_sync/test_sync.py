import tempfile
import unittest
from pathlib import Path

from scripts.transport_sync.sync import (
    SyncError,
    apply_overrides,
    identity,
    parse_action,
    read_tsv,
    write_tsv,
)


FIXTURES = Path(__file__).resolve().parent / "fixtures"


class TransportSyncTest(unittest.TestCase):
    def test_parses_multiword_target_and_object_id(self):
        self.assertEqual(("Open", "Tree Gnome Gate", "190"), parse_action("Open Tree Gnome Gate 190"))

    def test_applies_duration_override_without_mutating_other_fields(self):
        table = read_tsv(FIXTURES / "upstream" / "transports.tsv")
        overrides = read_tsv(FIXTURES / "local_overrides.tsv").rows
        applied = apply_overrides({"transports.tsv": table}, overrides)

        self.assertEqual(1, len(applied))
        gate = next(row for row in table.rows if row["menuOption menuTarget objectID"] == "Open Gate 190")
        self.assertEqual("9", gate["Duration"])
        door = next(row for row in table.rows if row["menuOption menuTarget objectID"] == "Open Door 123")
        self.assertEqual("Fixture door", door["Display info"])

    def test_ambiguous_patch_fails_loudly(self):
        table = read_tsv(FIXTURES / "upstream" / "transports.tsv")
        table.rows.append(dict(table.rows[0]))
        overrides = read_tsv(FIXTURES / "local_overrides.tsv").rows
        with self.assertRaisesRegex(SyncError, "matched 2 rows"):
            apply_overrides({"transports.tsv": table}, overrides)

    def test_output_is_deterministic(self):
        table = read_tsv(FIXTURES / "upstream" / "transports.tsv")
        with tempfile.TemporaryDirectory() as temp_dir:
            first = Path(temp_dir) / "first.tsv"
            second = Path(temp_dir) / "second.tsv"
            table.rows.sort(key=lambda row: identity("transports.tsv", row))
            write_tsv(first, table)
            write_tsv(second, table)
            self.assertEqual(first.read_bytes(), second.read_bytes())


if __name__ == "__main__":
    unittest.main()

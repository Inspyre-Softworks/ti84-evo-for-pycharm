import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("smartpad_usb.py")
SPEC = importlib.util.spec_from_file_location("smartpad_usb", SCRIPT)
smartpad_usb = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(smartpad_usb)


PACKAGE_REPORT_DESCRIPTOR = bytes.fromhex(
    "05 01 09 06 A1 01 05 07 19 E0 29 E7 15 00 25 01 "
    "75 01 95 08 81 02 95 01 75 08 81 01 95 05 75 01 "
    "05 08 19 01 29 05 91 02 95 01 75 03 91 01 95 06 "
    "75 08 15 00 25 FF 05 07 19 00 29 FF 81 00 C0"
)


class HidDescriptorTests(unittest.TestCase):
    def test_os_7_1_descriptor_reports_and_unknown_policy(self):
        parsed = smartpad_usb.parse_hid_report_descriptor(PACKAGE_REPORT_DESCRIPTOR)

        self.assertEqual(63, parsed["length"])
        self.assertEqual([0], parsed["report_ids"])
        self.assertEqual([], parsed["vendor_defined_usage_pages"])
        self.assertEqual(
            [
                {"type": "input", "report_id": 0, "payload_bits": 64, "wire_bytes": 8},
                {"type": "output", "report_id": 0, "payload_bits": 8, "wire_bytes": 1},
            ],
            parsed["reports"],
        )
        self.assertFalse(any(field["type"] == "feature" for field in parsed["fields"]))

    def test_truncated_long_item_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "offset 0"):
            smartpad_usb.parse_hid_report_descriptor(bytes.fromhex("FE 03 A5 01"))


class BootReportTests(unittest.TestCase):
    def test_modifiers_multiple_keys_and_unknown_usage_are_preserved(self):
        raw = bytes.fromhex("03 7F 04 FE 00 00 00 00")
        decoded = smartpad_usb.decode_boot_report(raw)

        self.assertTrue(decoded["valid"])
        self.assertEqual(0x7F, decoded["reserved_byte"])
        self.assertEqual([0xE0, 0xE1], decoded["modifiers"])
        self.assertEqual([4, 0xFE, 0, 0, 0, 0], decoded["key_slots"])
        self.assertIn("Unknown(0xFE)", decoded["decoded_usages"])

    def test_malformed_report_keeps_raw_hex(self):
        decoded = smartpad_usb.decode_boot_report(bytes.fromhex("00 01"))

        self.assertFalse(decoded["valid"])
        self.assertEqual("00 01", decoded["raw_hex"])
        self.assertIn("expected 8 bytes", decoded["error"])

    def test_press_hold_and_release_transitions(self):
        down, state = smartpad_usb.report_log_line(
            "t1", bytes.fromhex("00 00 3A 00 00 00 00 00"), set(), 3, 0x84
        )
        held, state = smartpad_usb.report_log_line(
            "t2", bytes.fromhex("00 00 3A 00 00 00 00 00"), state, 3, 0x84
        )
        up, state = smartpad_usb.report_log_line("t3", bytes(8), state, 3, 0x84)

        self.assertIn("F1:DOWN", down)
        self.assertIn("HELD/UNCHANGED", held)
        self.assertIn("F1:UP", up)
        self.assertEqual(set(), state)

    def test_captured_modifier_chord_maps_to_calculator_key(self):
        decoded = smartpad_usb.decode_boot_report(bytes.fromhex("05 00 6C 00 00 00 00 00"))

        self.assertEqual(["LeftControl", "LeftAlt", "F17"], decoded["decoded_usages"])
        self.assertEqual(["WINDOW"], decoded["calculator_keys"])
        self.assertEqual(50, len(smartpad_usb.SMARTPAD_CALCULATOR_KEYS))

    def test_unknown_chord_is_retained_without_calculator_label(self):
        decoded = smartpad_usb.decode_boot_report(bytes.fromhex("00 00 FE 00 00 00 00 00"))

        self.assertEqual(["Unknown(0xFE)"], decoded["decoded_usages"])
        self.assertEqual([], decoded["calculator_keys"])

    def test_modifier_only_change_tracks_reused_usage_as_distinct_calculator_keys(self):
        _, state = smartpad_usb.report_log_line(
            "t1", bytes.fromhex("03 00 69 00 00 00 00 00"), set(), 3, 0x84
        )
        changed, _ = smartpad_usb.report_log_line(
            "t2", bytes.fromhex("02 00 69 00 00 00 00 00"), state, 3, 0x84
        )

        self.assertIn("9:UP", changed)
        self.assertIn("LOG:DOWN", changed)


if __name__ == "__main__":
    unittest.main()

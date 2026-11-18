import argparse
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("reset_signup_limits", Path(__file__).resolve().parents[1] / "reset_signup_limits.py")
reset = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reset)


class FakeRedis:
    def __init__(self):
        self.calls = []
        self.reservation = None
    def scan(self, pattern):
        if pattern.startswith("signup:session:"):
            return ["reservation"] if self.reservation else []
        return ["selected"]
    def run(self, *command):
        self.calls.append(command)
        return self.reservation if command[0] == "GET" else len(command) - 1


class ResetLimitsTest(unittest.TestCase):
    def test_dry_run_never_deletes(self):
        redis = FakeRedis()
        self.assertEqual((1, 0), reset.reset(redis, "selected"))
        self.assertEqual([], redis.calls)
    def test_pending_compensation_prevents_deleting_fresh_counters(self):
        redis = FakeRedis()
        redis.reservation = '["selected", "unrelated"]'
        with self.assertRaises(ValueError):
            reset.reset(redis, "selected", True)
        self.assertFalse(any(command[0] == "UNLINK" for command in redis.calls))
    def test_window_requires_timezone_and_exact_alignment(self):
        self.assertEqual(1794236400, reset.window_start("destination", "2026-11-10T00:00:00+09:00"))
        self.assertEqual(reset.window_start("total-email", "2026-11-10T09:00:00+09:00"),
                         reset.window_start("total-email", "2026-11-10T00:00:00Z"))
        for invalid in ["2026-11-10T00:00:00", "2026-11-10T00:01:00+09:00", "2026-11-10T01:00:00+09:00"]:
            with self.assertRaises(ValueError): reset.window_start("destination", invalid)
    def test_hourly_window_alignment_uses_seoul_instead_of_input_offset(self):
        with self.assertRaises(ValueError):
            reset.window_start("ip", "2026-11-10T00:00:00+05:30")
        self.assertEqual(reset.window_start("ip", "2026-11-10T00:00:00Z"),
                         reset.window_start("ip", "2026-11-10T00:30:00+00:30"))
    def test_ipv6_uses_64_bits_and_ipv4_mapped_ipv6_matches_ipv4(self):
        self.assertEqual(reset.ip_digest("test", "2001:db8:1:2::1"), reset.ip_digest("test", "2001:db8:1:2::ffff"))
        self.assertEqual(reset.ip_digest("test", "192.0.2.1"), reset.ip_digest("test", "::ffff:192.0.2.1"))
    def test_identity_verification_kind_selects_one_account_key_the_server_writes(self):
        self.assertIn("identity-verification", reset.KINDS)
        args = argparse.Namespace(kind="identity-verification", window_start="2026-11-10T14:00:00+09:00",
                                  account="01937A5E-8C2B-7E41-9A7D-4F1C2B3E5D6A", recipient=False, ip=False, channel=None)
        # RedisPublicRequestLimitStoreIdentityVerificationTest가 같은 문자열을 서버 쪽 키로 단정한다.
        self.assertEqual("signup:limit:v2:identity-verification:1794286800:01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6a",
                         reset.selection(args))
    def test_identity_verification_kind_without_account_matches_every_account_in_the_window(self):
        args = argparse.Namespace(kind="identity-verification", window_start="2026-11-10T14:00:00+09:00",
                                  account=None, recipient=False, ip=False, channel=None)
        self.assertEqual("signup:limit:v2:identity-verification:1794286800:*", reset.selection(args))
    def test_account_is_only_for_identity_verification_and_must_be_a_uuid(self):
        window = "2026-11-10T14:00:00+09:00"
        account = "01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6a"
        invalid = [
            argparse.Namespace(kind="identity-verification", window_start=window, account="not-a-uuid",
                               recipient=False, ip=False, channel=None),
            argparse.Namespace(kind="identity-verification", window_start=window, account="01937a5e8c2b7e419a7d4f1c2b3e5d6a",
                               recipient=False, ip=False, channel=None),
            argparse.Namespace(kind="identity-verification", window_start=window, account=account,
                               recipient=False, ip=True, channel=None),
            argparse.Namespace(kind="ip", window_start=window, account=account,
                               recipient=False, ip=False, channel=None),
        ]
        for args in invalid:
            with self.assertRaises(ValueError): reset.selection(args)
    def test_email_strip_matches_java_and_phone_rejects_noncanonical_input(self):
        self.assertEqual("me@example.com", reset.normalized_recipient("email", "\u2000ME@example.com\t"))
        self.assertEqual("\u00a0me@example.com\u00a0", reset.normalized_recipient("email", "\u00a0ME@example.com\u00a0"))
        for phone in ["010-1234-5678", "+821012345678", " 01012345678"]:
            with self.assertRaises(ValueError): reset.normalized_recipient("phone", phone)


if __name__ == "__main__":
    unittest.main()

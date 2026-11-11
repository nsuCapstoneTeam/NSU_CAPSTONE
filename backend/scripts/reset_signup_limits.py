#!/usr/bin/env python3
"""Inspect or reset a single signup rate-limit kind/window using the container's redis-cli."""
import argparse
from datetime import datetime, timezone
import getpass
import hashlib
import hmac
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from zoneinfo import ZoneInfo

PREFIX = "signup:limit:v2:"
RECOVERY_SCRIPT = (Path(__file__).resolve().parents[1]
                   / "src/main/resources/redis/recover-unsent-signup-reservation.lua")
KINDS = ("destination", "attempt", "ip", "ip-email", "total-phone", "total-email",
         "session", "oauth", "login", "login-failure")
# Character.isWhitespace used by Java String.strip excludes nonbreaking spaces.
JAVA_WHITESPACE = "\t\n\v\f\r\u001c\u001d\u001e\u001f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000"


def normalized_recipient(channel, value):
    if channel == "email":
        return value.strip(JAVA_WHITESPACE).lower()
    if not re.fullmatch(r"01[016789][0-9]{7,8}", value):
        raise ValueError("휴대폰은 서버와 같은 숫자 정규형으로 입력하세요")
    return value


def digest(secret, value):
    return hmac.new(secret.encode("utf-8"), value.encode("utf-8"), hashlib.sha256).hexdigest()


def destination_digest(secret, channel, recipient):
    return digest(secret, "\x1f".join(("destination", channel.upper(), normalized_recipient(channel, recipient))))


def ip_digest(secret, value):
    address = ipaddress.ip_address(value)
    if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped:
        address = address.ipv4_mapped
    return digest(secret, "ip\x1f" + address.packed[:8].hex())


def window_start(kind, value):
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        raise ValueError("창 시작은 시간대가 있는 ISO-8601 시각이어야 합니다") from None
    if parsed.tzinfo is None:
        raise ValueError("창 시작은 시간대가 명시된 정각이어야 합니다")
    seoul = parsed.astimezone(ZoneInfo("Asia/Seoul"))
    if seoul.microsecond or seoul.minute or seoul.second:
        raise ValueError("창 시작은 Asia/Seoul 기준 정각이어야 합니다")
    if kind in ("destination", "attempt", "total-phone") and seoul.hour != 0:
        raise ValueError("이 하루 창은 Asia/Seoul 자정부터 시작합니다")
    if kind == "total-email" and seoul.hour != 9:
        raise ValueError("이메일 전체 하루 창은 Asia/Seoul 09:00부터 시작합니다")
    return int(parsed.timestamp())


def secret_input(label):
    if sys.stdin.isatty():
        return getpass.getpass(label + ": ")
    value = sys.stdin.readline()
    if not value:
        raise ValueError("선택한 수신처/IP 값을 표준입력으로 제공하세요")
    return value.rstrip("\r\n")


def selection(args):
    start = window_start(args.kind, args.window_start)
    if args.kind.startswith("total-"):
        if args.recipient or args.ip or args.channel:
            raise ValueError("서버 전체 한도에는 수신처/IP/channel을 지정하지 않습니다")
        suffix = "all"
    else:
        needs_pair = args.kind in ("attempt", "login-failure")
        if needs_pair and args.recipient != args.ip:
            raise ValueError("확인 시도·로그인 실패는 수신처와 IP를 함께 지정하세요")
        if args.recipient and args.kind not in ("destination", "attempt", "login-failure"):
            raise ValueError("이 한도에는 수신처를 지정할 수 없습니다")
        if args.ip and args.kind == "destination":
            raise ValueError("받는 곳 한도에는 IP를 지정하지 않습니다")
        channel = "email" if args.kind == "login-failure" else args.channel
        if args.recipient and not channel:
            raise ValueError("수신처를 지정할 때 --channel email 또는 phone이 필요합니다")
        if args.channel and not args.recipient:
            raise ValueError("--channel은 특정 수신처를 지정할 때만 사용합니다")
        secret = os.environ.get("SIGNUP_VERIFICATION_HMAC_SECRET", "")
        if (args.recipient or args.ip) and not secret:
            raise ValueError("대상 서버와 같은 SIGNUP_VERIFICATION_HMAC_SECRET 환경변수가 필요합니다")
        parts = []
        if args.recipient:
            parts.append(destination_digest(secret, channel, secret_input("수신처")))
        if args.ip:
            try:
                parts.append(ip_digest(secret, secret_input("IP")))
            except ValueError:
                raise ValueError("유효한 IP 주소를 입력하세요") from None
        suffix = ":".join(parts) if parts else "*"
    return f"{PREFIX}{args.kind}:{start}:{suffix}"


class RedisCli:
    def __init__(self, container=None):
        if container:
            if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", container):
                raise ValueError("컨테이너 ID/이름이 올바르지 않습니다")
            self.command = ["docker", "exec"]
        else:
            self.command = ["docker", "compose", "-f", str(Path(__file__).resolve().parents[1] / "compose.yaml"), "exec", "-T"]
        if os.environ.get("REDISCLI_AUTH"):
            self.command += ["--env", "REDISCLI_AUTH"]
        self.command += [container or "redis", "redis-cli", "--json"]

    def run(self, *args):
        try:
            result = subprocess.run(self.command + list(args), capture_output=True, text=True, check=True, timeout=10)
            return json.loads(result.stdout)
        except (OSError, subprocess.SubprocessError, ValueError):
            raise ValueError("Redis 명령에 실패했습니다. 컨테이너·접속 설정을 확인하세요") from None

    def scan(self, pattern):
        cursor = "0"
        while True:
            cursor, keys = self.run("SCAN", cursor, "MATCH", pattern, "COUNT", "200")
            yield from keys
            if cursor == "0":
                return


def recover_unsent(redis, reservation, encoded, record):
    pending = record.get("pending")
    indexes = record.get("indexes")
    if (record.get("key") != reservation or not isinstance(pending, str)
            or not isinstance(indexes, list) or not all(isinstance(key, str) for key in indexes)):
        raise ValueError("발송 예약 형식이 올바르지 않아 초기화를 중단했습니다")
    keys = [reservation, pending, *record["counters"], *indexes]
    if "deliveryKey" in record:
        if not isinstance(record["deliveryKey"], str):
            raise ValueError("발송 예약 형식이 올바르지 않아 초기화를 중단했습니다")
        keys.append(record["deliveryKey"])
    try:
        script = RECOVERY_SCRIPT.read_text(encoding="utf-8")
    except OSError:
        raise ValueError("발송 예약 복구 스크립트를 읽지 못해 초기화를 중단했습니다") from None
    script += """
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
if redis.call('EXISTS', KEYS[1]) == 0 then return 1 end
return recoverUnsentReservation(ARGV[1], declaredKeys(KEYS), now)
"""
    return redis.run("EVAL", script, str(len(keys)), *keys, encoded) == 1


def reset(redis, pattern, apply=False):
    keys = sorted(set(redis.scan(pattern)))
    if apply and keys:
        targets = set(keys)
        # A later compensation must not subtract from fresh counters after reset.
        for reservation in redis.scan("signup:session:*:verification:*:pending:*"):
            encoded = redis.run("GET", reservation)
            if encoded is None:
                continue
            try:
                record = json.loads(encoded)
            except (TypeError, ValueError):
                raise ValueError("발송 예약을 확인하지 못해 초기화를 중단했습니다") from None
            counters = record.get("counters") if isinstance(record, dict) else record
            if not isinstance(counters, list) or not all(isinstance(key, str) for key in counters):
                raise ValueError("발송 예약 형식이 올바르지 않아 초기화를 중단했습니다")
            if not targets.intersection(counters):
                continue
            if isinstance(record, dict) and recover_unsent(redis, reservation, encoded, record):
                continue
            raise ValueError("관련 발송 예약이 남아 있습니다. 복구 대기 시간 또는 발송 결과 정리 후 다시 실행하세요")
        removed = 0
        for index in range(0, len(keys), 200):
            removed += redis.run("UNLINK", *keys[index:index + 200])
        return len(keys), removed
    return len(keys), 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", required=True, choices=KINDS)
    parser.add_argument("--window-start", required=True, help="예: 2026-11-10T00:00:00+09:00")
    parser.add_argument("--channel", choices=("email", "phone"))
    parser.add_argument("--recipient", action="store_true", help="표준입력/숨김 프롬프트로 특정 수신처 지정")
    parser.add_argument("--ip", action="store_true", help="표준입력/숨김 프롬프트로 특정 IP 지정")
    parser.add_argument("--container", help="미지정 시 이 저장소 Compose의 redis 사용")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--quiesced", action="store_true", help="대상 서버의 신규 발송·진행 중 요청을 중지했음을 확인")
    args = parser.parse_args()
    try:
        if args.apply and not args.quiesced:
            raise ValueError("실제 초기화 전 발송을 중지하고 진행 요청 종료를 확인한 뒤 --quiesced를 지정하세요")
        pattern = selection(args)
        count, removed = reset(RedisCli(args.container), pattern, args.apply)
        print(json.dumps({"kind": args.kind, "windowStart": args.window_start,
                          "matched": count, "removed": removed, "applied": args.apply}, ensure_ascii=False))
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

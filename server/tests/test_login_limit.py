"""登录失败限流的回归用例：直接 python tests/test_login_limit.py 跑。"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.ratelimit import LoginLimiter  # noqa: E402


class FakeClock:
    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


def make_limiter(clock):
    return LoginLimiter(max_failures=5, window_sec=600, lock_sec=300, clock=clock)


def test_under_threshold_stays_allowed():
    clock = FakeClock()
    limiter = make_limiter(clock)
    for _ in range(4):
        limiter.fail("bob")
    assert limiter.retry_after("bob") == 0


def test_fifth_failure_locks():
    clock = FakeClock()
    limiter = make_limiter(clock)
    for _ in range(5):
        limiter.fail("bob")
    assert limiter.retry_after("bob") > 0


def test_lock_expires():
    clock = FakeClock()
    limiter = make_limiter(clock)
    for _ in range(5):
        limiter.fail("bob")
    clock.advance(301)
    assert limiter.retry_after("bob") == 0


def test_success_resets():
    clock = FakeClock()
    limiter = make_limiter(clock)
    for _ in range(4):
        limiter.fail("bob")
    limiter.reset("bob")
    assert limiter.retry_after("bob") == 0


def test_old_failures_fall_outside_window():
    clock = FakeClock()
    limiter = make_limiter(clock)
    for _ in range(4):
        limiter.fail("bob")
    clock.advance(601)
    limiter.fail("bob")
    assert limiter.retry_after("bob") == 0


def test_users_are_independent():
    clock = FakeClock()
    limiter = make_limiter(clock)
    for _ in range(5):
        limiter.fail("bob")
    assert limiter.retry_after("bob") > 0
    assert limiter.retry_after("alice") == 0


if __name__ == "__main__":
    failures = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("PASS", name)
            except AssertionError as exc:
                failures += 1
                print("FAIL", name, "->", exc)
            except Exception as exc:  # noqa: BLE001
                failures += 1
                print("ERROR", name, "->", type(exc).__name__, exc)
    raise SystemExit(1 if failures else 0)

"""登录失败限流：同一用户名在窗口内失败够多次就锁一段时间，成功登录立刻清零。

单进程内存实现：服务是 uvicorn --workers 1，够用；将来多进程再说。
"""

import math
import threading
import time


class _State:
    __slots__ = ("failures", "locked_until")

    def __init__(self) -> None:
        self.failures = []
        self.locked_until = 0.0


class LoginLimiter:
    def __init__(
        self,
        max_failures: int = 5,
        window_sec: int = 600,
        lock_sec: int = 300,
        clock=time.monotonic,
    ) -> None:
        self.max_failures = max_failures
        self.window_sec = window_sec
        self.lock_sec = lock_sec
        self._clock = clock
        self._lock = threading.Lock()
        self._states = {}

    def retry_after(self, key: str) -> int:
        """返回 0 表示允许尝试，正数表示还要等多少秒。"""
        with self._lock:
            state = self._states.get(key)
            if state is None:
                return 0
            remaining = state.locked_until - self._clock()
            if remaining > 0:
                return int(math.ceil(remaining))
            return 0

    def fail(self, key: str) -> int:
        """记一次失败，返回记完之后剩余锁定秒数（0 表示还没锁）。"""
        with self._lock:
            now = self._clock()
            state = self._states.setdefault(key, _State())
            state.failures = [t for t in state.failures if now - t < self.window_sec]
            state.failures.append(now)
            if len(state.failures) >= self.max_failures:
                state.failures = []
                state.locked_until = now + self.lock_sec
                return self.lock_sec
            return 0

    def reset(self, key: str) -> None:
        with self._lock:
            self._states.pop(key, None)

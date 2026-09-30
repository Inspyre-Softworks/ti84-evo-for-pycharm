#!/usr/bin/env python3
"""Use TI-84 Evo SmartPad's distinctive HID chords as Windows macro keys.

The calculator is a standard USB keyboard in SmartPad mode. This script uses
Windows global hotkeys, so it does not need a USB capture driver or a COM port.
Only configured keys are intercepted. See docs/smartpad.rst for limitations.
"""

from __future__ import annotations

import argparse
import ctypes
import json
import sys
import time
from ctypes import wintypes
from pathlib import Path


DEFAULT_BINDINGS = {
    "GRAPH": {"shortcut": "Shift+F10", "window": "PyCharm"},
    "TRACE": {"shortcut": "Shift+F9", "window": "PyCharm"},
}

# Physical key sweep on TI-84 Evo OS 7.1. Entries are (Win32 modifiers, VK).
# These distinctive F14-F20 chords avoid intercepting ordinary editing keys.
ALT, CTRL, SHIFT, NOREPEAT = 0x0001, 0x0002, 0x0004, 0x4000
SMARTPAD_KEYS = {
    "Y=": (0, 0x83), "WINDOW": (CTRL | ALT, 0x80),
    "ZOOM": (CTRL | ALT, 0x7F), "TRACE": (CTRL | SHIFT, 0x83),
    "GRAPH": (SHIFT | ALT, 0x80), "2nd": (CTRL | ALT, 0x82),
    "MODE": (CTRL | ALT, 0x83), "ALPHA": (CTRL | SHIFT, 0x81),
    "X,T,θ,n": (ALT, 0x7F), "STAT": (CTRL, 0x80),
    "MATH": (CTRL | SHIFT, 0x80), "FRAC": (CTRL, 0x83),
    "PRGM": (CTRL, 0x7F), "VARS": (SHIFT, 0x82),
    "CLEAR": (SHIFT, 0x80), "x⁻¹": (SHIFT, 0x7F),
    "SIN": (CTRL, 0x82), "COS": (SHIFT, 0x83),
    "TAN": (SHIFT, 0x81), "x²": (ALT, 0x83),
    ",": (CTRL, 0x81), "(": (SHIFT | ALT, 0x82),
    ")": (SHIFT | ALT, 0x81), "LOG": (SHIFT, 0x7D),
    "7": (ALT, 0x82), "8": (CTRL | ALT, 0x7E),
    "9": (CTRL | SHIFT, 0x7D), "LN": (ALT, 0x81),
    "4": (CTRL | ALT, 0x7D), "5": (SHIFT | ALT, 0x7F),
    "6": (0, 0x81), "STO→": (ALT, 0x80),
    "1": (SHIFT, 0x7E), "2": (0, 0x7F),
    "3": (0, 0x80), "TOGGLE": (CTRL | SHIFT, 0x7F),
    "0": (CTRL | SHIFT, 0x7E), ".": (0, 0x82),
    "(−)": (SHIFT | ALT, 0x83),
}

KEYS = {
    "ENTER": 0x0D, "ESC": 0x1B, "TAB": 0x09, "SPACE": 0x20,
    "LEFT": 0x25, "UP": 0x26, "RIGHT": 0x27, "DOWN": 0x28,
    "DELETE": 0x2E, "BACKSPACE": 0x08,
}
KEYS.update({f"F{number}": 0x6F + number for number in range(1, 25)})
MODIFIER_VKS = ((CTRL, 0x11), (SHIFT, 0x10), (ALT, 0x12))
INPUT_KEYBOARD = 1
KEYEVENTF_KEYUP = 0x0002
ENUM_WINDOWS_CALLBACK = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD),
                ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD),
                ("dwExtraInfo", ctypes.c_size_t)]


class INPUT_UNION(ctypes.Union):
    # INPUT's union is sized for MOUSEINPUT, even for keyboard events.
    _fields_ = [("ki", KEYBDINPUT), ("padding", ctypes.c_byte * (32 if ctypes.sizeof(ctypes.c_void_p) == 8 else 24))]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wintypes.DWORD), ("data", INPUT_UNION)]


def parse_shortcut(value: str) -> tuple[int, int]:
    parts = [part.strip().upper() for part in value.split("+")]
    if not parts or any(not part for part in parts):
        raise ValueError(f"invalid shortcut: {value!r}")
    modifiers = 0
    for part in parts[:-1]:
        flag = {"CTRL": CTRL, "CONTROL": CTRL, "SHIFT": SHIFT, "ALT": ALT}.get(part)
        if flag is None or modifiers & flag:
            raise ValueError(f"invalid shortcut modifier: {part!r}")
        modifiers |= flag
    key = parts[-1]
    vk = KEYS.get(key)
    if vk is None and len(key) == 1 and key.isascii() and key.isalnum():
        vk = ord(key)
    if vk is None:
        raise ValueError(f"unsupported shortcut key: {key!r}")
    return modifiers, vk


def read_bindings(path: Path | None) -> dict[str, dict[str, str | None]]:
    bindings = json.loads(path.read_text(encoding="utf-8")) if path else DEFAULT_BINDINGS
    if not isinstance(bindings, dict) or not bindings:
        raise ValueError("bindings must be a nonempty JSON object")
    for name, action in bindings.items():
        if name not in SMARTPAD_KEYS:
            raise ValueError(f"unsupported SmartPad key {name!r}; use --list-keys")
        if not isinstance(action, dict) or not isinstance(action.get("shortcut"), str):
            raise ValueError(f"{name}: expected an object with a shortcut string")
        parse_shortcut(action["shortcut"])
        if action.get("window") is not None and not isinstance(action["window"], str):
            raise ValueError(f"{name}: window must be a title substring or null")
    return bindings


def find_window(user32: ctypes.WinDLL, title_part: str) -> int | None:
    matches: list[int] = []
    @ENUM_WINDOWS_CALLBACK
    def visit(hwnd: int, _: int) -> bool:
        if not user32.IsWindowVisible(hwnd):
            return True
        length = user32.GetWindowTextLengthW(hwnd)
        if length:
            title = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, title, length + 1)
            if title_part.casefold() in title.value.casefold():
                matches.append(hwnd)
        return True

    user32.EnumWindows(visit, 0)
    return matches[0] if matches else None


def send_shortcut(user32: ctypes.WinDLL, shortcut: str) -> None:
    modifiers, vk = parse_shortcut(shortcut)
    sequence = [modifier_vk for flag, modifier_vk in MODIFIER_VKS if modifiers & flag]
    sequence.append(vk)
    events = [(key, 0) for key in sequence] + [(key, KEYEVENTF_KEYUP) for key in reversed(sequence)]
    inputs = (INPUT * len(events))(*(INPUT(INPUT_KEYBOARD, INPUT_UNION(ki=KEYBDINPUT(key, 0, flags, 0, 0)))
                                      for key, flags in events))
    sent = user32.SendInput(len(inputs), inputs, ctypes.sizeof(INPUT))
    if sent != len(inputs):
        raise OSError(ctypes.get_last_error(), "SendInput failed")


def wait_for_release(user32: ctypes.WinDLL, modifiers: int, vk: int) -> bool:
    watched = [vk] + [key for flag, key in MODIFIER_VKS if modifiers & flag]
    deadline = time.monotonic() + 3.0
    while time.monotonic() < deadline:
        if all(not user32.GetAsyncKeyState(key) & 0x8000 for key in watched):
            return True
        time.sleep(0.015)
    return False


def run(bindings: dict[str, dict[str, str | None]], observe: bool = False) -> None:
    user32 = ctypes.WinDLL("user32", use_last_error=True)
    user32.RegisterHotKey.argtypes = (wintypes.HWND, ctypes.c_int, wintypes.UINT, wintypes.UINT)
    user32.RegisterHotKey.restype = wintypes.BOOL
    user32.UnregisterHotKey.argtypes = (wintypes.HWND, ctypes.c_int)
    user32.SendInput.argtypes = (wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int)
    user32.SendInput.restype = wintypes.UINT
    user32.GetAsyncKeyState.argtypes = (ctypes.c_int,)
    user32.GetAsyncKeyState.restype = ctypes.c_short
    user32.IsWindowVisible.argtypes = (wintypes.HWND,)
    user32.GetWindowTextLengthW.argtypes = (wintypes.HWND,)
    user32.GetWindowTextW.argtypes = (wintypes.HWND, wintypes.LPWSTR, ctypes.c_int)
    user32.EnumWindows.argtypes = (ENUM_WINDOWS_CALLBACK, wintypes.LPARAM)
    user32.SetForegroundWindow.argtypes = (wintypes.HWND,)
    user32.SetForegroundWindow.restype = wintypes.BOOL
    registered: list[int] = []
    try:
        for hotkey_id, (name, action) in enumerate(bindings.items(), start=1):
            modifiers, vk = SMARTPAD_KEYS[name]
            if not user32.RegisterHotKey(None, hotkey_id, modifiers | NOREPEAT, vk):
                raise OSError(ctypes.get_last_error(), f"could not register {name}; another app may use its chord")
            registered.append(hotkey_id)
            target = f"window containing {action['window']!r}" if action.get("window") else "active window"
            print(f"{name} -> {action['shortcut']} in {target}", flush=True)
        mode = "observe only" if observe else "active"
        print(f"SmartPad macro pad {mode}. Open SmartPad on the calculator; press Ctrl+C here to stop.", flush=True)
        message = wintypes.MSG()
        while True:
            if not user32.PeekMessageW(ctypes.byref(message), None, 0, 0, 0x0001):
                time.sleep(0.025)
                continue
            if message.message != 0x0312:  # WM_HOTKEY
                continue
            hotkey_id = message.wParam
            name = list(bindings)[hotkey_id - 1]
            action = bindings[name]
            modifiers, vk = SMARTPAD_KEYS[name]
            if not wait_for_release(user32, modifiers, vk):
                print(f"{name}: key remained held; skipped", flush=True)
                continue
            if observe:
                print(f"{name}: received", flush=True)
                continue
            target_title = action.get("window")
            if target_title:
                hwnd = find_window(user32, target_title)
                if hwnd is None:
                    print(f"{name}: no visible window containing {target_title!r}", flush=True)
                    continue
                if not user32.SetForegroundWindow(hwnd):
                    print(f"{name}: could not activate {target_title!r}", flush=True)
                    continue
                time.sleep(0.07)
            send_shortcut(user32, action["shortcut"])
            print(f"{name}: sent {action['shortcut']}", flush=True)
    finally:
        for hotkey_id in registered:
            user32.UnregisterHotKey(None, hotkey_id)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, help="JSON object mapping calculator keys to shortcut/window actions")
    parser.add_argument("--list-keys", action="store_true", help="show supported calculator key names")
    parser.add_argument("--observe", action="store_true", help="print mapped key presses without sending shortcuts")
    args = parser.parse_args()
    if args.list_keys:
        print("\n".join(SMARTPAD_KEYS))
        return 0
    if sys.platform != "win32":
        parser.error("this macro pad uses Windows global hotkeys")
    try:
        run(read_bindings(args.config), observe=args.observe)
    except (ValueError, OSError) as exc:
        parser.exit(1, f"smartpad_macropad: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

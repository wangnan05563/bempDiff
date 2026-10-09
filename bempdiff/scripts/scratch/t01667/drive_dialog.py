#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T00926 原生对话框驱动：查找「比对仍在进行」MessageBox 并点击指定按钮。

用法:
  python drive_dialog.py cancel    # WM_CLOSE = 取消关闭（cancelId=0）
  python drive_dialog.py force     # 找「强制关闭」按钮发 BM_CLICK
  python drive_dialog.py find      # 仅查找并列出窗口/按钮文本
"""
import ctypes
import ctypes.wintypes as wt
import sys
import time

user32 = ctypes.windll.user32
EnumWindows = user32.EnumWindows
EnumWindowsProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)

WM_CLOSE = 0x0010
BM_CLICK = 0x00F5


def window_title(hwnd):
    n = user32.GetWindowTextLengthW(hwnd)
    buf = ctypes.create_unicode_buffer(n + 1)
    user32.GetWindowTextW(hwnd, buf, n + 1)
    return buf.value


def window_class(hwnd):
    buf = ctypes.create_unicode_buffer(256)
    user32.GetClassNameW(hwnd, buf, 256)
    return buf.value


def is_visible(hwnd):
    return bool(user32.IsWindowVisible(hwnd))


def find_dialog():
    """找标题为「比对仍在进行」的 #32770 对话框"""
    found = []

    def cb(hwnd, lp):
        if is_visible(hwnd) and window_class(hwnd) == '#32770':
            t = window_title(hwnd)
            if '比对仍在进行' in t:
                found.append((hwnd, t))
        return True

    EnumWindows(EnumWindowsProc(cb), 0)
    return found


def child_buttons(hwnd):
    """列出对话框的全部子按钮（含文本）"""
    out = []

    def cb(h, lp):
        if window_class(h) == 'Button':
            out.append((h, window_title(h)))
        return True

    EnumChildWindows = user32.EnumChildWindows
    EnumChildWindows(hwnd, EnumWindowsProc(cb), 0)
    return out


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else 'find'
    dlg = find_dialog()
    print('dialogs:', [(hex(h), t) for h, t in dlg])
    if not dlg:
        print('RESULT: DIALOG_NOT_FOUND')
        return 1
    hwnd, title = dlg[0]
    if action == 'find':
        for bh, bt in child_buttons(hwnd):
            print('  button:', hex(bh), repr(bt))
        print('RESULT: FOUND')
        return 0
    if action == 'cancel':
        user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)
        time.sleep(0.5)
        print('RESULT: WM_CLOSE sent (=> 取消关闭)')
        return 0
    if action == 'force':
        for bh, bt in child_buttons(hwnd):
            if '强制关闭' in bt:
                user32.SendMessageW(bh, BM_CLICK, 0, 0)
                print('RESULT: CLICKED 强制关闭')
                return 0
        print('RESULT: BUTTON_NOT_FOUND; buttons =', [bt for _, bt in child_buttons(hwnd)])
        return 1
    print('unknown action')
    return 2


if __name__ == '__main__':
    sys.exit(main())

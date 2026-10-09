#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""创建派生单：关于页「已是最新」仍显示「立即更新」"""
import json
import os
import subprocess
import sys

payload = {
    "projectName": "compare一期",
    "title": "【AI回写待审核】关于页「已是最新」状态仍显示「立即更新」按钮，点击会降级安装旧版",
    "description": (
        "来源：T01542（T00877 复验观察）/ T01506（R9）\n\n"
        "## 现象（CDP 驱动真实应用实测，2026-10-10）\n\n"
        "当前版本 0.1.2026100902 > 最新 Release 0.1.2026100901 时，配置中心 → 关于 → 检查更新："
        "绿色「已是最新」横幅与「立即更新」按钮**同时显示**。\n\n"
        "## 根因定位\n\n"
        "`webui/src/components/About.vue` L185：`<button v-if=\"result.latest.downloadUrl && canOneClick()\">`"
        " —— 按钮显隐只看「最新 Release 是否有下载 URL」，**未参考 `result.upToDate`**"
        "（L171-177 的横幅才区分已最新/有新版）。\n\n"
        "## 影响\n\n"
        "已最新的用户点「立即更新」会把 0.1.2026100901（更旧）安装回本机（降级），"
        "一键更新流程会正常执行——存在误降级风险。\n\n"
        "## 修复建议\n\n"
        "`v-if` 增加 `result.upToDate === false`（仅确有新版时显示「立即更新」；「前往下载页」链接可保留）。\n\n"
        "## 验收\n\n"
        "0902 实例检查更新后：「已是最新」横幅出现且无「立即更新」按钮；"
        "人工降版本（或 mock upToDate=false）时按钮恢复显示。"
    ),
    "derivedFrom": "T01542",
    "priority": "normal",
}

script = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', '..', '..',
                      'Users', 'hspcadmin')  # 占位，不使用
cmd = [
    sys.executable,
    r'C:\Users\hspcadmin\.workbuddy\skills\mtask\scripts\mcp-direct-call.py',
    'raw', 'mtask_create_task', json.dumps(payload, ensure_ascii=False),
]
p = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
print(p.stdout[-1500:])
if p.returncode != 0:
    print('STDERR:', p.stderr[-800:])

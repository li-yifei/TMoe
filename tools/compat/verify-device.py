#!/usr/bin/env python3
"""Verify the built module against installed standalone Telegram, without opening its UI.

Usage: python3 tools/compat/verify-device.py --serial 11xlr:45454 --sdk /path/to/android-sdk
Run :app:assembleModernDebug first. Requires JDK 17, adb, and the resolved Gradle dependencies.
"""
import argparse
from pathlib import Path
import os
import shlex
import subprocess
import tempfile


def run(*args, **kwargs):
    return subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--package", default="org.telegram.messenger.web")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    caches = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"

    def jar(group, artifact, version):
        matches = list((caches / group / artifact / version).glob("*/*.jar"))
        if len(matches) != 1:
            raise RuntimeError(f"Resolve {group}:{artifact}:{version} through Gradle first")
        return matches[0]

    dexlib = jar("org.smali", "dexlib2", "2.5.2")
    xposed = jar("de.robv.android.xposed", "api", "82")
    android = args.sdk / "platforms/android-35/android.jar"
    d8 = args.sdk / "build-tools/35.0.0/d8"
    classes = root / "app/build/intermediates/javac/modernDebug/compileModernDebugJavaWithJavac/classes"
    module = root / "app/build/outputs/apk/modern/debug/app-modern-debug.apk"
    adb = ["adb", "-s", args.serial]
    paths = run(*adb, "shell", "pm", "path", args.package, capture_output=True, text=True).stdout.splitlines()
    host_paths = [p.removeprefix("package:").strip() for p in paths if p.startswith("package:")]
    if not host_paths:
        raise RuntimeError("Target Telegram is not installed")
    with tempfile.TemporaryDirectory(prefix="tmoe-art-") as temp:
        temp = Path(temp)
        compiled = temp / "classes"
        compiled.mkdir()
        run("javac", "--release", "17", "-cp", os.pathsep.join(map(str, [android, classes, dexlib])),
            "-d", compiled, root / "tools/compat/HostCompatibilityCheck.java")
        probe = temp / "probe.jar"
        run("jar", "cf", probe, "-C", compiled, ".")
        dex = temp / "probe.zip"
        run(d8, "--min-api", "26", "--lib", android, "--output", dex, probe, xposed)
        remote = "/data/local/tmp/tmoe-compat-probe"
        run(*adb, "shell", "mkdir", "-p", remote)
        run(*adb, "push", module, remote + "/module.apk", stdout=subprocess.DEVNULL)
        run(*adb, "push", dex, remote + "/probe.zip", stdout=subprocess.DEVNULL)
        classpath = ":".join([remote + "/probe.zip", remote + "/module.apk", *host_paths])
        command = "CLASSPATH=" + shlex.quote(classpath) + " app_process /system/bin " + \
            "cc.ioctl.tmoe.rtti.HostCompatibilityCheck " + shlex.quote(host_paths[0]) + " " + shlex.quote(remote)
        run(*adb, "shell", command)


if __name__ == "__main__":
    main()

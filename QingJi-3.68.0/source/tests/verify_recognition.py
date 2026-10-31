"""Run recognition regression tests with the application build configuration."""
import argparse
from pathlib import Path
import os
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle", required=True, type=Path)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--device-apk", action="store_true", help="Also package instrumentation tests; does not run them")
    args = parser.parse_args()
    env = os.environ.copy()
    env["JAVA_HOME"], env["ANDROID_HOME"] = str(args.jdk), str(args.sdk)
    tasks = ["testDebugUnitTest"]
    if args.device_apk:
        tasks.append("assembleDebugAndroidTest")
    subprocess.run([str(args.gradle), "-p", str(Path(__file__).resolve().parents[1]), *tasks, "--console=plain"], env=env, check=True)


if __name__ == "__main__":
    main()

"""Compile and replay synthetic cases; optionally package the unsigned application and device tests."""
import argparse
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import zipfile


def run(*args, cwd=None):
    subprocess.run([str(arg) for arg in args], check=True, cwd=cwd)


def archive(folder, output):
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as jar:
        for item in folder.rglob("*.class"):
            jar.write(item, item.relative_to(folder).as_posix())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--android-jar", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path)
    parser.add_argument("--r8", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--package-only", action="store_true", help="Reuse completed test results; build artifacts only")
    args = parser.parse_args()
    if args.output and not (args.aapt2 and args.r8):
        parser.error("--output requires --aapt2 and --r8")
    root = Path(__file__).resolve().parents[1]
    java = args.jdk / "bin" / ("java.exe" if os.name == "nt" else "java")
    javac = args.jdk / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    with tempfile.TemporaryDirectory(prefix="qingji-recognition-") as temp:
        temp = Path(temp)
        classes, tests = temp / "classes", temp / "tests"
        classes.mkdir()
        tests.mkdir()
        run(javac, "-g:none", "-source", "8", "-target", "8", "-encoding", "UTF-8",
            "-cp", args.android_jar, "-d", classes, *sorted((root / "src").rglob("*.java")))
        classpath = os.pathsep.join(map(str, (classes, args.android_jar)))
        run(javac, "-source", "8", "-target", "8", "-encoding", "UTF-8", "-cp", classpath,
            "-d", tests, *sorted((root / "tests").glob("*.java")))
        if not args.package_only:
            run(java, "-cp", os.pathsep.join((str(tests), classpath)), "me.minis.ledger.RecognitionTest")
        if not args.output:
            return
        args.output.mkdir(parents=True, exist_ok=True)
        app_jar, test_jar = temp / "app.jar", temp / "tests.jar"
        archive(classes, app_jar)
        archive(tests, test_jar)
        dex = temp / "dex"
        dex.mkdir()
        run(java, "-cp", args.r8, "com.android.tools.r8.R8", "--release", "--lib", args.android_jar,
            "--min-api", "26", "--pg-conf", root / "tools/release-rules.pro", "--output", dex, app_jar)
        # Windows aapt2 does not reliably accept non-ASCII absolute input/output paths.
        run(args.aapt2, "compile", "--dir", "res", "-o", temp / "res.zip", cwd=root)
        apk = temp / "QingJi-3.47.0-unsigned.apk"
        run(args.aapt2, "link", "-o", apk, "--manifest", "AndroidManifest.xml",
            "-I", args.android_jar, "-A", "assets-v2", temp / "res.zip", cwd=root)
        with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as output:
            for item in dex.glob("*.dex"):
                output.write(item, item.name)
        # Device checks use the same unshrunk application classes, in a separate test package.
        # This avoids reflective/package-access assumptions against a release-obfuscated APK.
        test_dex = temp / "test-dex"
        test_dex.mkdir()
        run(java, "-cp", args.r8, "com.android.tools.r8.D8", "--lib", args.android_jar,
            "--min-api", "26", "--output", test_dex, app_jar, test_jar)
        manifest = temp / "tests.xml"
        manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="me.minis.ledger.recognitiontests">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
<application android:label="轻记识别测试"/>
<instrumentation android:name="me.minis.ledger.RecognitionDeviceTest" android:targetPackage="me.minis.ledger.recognitiontests"/>
</manifest>''', encoding="utf-8")
        device_apk = temp / "QingJi-recognition-tests-unsigned.apk"
        run(args.aapt2, "link", "-o", device_apk, "--manifest", manifest, "-I", args.android_jar)
        with zipfile.ZipFile(device_apk, "a", zipfile.ZIP_DEFLATED) as output:
            for item in test_dex.glob("*.dex"):
                output.write(item, item.name)
        shutil.copyfile(apk, args.output / apk.name)
        shutil.copyfile(device_apk, args.output / device_apk.name)
        print(f"Unsigned application: {args.output / apk.name}\nUnsigned isolated device tests: {args.output / device_apk.name}")
        print("Device checks are packaged, not executed; they do not open the user's ledger.")


if __name__ == "__main__":
    main()

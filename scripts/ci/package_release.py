"""Require the installed app's signing certificate and publish a verifiable APK."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess


def main():
    expected = os.environ.get("ANDROID_SIGNING_CERT_SHA256", "").lower().replace(":", "")
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit("Configure the repository variable ANDROID_SIGNING_CERT_SHA256")
    directory = Path("app/build/outputs/apk/release")
    metadata = json.loads((directory / "output-metadata.json").read_text())
    element, = metadata["elements"]
    apk = directory / element["outputFile"]
    verifier = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0/apksigner"
    result = subprocess.run([str(verifier), "verify", "--verbose", "--print-certs", str(apk)],
                            capture_output=True, text=True, check=True)
    fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", result.stdout)
    if fingerprints != [expected]:
        raise SystemExit("Release signing certificate differs from the installed official version")
    if metadata["applicationId"] != "org.freeyourgadget.weightrecorder":
        raise SystemExit("Unexpected release application ID")
    version = element["versionName"]
    if not re.fullmatch(r"[A-Za-z0-9.+_-]+", version):
        raise SystemExit("Unexpected release version name")
    destination = Path("dist")
    destination.mkdir(exist_ok=True)
    name = f"weight-recorder-{version}-release.apk"
    shutil.copyfile(apk, destination / name)
    checksum = hashlib.sha256(apk.read_bytes()).hexdigest()
    (destination / "SHA256SUMS.txt").write_text(f"{checksum}  {name}\n")
    info = {
        "application_id": metadata["applicationId"],
        "version_name": version, "version_code": element["versionCode"],
        "apk_sha256": checksum, "signing_certificate_sha256": expected,
        "commit": os.environ["GITHUB_SHA"], "run_id": os.environ["GITHUB_RUN_ID"],
    }
    (destination / "release.json").write_text(json.dumps(info, indent=2) + "\n")
    artifact = f"weight-recorder-{version}-release-{os.environ['GITHUB_RUN_ID']}-{os.environ['GITHUB_RUN_ATTEMPT']}"
    with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
        stream.write(f"artifact_name={artifact}\n")
    print(f"Verified {name}; certificate SHA-256: {expected}; APK SHA-256: {checksum}")


if __name__ == "__main__":
    main()

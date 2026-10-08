"""Decode the Actions secret, build with the existing release key, then remove it."""
import base64
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    required = (
        "ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD", "RUNNER_TEMP",
    )
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise SystemExit("Missing Actions secrets/environment: " + ", ".join(missing))
    descriptor, path = tempfile.mkstemp(prefix="weight-recorder-release-", suffix=".jks", dir=os.environ["RUNNER_TEMP"])
    keystore = Path(path)
    env = os.environ.copy()
    env["ANDROID_KEYSTORE_PATH"] = str(keystore.resolve())
    encoded = env.pop("ANDROID_KEYSTORE_BASE64")
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(base64.b64decode(encoded, validate=True))
        subprocess.run(["./gradlew", "--no-daemon", ":app:assembleRelease"], env=env, check=True)
    finally:
        keystore.unlink(missing_ok=True)


if __name__ == "__main__":
    main()

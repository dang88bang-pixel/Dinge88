#!/usr/bin/env python3
import os
import sys
import base64
import subprocess

diag_file = "ci-logs/keystore-decode.log"
os.makedirs("ci-logs", exist_ok=True)

with open(diag_file, "w") as log:
    raw_b64 = os.environ.get("KEYSTORE_BASE64", "")
    log.write(f"Raw len: {len(raw_b64)}\n")
    clean = "".join(c for c in raw_b64 if c in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=")
    log.write(f"Clean len: {len(clean)}\n")
    log.write(f"First 20 chars: {clean[:20]}\n")
    log.write(f"Last 20 chars: {clean[-20:]}\n")

    if not clean:
        log.write("ERROR: clean base64 is empty\n")
        sys.exit(1)

    rem = len(clean) % 4
    log.write(f"Remainder mod 4: {rem}\n")
    if rem == 2:
        clean += "=="
    elif rem == 3:
        clean += "="
    elif rem == 1:
        log.write("WARNING: Remainder 1 cannot be padded directly\n")

    try:
        decoded = base64.b64decode(clean)
        log.write(f"Decoded bytes: {len(decoded)}\n")
        with open("secureguard-keystore.p12", "wb") as f:
            f.write(decoded)
        log.write("Written to secureguard-keystore.p12\n")
    except Exception as e:
        log.write(f"Decode exception: {e}\n")
        sys.exit(2)

    # Versuche openssl/keytool zur Verifizierung
    cmd = ["openssl", "pkcs12", "-info", "-in", "secureguard-keystore.p12", "-password", "pass:" + os.environ.get("KEYSTORE_PASSWORD", ""), "-noout"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    log.write(f"OpenSSL test rc: {res.returncode}\n")
    if res.stderr:
        log.write(f"OpenSSL stderr: {res.stderr[:200]}\n")
    if res.returncode != 0:
        # Fallback prüfen ob es JKS ist
        cmd_jks = ["keytool", "-list", "-keystore", "secureguard-keystore.p12", "-storepass", os.environ.get("KEYSTORE_PASSWORD", "")]
        res_jks = subprocess.run(cmd_jks, capture_output=True, text=True)
        log.write(f"Keytool test rc: {res_jks.returncode}\n")
        if res_jks.stderr:
            log.write(f"Keytool stderr: {res_jks.stderr[:200]}\n")
        if res_jks.returncode == 0:
            os.rename("secureguard-keystore.p12", "secureguard-keystore.jks")
            log.write("Renamed to secureguard-keystore.jks (was JKS format)\n")
            sys.exit(0)
        sys.exit(3)
    sys.exit(0)

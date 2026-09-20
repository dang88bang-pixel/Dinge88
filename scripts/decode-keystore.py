#!/usr/bin/env python3
import os
import sys
import base64

raw_b64 = os.environ.get('KEYSTORE_BASE64', '')
clean = ''.join(c for c in raw_b64 if c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=')
if not clean:
    sys.exit(1)

rem = len(clean) % 4
if rem == 2:
    clean += '=='
elif rem == 3:
    clean += '='

try:
    decoded = base64.b64decode(clean)
    with open('secureguard-keystore.p12', 'wb') as f:
        f.write(decoded)
    sys.exit(0)
except Exception as e:
    sys.stderr.write(str(e) + '\n')
    sys.exit(2)

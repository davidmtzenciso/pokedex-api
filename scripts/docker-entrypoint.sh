#!/bin/sh
# The application will not start without an ES256 keystore, and keys/ is excluded from the
# build context on purpose — baking one in would give every image built from this Dockerfile
# the same signing key.
#
# So: if the configured keystore is absent, generate a throwaway one at startup. That is what
# makes `docker compose up` work with zero manual steps. In any real deployment the keystore
# is mounted and this branch never runs.
set -eu

KEYSTORE_PATH="${JWT_KEYSTORE_PATH_FILE:-/app/keys/pokedex-dev.p12}"

if [ ! -f "$KEYSTORE_PATH" ]; then
  echo "no keystore at $KEYSTORE_PATH — generating a throwaway development key"
  keytool -genkeypair -alias "${JWT_KEY_ALIAS:-pokedex-dev}" -keyalg EC -groupname secp256r1 \
    -sigalg SHA256withECDSA -validity 3650 -storetype PKCS12 \
    -keystore "$KEYSTORE_PATH" -storepass "$JWT_KEYSTORE_PASSWORD" \
    -dname "CN=pokedex-api-dev, O=ElatusDev, C=US" >/dev/null
fi

# exec, so the JVM replaces this shell as PID 1 and still receives SIGTERM
exec java -XX:MaxRAMPercentage=75 -jar application.jar "$@"

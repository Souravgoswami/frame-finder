#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
readonly KEYSTORE_PATH="${1:-${PROJECT_DIR}/framefinder-release.jks}"
readonly ENV_PATH="${PROJECT_DIR}/.framefinder-signing.env"
readonly KEY_ALIAS="${FRAMEFINDER_KEY_ALIAS:-framefinder-release}"
readonly CERTIFICATE_NAME="${FRAMEFINDER_CERTIFICATE_NAME:-CN=Frame Finder, OU=Android, O=Frame Finder, C=IN}"

if ! command -v keytool >/dev/null 2>&1; then
    echo "keytool was not found. Install JDK 17 and try again." >&2
    exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl was not found. Install OpenSSL and try again." >&2
    exit 1
fi

if [[ -e "${KEYSTORE_PATH}" ]]; then
    echo "Refusing to replace the existing keystore: ${KEYSTORE_PATH}" >&2
    exit 1
fi

if [[ -e "${ENV_PATH}" ]]; then
    echo "Refusing to replace the existing signing environment: ${ENV_PATH}" >&2
    exit 1
fi

FRAMEFINDER_GENERATED_STORE_PASSWORD="$(openssl rand -hex 32)"
FRAMEFINDER_GENERATED_KEY_PASSWORD="$(openssl rand -hex 32)"
export FRAMEFINDER_GENERATED_STORE_PASSWORD
export FRAMEFINDER_GENERATED_KEY_PASSWORD

keystore_created=false
cleanup() {
    local status=$?
    trap - EXIT
    unset FRAMEFINDER_GENERATED_STORE_PASSWORD FRAMEFINDER_GENERATED_KEY_PASSWORD
    if [[ ${status} -ne 0 && "${keystore_created}" == "true" ]]; then
        rm -f -- "${KEYSTORE_PATH}"
    fi
    exit "${status}"
}
trap cleanup EXIT

mkdir -p "$(dirname "${KEYSTORE_PATH}")"
keytool -genkeypair \
    -keystore "${KEYSTORE_PATH}" \
    -storetype JKS \
    -alias "${KEY_ALIAS}" \
    -keyalg RSA \
    -keysize 4096 \
    -sigalg SHA256withRSA \
    -validity 10000 \
    -dname "${CERTIFICATE_NAME}" \
    -storepass:env FRAMEFINDER_GENERATED_STORE_PASSWORD \
    -keypass:env FRAMEFINDER_GENERATED_KEY_PASSWORD
keystore_created=true

umask 077
{
    printf 'export FRAMEFINDER_KEYSTORE=%q\n' "${KEYSTORE_PATH}"
    printf 'export FRAMEFINDER_STORE_PASSWORD=%q\n' "${FRAMEFINDER_GENERATED_STORE_PASSWORD}"
    printf 'export FRAMEFINDER_KEY_ALIAS=%q\n' "${KEY_ALIAS}"
    printf 'export FRAMEFINDER_KEY_PASSWORD=%q\n' "${FRAMEFINDER_GENERATED_KEY_PASSWORD}"
} > "${ENV_PATH}"
chmod 600 "${KEYSTORE_PATH}" "${ENV_PATH}"

unset FRAMEFINDER_GENERATED_STORE_PASSWORD FRAMEFINDER_GENERATED_KEY_PASSWORD
trap - EXIT

echo "Created ${KEYSTORE_PATH}"
echo "Created ${ENV_PATH}"
echo
echo "Back up both files securely. Losing the key can prevent future app updates."
echo "Load the signing environment with:"
echo "  source ./.framefinder-signing.env"

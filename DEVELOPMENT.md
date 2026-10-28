# Frame Finder development guide

This guide explains how to build Frame Finder from a fresh clone and how to create packages using your own signing key. The private key used for official Frame Finder releases is not included in the repository and is never required for ordinary development.

## Requirements

- JDK 17
- Android SDK 35
- Android SDK Build Tools
- Git LFS
- An Android device or emulator for installation

## Download the bundled models

Frame Finder stores its ONNX model files with Git LFS. Install Git LFS before cloning, or initialize it and fetch the model files after cloning:

```bash
git lfs install
git lfs pull
```

Do not build from the small LFS pointer files. A successful `git lfs pull` replaces them with the complete models required by Frame Finder.

## Local files that are not included in Git

A fresh clone intentionally does not contain the following local files:

| File | Purpose | How it is created |
| --- | --- | --- |
| `local.properties` | Tells Gradle where the Android SDK is installed | Android Studio normally creates it, or create it manually as shown below |
| `framefinder-release.jks` | Holds a developer's private release signing key | Created by `scripts/create-release-keystore.sh`, by `keytool`, or supplied from a secure backup |
| `.framefinder-signing.env` | Supplies the keystore path, alias, and passwords to Gradle | Created by the signing script or manually from the template below |

These files are deliberately ignored by Git. Do not copy real passwords or private keys into a tracked file.

### Configure the Android SDK

Android Studio normally writes `local.properties` when it opens the project. For a command line setup, create `local.properties` in the project root with the absolute SDK path:

```properties
sdk.dir=/home/your-name/Android/Sdk
```

If the Android SDK environment variables are already configured correctly, Gradle may not need this file.

## Debug builds

Debug builds use Android's automatically generated debug certificate. You do not need a JKS file or any release credentials.

Debug builds use the application ID `com.souravgoswami.framefinder.debug` and the label `Frame Finder Debug`. They install alongside signed releases, so local testing cannot replace a release installation or erase its private index.

The default build targets x86_64 for Genymotion:

```bash
./gradlew installDebug
```

To build for a typical ARM64 phone:

```bash
./gradlew assembleDebug -PframefinderAbis=arm64-v8a
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Run the JVM tests and debug lint checks with:

```bash
./gradlew testDebugUnitTest lintDebug
```

With an emulator or device connected, run the Android migration tests with:

```bash
./gradlew connectedDebugAndroidTest
```

## Release signing and key ownership

Android requires every release package to be signed. Each developer or distributor must use a key they control.

- The official Frame Finder signing key is private and is not stored in Git.
- A fresh clone has no access to the official key.
- The included generator creates a new, independent key for the current developer.
- A package signed with a developer's key cannot impersonate or update an official Frame Finder installation.
- Gradle refuses to create a release when signing credentials are absent or incomplete.

Official maintainers load the official credentials through the same environment-variable interface. Other developers should generate their own key as described below.

## Generate your own JKS key

### Recommended automatic setup

From the project root, run:

```bash
./scripts/create-release-keystore.sh
```

The generator creates:

```text
framefinder-release.jks
.framefinder-signing.env
```

The JKS contains a new 4096-bit RSA key. Strong store and key passwords are generated locally. The environment file contains the local keystore path, alias, and passwords. Both files are ignored by Git and are created with owner-only permissions.

The generated `.framefinder-signing.env` has this structure:

```bash
export FRAMEFINDER_KEYSTORE=/absolute/path/to/framefinder-release.jks
export FRAMEFINDER_STORE_PASSWORD=generated_store_password
export FRAMEFINDER_KEY_ALIAS=framefinder-release
export FRAMEFINDER_KEY_PASSWORD=generated_key_password
```

The actual generated values are shell escaped and must remain private. The file is absent from a fresh clone because committing it would expose everything required to sign an update.

The script never downloads, copies, or uses the official Frame Finder key. It also refuses to overwrite an existing key or signing environment.

To choose a different certificate identity or alias, set these values before running the generator:

```bash
export FRAMEFINDER_CERTIFICATE_NAME='CN=Your Name, OU=Android, O=Your Organization, C=IN'
export FRAMEFINDER_KEY_ALIAS='framefinder-release'
./scripts/create-release-keystore.sh
```

To place the JKS at another location, pass its path as the first argument:

```bash
./scripts/create-release-keystore.sh /secure/path/my-framefinder-key.jks
```

If you distribute packages signed with this key, securely back up both the JKS and its credentials. Losing them prevents you from publishing compatible updates to those installations. Anyone who obtains both files can sign an update as you.

### Manual setup with an existing JKS

If you already own a JKS, create `.framefinder-signing.env` in the project root and replace every placeholder below:

```bash
export FRAMEFINDER_KEYSTORE='/absolute/path/to/your-release-key.jks'
export FRAMEFINDER_STORE_PASSWORD='replace_with_the_keystore_password'
export FRAMEFINDER_KEY_ALIAS='replace_with_the_key_alias'
export FRAMEFINDER_KEY_PASSWORD='replace_with_the_key_password'
```

Use an absolute path so the file works regardless of the shell's current directory. Keep literal values quoted. If a value itself contains a single quote, use valid shell escaping or let the included generator create the file safely.

Restrict both files to the current user:

```bash
chmod 600 /absolute/path/to/your-release-key.jks .framefinder-signing.env
```

### Manual key generation without the script

The included script is safer and simpler, but the equivalent interactive `keytool` command is:

```bash
keytool -genkeypair \
  -keystore /absolute/path/to/framefinder-release.jks \
  -storetype JKS \
  -alias framefinder-release \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 10000 \
  -dname 'CN=Your Name, OU=Android, O=Your Organization, C=IN'
```

`keytool` asks for the store password and key password. After it finishes, create `.framefinder-signing.env` using the template above with those exact values.

## Load your signing environment

After creating or restoring both files, run this once in each terminal used for a release build:

```bash
source ./.framefinder-signing.env
```

The file exports:

- `FRAMEFINDER_KEYSTORE`
- `FRAMEFINDER_STORE_PASSWORD`
- `FRAMEFINDER_KEY_ALIAS`
- `FRAMEFINDER_KEY_PASSWORD`

The passwords are not hardcoded in Gradle files. Release commands disable Gradle's configuration cache so credentials are not persisted in cached build state.

Verify the environment before the first release build:

```bash
: "${FRAMEFINDER_KEYSTORE:?Missing FRAMEFINDER_KEYSTORE}"
: "${FRAMEFINDER_STORE_PASSWORD:?Missing FRAMEFINDER_STORE_PASSWORD}"
: "${FRAMEFINDER_KEY_ALIAS:?Missing FRAMEFINDER_KEY_ALIAS}"
: "${FRAMEFINDER_KEY_PASSWORD:?Missing FRAMEFINDER_KEY_PASSWORD}"
test -f "$FRAMEFINDER_KEYSTORE"
keytool -list \
  -keystore "$FRAMEFINDER_KEYSTORE" \
  -alias "$FRAMEFINDER_KEY_ALIAS" \
  -storepass:env FRAMEFINDER_STORE_PASSWORD
```

The final command should print the selected key entry without asking you to expose a password on the command line.

## Build a signed APK

For a typical ARM64 phone:

```bash
source ./.framefinder-signing.env
./gradlew --no-configuration-cache assembleRelease -PframefinderAbis=arm64-v8a
```

Output:

```text
app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

For an x86_64 emulator:

```bash
source ./.framefinder-signing.env
./gradlew --no-configuration-cache assembleRelease -PframefinderAbis=x86_64
```

### Build ARM64 and x86_64 together

Every `assembleRelease` invocation configures the same release variant and writes it to the same managed output directory. If one build requests only ARM64 and the next requests only x86_64, Gradle treats the previous APK as a stale output and removes it. The two builds are not separate permanent release archives.

To keep both architecture-specific APKs in the output directory, request both ABIs in one build:

```bash
source ./.framefinder-signing.env
./gradlew --no-configuration-cache assembleRelease \
  -PframefinderAbis=arm64-v8a,x86_64 \
  -PframefinderCompressModels=true
```

This produces:

```text
app/build/outputs/apk/release/app-arm64-v8a-release.apk
app/build/outputs/apk/release/app-x86_64-release.apk
```

If the ABIs must be built in separate invocations, copy each completed APK to a separate release archive before starting the next build. Gradle owns `app/build/outputs`, so files there can be replaced or removed by later builds and clean tasks.

To produce architecture-specific APKs plus a universal fallback:

```bash
source ./.framefinder-signing.env
./gradlew --no-configuration-cache assembleRelease \
  -PframefinderAbis=arm64-v8a,armeabi-v7a,x86_64 \
  -PframefinderUniversalApk=true
```

Release APKs are minified with R8, have unused resources removed, and use APK Signature Scheme v2 and v3.

ONNX packaging is selected automatically by build type:

- Debug builds keep models uncompressed for faster local packaging and faster first model preparation.
- Release builds compress models for a smaller APK or bundle.

To override the automatic rule for a comparison build, pass `framefinderCompressModels` explicitly. For example, this creates an uncompressed release:

```bash
source ./.framefinder-signing.env
./gradlew --no-configuration-cache assembleRelease \
  -PframefinderAbis=x86_64 \
  -PframefinderCompressModels=false
```

To test compressed models in a debug build:

```bash
./gradlew assembleDebug \
  -PframefinderAbis=x86_64 \
  -PframefinderCompressModels=true
```

Compression changes APK packaging, transfer size, and first model preparation time, not model quality or search results. Frame Finder expands the selected model into private storage before using it.

## Build an Android App Bundle

For Google Play or another store that accepts Android App Bundles:

```bash
source ./.framefinder-signing.env
./gradlew --no-configuration-cache bundleRelease
```

Output:

```text
app/build/outputs/bundle/release/app-release.aab
```

For an official Google Play release, enable Google Play App Signing. The locally protected key can then serve as the upload key while Google protects the app signing key used for delivered APKs.

An independent fork must use its own application ID before publishing to an app store. It cannot publish updates under `com.souravgoswami.framefinder` or replace packages signed with the official certificate.

## Installing independently signed packages

Android identifies an update by both application ID and signing certificate. A package signed with your new key cannot be installed over an official or debug build that uses a different certificate.

You can uninstall the existing package before installing your independently signed APK, but uninstalling removes Frame Finder's private database and preferences. Export the analysis data first if it is needed.

For a long-lived fork that should coexist with Frame Finder, assign a unique `applicationId` in `app/build.gradle` before building.

## CI signing

In CI, store the JKS as a protected secret file and configure the four `FRAMEFINDER_*` variables through the CI provider's secret storage. Do not commit a keystore, signing environment, password, or encoded key to the repository.

Pull requests and ordinary verification jobs should use debug tasks and therefore require no release secrets.

## Verify a release

Verify an APK before distributing it:

```bash
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

The output should report one signer and successful v2 and v3 verification.

Keep the R8 mapping file for each distributed version so crash traces can be decoded:

```text
app/build/outputs/mapping/release/mapping.txt
```

## Release checklist

1. Increase `versionCode` in `app/build.gradle` for every release.
2. Update `versionName` when the public version changes.
3. Run `./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest`.
4. Load the correct signing environment.
5. Build the signed APK or bundle.
6. Verify the signature and signer certificate.
7. Archive the exact JKS, credentials, APK or bundle, and R8 mapping file securely.

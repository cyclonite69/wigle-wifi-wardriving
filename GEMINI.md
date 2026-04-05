# ShadowCheck Collector (wigle-wifi-wardriving fork) — Gemini CLI Config

---

## 🚨 Repository Operating Mode

> This repo is operated as an **independent working fork**.
> Default ALL git, PR, and merge actions to **my fork (`origin`)**, NOT upstream.

---

## Project Overview

This is a hardened fork of the WiGLE Wireless Wardriving Android application,
modified to serve as a field data collection device for the ShadowCheck-Web
SIGINT forensics platform.

**Role in Ecosystem:**

* Collects WiFi, Bluetooth, BLE, and cellular observations in the field
* Stores data in a local SQLite database (wiglewifi.sqlite)
* Uploads the database to ShadowCheck-Web via S3 presigned URL pipeline
* Runs TrackerEngine for real-time follower detection

**Primary Technologies:**

* Platform: Android (Min SDK 24, Target/Compile SDK 36)
* Language: Java 8 (with desugaring enabled)
* Build System: Gradle 8.13.0
* Maps: Google Maps SDK + MapLibre GL (FOSS builds)
* Database: SQLite — DatabaseHelper.java manages all schema migrations
* Upload: S3 presigned URL pattern (NOT direct multipart POST)

---

## Git / GitHub Remote Safety Rules

### Hard rules

* NEVER open, suggest, or target a pull request against upstream (`wiglenet/main`) unless explicitly instructed
* NEVER use upstream as the default merge target
* ALWAYS assume `origin` is the primary repo and merge target
* NEVER assume upstream maintainer approval is required
* NEVER optimize changes for upstream acceptance unless explicitly asked
* If upstream is referenced in a prompt involving git or PRs, STOP and ask for clarification

### PR Targeting Rules

When suggesting PRs, ALWAYS use:

```bash
<feature-branch> -> origin/main
```

GitHub UI must be:

* base repository: **my fork**
* base branch: `main`
* compare repository: **my fork**
* compare branch: feature branch

### Merge Policy

Standard workflow:

1. Create or update feature branch in my fork
2. Push feature branch to my fork
3. Merge into **my fork’s `main`**
4. Push `main` to my fork

Do NOT:

* wait for upstream approval
* involve upstream CI/workflows
* require maintainer review

### Safe Default Git Commands

Start work:

```bash
git fetch origin
git checkout main
git pull --no-rebase origin main
git checkout -b <feature-branch>
```

Push branch:

```bash
git push -u origin <feature-branch>
```

Merge locally:

```bash
git fetch origin
git checkout main
git pull --no-rebase origin main
git merge <feature-branch>
git push origin main
```

### Forbidden Defaults

Unless explicitly instructed, NEVER:

* open PRs to `wiglenet/main`
* reference upstream as default merge destination
* suggest waiting for approval
* rely on upstream CI/workflows

### If uncertain

Default to:

* push to origin
* merge into origin/main
* avoid upstream entirely

---

## Directory Structure

* `wiglewifiwardriving/src/main/java/net/wigle/wigleandroid/`

  * `MainActivity.java` — Primary entry point, initializes TrackerEngine
  * `WigleService.java` — High-priority foreground service with WakeLock/WifiLock
  * `db/DatabaseHelper.java` — SQLite schema and all migrations — source of truth
  * `util/UrlConfig.java` — Endpoint and API key constants
  * `listener/` — WiFi, Bluetooth, Cell receivers
  * `ui/` — Fragment and UI components
  * `CollectorFragment.java` — SC Collector Mode minimalist UI
  * `ShadowCheckUploader.java` — S3 upload implementation
  * `TrackerEngine.java` — Follower detection heuristics
* `wiglewifiwardriving/src/main/res/` — Android resources
* `wiglewifiwardriving/src/main/AndroidManifest.xml` — App manifest
* `gradle/` — Gradle wrapper
* `build.gradle` — Root build config
* `wiglewifiwardriving/build.gradle` — App module build config
* `gradle.properties` — Project-wide Gradle properties
* `SHADOWCHECK.md` — Integration technical reference
* `FEATURES_MOD.md` — Custom feature summary
* `TERRAFORM_S3.md` — S3 infrastructure and upload pattern reference

---

## Schema — Source of Truth

**DatabaseHelper.java is the ONLY authoritative source for the DB version.**
Do not trust GEMINI.md, SHADOWCHECK.md, or FEATURES_MOD.md for version numbers.
Always read DatabaseHelper.java first when working on schema.

Current known columns in `route` table (verify against DatabaseHelper.java):

* case_id TEXT
* device_model TEXT
* device_brand TEXT
* os_release TEXT
* device_id TEXT
* barometer DOUBLE

---

## Build Commands

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
./gradlew clean
```

---

## Upload Architecture — Critical

The correct upload pattern is PRESIGNED URL, not direct multipart POST.

Flow:

1. Android app POSTs to `/api/v1/ingest/request-upload` on shadowcheck-web

   * Body: { fileName, case_id }
   * Headers: Authorization: Bearer {api_key}
   * Returns: { uploadUrl, s3Key }
2. Android app PUTs the binary sqlite file directly to uploadUrl
3. No AWS credentials ever touch the APK

The api_key must be sent in the Authorization header, NOT in the POST body.
Reference: TERRAFORM_S3.md for the complete server-side implementation.

Any prompt touching ShadowCheckUploader.java must use this pattern.
Never implement direct S3 credentials in the APK.
Never send api_key as a form field — header only.

---

## Hard Rules — No Exceptions

### Android Manifest

* NEVER modify AndroidManifest.xml without explicit instruction
* NEVER add permissions without explicit instruction and justification
* NEVER change minSdkVersion or targetSdkVersion without explicit instruction

### Build Config

* NEVER modify build.gradle dependency versions without checking compatibility with Min SDK 24 and Java 8 desugaring
* NEVER add a dependency without checking if it already exists
* NEVER modify gradle.properties without explicit instruction

### Schema

* NEVER modify DatabaseHelper.java schema without explicit instruction
* Any schema change MUST increment DATABASE_VERSION
* Any schema change MUST have a corresponding onUpgrade() migration case
* NEVER drop or rename an existing column — add new columns only

### Security

* NEVER put AWS credentials, API keys, or secrets in Java source files
* NEVER put secrets in gradle.properties
* API keys go in local.properties surfaced via BuildConfig
* NEVER send api_key in POST body — header only

### Git

* NEVER run git push without explicit approval
* NEVER run git commit without showing diff and message first
* NEVER use --force on any git operation

### Testing

* Run ./gradlew lint after every Java change
* Run ./gradlew test after any logic change
* Lint errors are a hard stop

---

## Approval Gates

Stop and wait for explicit approval before:

1. Any git commit
2. Any git push
3. Any AndroidManifest.xml change
4. Any DATABASE_VERSION increment
5. Any new dependency
6. Any change to UrlConfig.java
7. Any new Android permission

---

## Context Loading Order

When starting any task:

1. `wiglewifiwardriving/build.gradle`
2. `DatabaseHelper.java`
3. `AndroidManifest.xml`
4. Any file referenced in prompt

---

## Verification Pattern

1. Make change
2. Run lint
3. Run tests
4. Report result
5. STOP for approval

---

## Known Doc Inconsistencies

Ignore doc versions — always trust DatabaseHelper.java.

---

## Scope Discipline

You are NOT:

* Refactoring unrelated code
* Improving adjacent code
* Adding logging beyond scope
* Changing formatting unnecessarily
* Making git decisions without approval
* Pulling features from proposals unless instructed

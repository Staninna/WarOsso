# WarOsso: droid app

WarOsso is the owner's own Android app, built from `SPEC.md`. It follows the droid conventions for
branches, commit identity, signing and releases, loaded with `/droid`. It has no upstream: skip the
skill's fork, sync and upstream-PR workflows, and its `-fork.<n>` tags.

`SPEC.md` is the source of truth for behaviour. Where it is silent, take the simplest option and
name the choice in the commit message.

## Releases

- **A push to `my-main` is a release.** CI tags it `v<versionName>` and publishes a signed APK.
  Push only when the owner asks and the work is verified.
- **Every release push raises `versionName` and `versionCode`** in `app/build.gradle.kts`. CI fails
  with a "bump the version" error when the tag already exists on another commit. Pushed tags stay
  where they are; a failed release is fixed by pushing a fix with a new version.
- Pushes that only touch the `paths-ignore` list in `.github/workflows/release.yml`, or carry
  `[skip ci]`, release nothing.
- **Signing keys are hands-off.** Only the droid scripts `signing-key.sh` and `build-signed.sh`
  touch them. Leave `*.jks`, `*.keystore` and the release-keys folder unread.

## Before committing

- `./gradlew :app:testDebugUnitTest :app:lintDebug` passes.
- Before a push, the droid `build-signed.sh` has built the minified release into `./dist`: R8
  problems only show up there.
- `git config --local user.email` ends in `@users.noreply.github.com`.

## Hard rules from the spec

- **Every dependency is FOSS.** Note its licence next to the entry in `gradle/libs.versions.toml`.
  Google Play Services, Firebase, ARCore and other proprietary SDKs stay out.
- **The manifest holds five permissions**: camera, fine and coarse location, internet, vibrate.
  A library that merges in another one gets a `tools:node="remove"` line, as MapLibre has.
- **`warosso.json` is the owner's real setup and stays out of git.** The repo ships only the public
  seed, `app/src/main/assets/seed.json`, written by `tools/fetch-seed.sh`.
- User-visible text lives in `values/strings.xml` and `values-nl/strings.xml`, both kept complete.

## Layout

- `domain/` is pure Kotlin and holds every rule the spec tests: geometry, lookup, door hints, range
  filling, refresh diff, AR label layout. New rules go there with a JVM test.
- `data/`, `sensors/` and `ui/` are the Android side. `AppContainer` wires them by hand.
- `ComplexRepository.data` is the one read path: a whole snapshot, re-read after every write.
- The emulator `warosso-api37` runs the debug build. Settings has a debug menu there that fakes
  position, heading and tilt.

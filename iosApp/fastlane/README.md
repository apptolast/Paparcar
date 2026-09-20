fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## iOS

### ios bootstrap_match

```sh
[bundle exec] fastlane ios bootstrap_match
```

Once (or after changing App ID capabilities): create/refresh Paparcar's App Store profile in the match repo. force only renews profiles — it never recreates the shared distribution certificate.

### ios match_certificates

```sh
[bundle exec] fastlane ios match_certificates
```

Sync App Store certificate + profile from the match repo (readonly)

### ios build

```sh
[bundle exec] fastlane ios build
```

Build a signed Release .ipa locally, without uploading

### ios beta

```sh
[bundle exec] fastlane ios beta
```

Build & upload to TestFlight (internal testers). Build number = latest TestFlight build + 1.

### ios screenshots

```sh
[bundle exec] fastlane ios screenshots
```

Capture App Store screenshots: iPhone 6.9", nine languages, from the mock graph. Reads Snapfile.

### ios upload_metadata

```sh
[bundle exec] fastlane ios upload_metadata
```

Upload the store listing (metadata + screenshots) to App Store Connect. No binary, no submission.

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).

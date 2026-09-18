<div align="center">

<h1 align="center"> Volition </h1>

[![Release build](https://img.shields.io/github/actions/workflow/status/mKonic/volition/release.yml?labelColor=27303D&label=Release&labelColor=06599d&color=043b69)](https://github.com/mKonic/volition/actions/workflows/release.yml)
[![Release](https://img.shields.io/github/v/release/mKonic/volition.svg?maxAge=3600&label=Release&labelColor=06599d&color=043b69)](https://github.com/mKonic/volition/releases/latest)
[![License: MIT](https://img.shields.io/github/license/mKonic/volition?labelColor=27303D&color=0877d2)](/LICENSE)

<div align="left">

Drive your own Android app from a terminal by name instead of by coordinate. The app declares where it can go, and
answers over adb:

```
$ volition go settings_webgpu
ok: SettingsWebGpuScreen
$ volition where
{"activity":"MainActivity","ready":true,"screen":"SettingsWebGpuScreen","stack":["HomeScreen","SettingsWebGpuScreen"]}
$ volition pref high_quality_renderer=false
ok: high_quality_renderer=false
```

For debugging and development of an app you build: Volition is compiled into it, so it knows the screens by name, the
shape of the stack and whatever state the app chooses to publish. Tools that drive from outside - an accessibility
tree, taps at coordinates - work on any app but know none of this.

## Use

Releases are attached to their tag rather than published to Maven Central, so they resolve through an ivy repository
over the release assets.

```kotlin
// settings.gradle.kts
exclusiveContent {
    forRepository {
        ivy("https://github.com/mKonic/volition/releases/download") {
            patternLayout {
                ivy("v[revision]/ivy-[revision].xml")
                artifact("v[revision]/[artifact]-[revision].[ext]")
            }
            metadataSources { ivyDescriptor() }
        }
    }
    filter { includeModule("dev.mkonic", "volition") }
}

// app/build.gradle.kts
debugImplementation("dev.mkonic:volition:0.1.0")
```

`debugImplementation` is the supported way in: the provider Volition is reached through comes with the artifact, so
that build needs no manifest entry of its own and no other build has the component at all.

## Declaring the map

Once, where the app starts - `Application.onCreate`, or wherever the navigator becomes available:

```kotlin
Volition.register {
    voyager { navigatorOrNull }                                  // Voyager apps: stack, back, home
    screen("settings_webgpu", "webgpu") { SettingsWebGpuScreen }
    screen("manga", takesArgument = true) { id -> MangaScreen(id.toLong()) }
    destination("library") { openTab(Tab.Library()); "ok: library" }
    command("seed") { seedTestData(); "ok" }
    state("theme") { preferences.theme().get().name }
}
```

- `screen` takes a Voyager `Screen`; `popFirst = true` clears the stack first, which is what a destination sitting
  under everything else (a tab) wants.
- `destination` is the general form - any suspending lambda that returns what the caller should see. Use it for
  anything that is not a Voyager push.
- `command` is for what is not a place: seeding data, clearing a cache, forcing a sync.
- `state` adds a field to `where`. These are read every time anyone asks, so keep them cheap.

Without the Voyager adapter nothing is lost but the stack and the two navigation destinations; `destination` covers
Fragments, Compose Navigation, plain Activities and anything else.

## From the terminal

`cli/volition` wraps `adb shell content call`. Put it on your `PATH`, then:

| | |
| --- | --- |
| `volition where` | what is on screen, and whether the app is ready to be driven |
| `volition list` | every destination the app knows |
| `volition go <name>` | open one, from wherever the app is - `go manga:42` passes an argument |
| `volition back` / `home` | up one screen, or back to the root |
| `volition pref <key>` | read a preference; `<key>=<value>` writes it live, no restart and no root |
| `volition shot [file]` | screenshot |
| `volition help` | what this app answers, its own commands included |

Anything else is passed to the app, so `volition seed library` reaches a `command("seed")` it registered.

The package comes from `-p`, `$VOLITION_PACKAGE`, or a `.volition` file in the working directory; the device from
`-s` or `$ANDROID_SERIAL`.

Without the script:

```
adb shell content call --uri content://<applicationId>.volition --method go --arg settings_webgpu
```

## Starting the app

A provider call wakes the process without an activity and before the app has set itself up, so `where` reports
`"ready": false` until an activity is resumed. The script waits for that, starting the launcher activity if nothing
is running. Anything that drives the app should wait for `ready` rather than for the process to exist.

## Answering on a build that is not debuggable

Volition refuses unless the app is debuggable. A signed internal or beta build that should still answer says so
itself:

```kotlin
Volition.allowOnAnyBuild()
```

The provider is exported - the shell user has to be able to call it - so this hands anyone with adb access the
ability to navigate that build and read and write its preferences.

## Building

`./gradlew :library:assembleRelease`. Releasing is tagging: a semver tag builds the AAR and publishes it, with the
ivy descriptor consumers resolve through, as that tag's release.

## License

MIT. See [LICENSE](./LICENSE).

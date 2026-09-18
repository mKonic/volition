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
$ volition click "Use high quality renderer"
ok: clicked switch "Use high quality renderer"
$ volition pref high_quality_renderer
high_quality_renderer=false
```

For debugging and development of an app you build: Volition is compiled into it, so it knows the screens by name, the
shape of the stack, every button and field on screen by what it says, and whatever state the app chooses to publish.
Tools that drive from outside - taps at coordinates read off a screenshot - work on any app but know none of this.

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
debugImplementation("dev.mkonic:volition:0.4.0")
```

`debugImplementation` is the supported way in: the provider Volition is reached through comes with the artifact, so
that build needs no manifest entry of its own and no other build has the component at all.

## Declaring the map

Once, where the app starts - `Application.onCreate`, or wherever the navigator becomes available:

```kotlin
Volition.register {
    voyager(MainActivity::class.java) { navigatorOrNull }        // Voyager: stack, back, home
    screen("settings_webgpu", "webgpu") { SettingsWebGpuScreen }
    screen("manga", takesArgument = true) { id -> MangaScreen(id.toLong()) }
    destination("library") { openTab(Tab.Library()); "ok: library" }
    command("seed") { seedTestData(); "ok" }
    state("theme") { preferences.theme().get().name }
}
```

- `voyager` takes the activity that holds the stack, so `back` closes another activity sitting on top of it - a
  reader, a viewer - instead of popping a screen nobody is looking at.
- Jetpack Navigation instead: `navigation(MainActivity::class.java) { navController }` and `route("manga",
  takesArgument = true) { id -> "manga/$id" }`. Fragments and Compose Navigation are the same `NavController`, so one
  adapter covers both, and the current route - with the one it came from - shows up in `where`.
- `screen` takes a Voyager `Screen`; `popFirst = true` clears the stack first, which is what a destination sitting
  under everything else (a tab) wants.
- `destination` is the general form - any suspending lambda that returns what the caller should see. Use it for
  anything that is not a Voyager push.
- `command` is for what is not a place: seeding data, clearing a cache, forcing a sync.
- `state` adds a field to `where`. These are read every time anyone asks, so keep them cheap.

Without an adapter nothing is lost but the stack and the two navigation destinations; `destination` covers anything
else that moves the app.

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
| `volition check <condition>` | whether what `where` says is so - `screen=MangaScreen`, `reader.page>3` |
| `volition wait <condition> [s]` | until it is, instead of sleeping and hoping |
| `volition help` | what this app answers, its own commands included |

Anything else is passed to the app, so `volition seed library` reaches a `command("seed")` it registered.

A condition is a field of `where`, then `=`, `!=`, `<`, `>`, `<=`, `>=` or `~` for contains. A path reaches inside
what a `state` published - `reader.page>3` - and `~` looks through a list, so `stack~SettingsScreen` asks whether that
screen is anywhere in it. Numbers compare as numbers, text ignores case.

## What is on screen

`volition ui` lists what the app is showing, read from its own views and Compose semantics:

```
$ volition ui
text      "Library"  @160,210
button    "Search"  @813,211
tab       "Default · 4"  selected  @161,393
switch    "Use high quality renderer · Draw pages with the GPU."  on  @608,490
slider    (unlabeled) = "4"  0..4  @608,1050
```

Anything listed can be acted on by what it says:

| | |
| --- | --- |
| `volition click Retry` | run its click, with no touch involved; `longclick` for a long press |
| `volition fill Search "one piece"` | set a field's text, or a slider's value; `""` names the focused field |
| `volition submit Search` | the field's keyboard action - search, go, done |
| `volition tap Retry` | a real touch at the element's own position, for when the touch path is what is being tested |
| `volition wait "Chapter 1" [s]` | until it is on screen, 10 seconds unless told |
| `volition find Retry` | what a name picks |

A name matches what an element says, its content description, test tag or view id - whole first, then as part of
it - and failing those, its kind: `fill slider 8`, `click switch#2`. When a name picks several, the answer lists them
and `Retry#2` takes the second, counted top to bottom. The window on top is asked first, so an open dialog's `Cancel`
wins over one behind it. A disabled element refuses.

Compose is read from the merged semantics tree, the one screen readers use, so what Volition can name is what
TalkBack can read. A row that shows a switch but carries no toggle state in its semantics reads as a plain
`clickable` here, as it does to a screen reader, and the fix is the same one.

Every failure exits non-zero, so a script can stop on it.

### Without a name

| | |
| --- | --- |
| `volition text "a query"` | type through the keyboard into whatever has focus |
| `volition key back` | a key by name or keycode |
| `volition tap <x> <y>` | a tap at a coordinate |
| `volition swipe <x1> <y1> <x2> <y2> [ms]` | a drag |

`where` names the focused field, which is where typed text lands.

A `command` is still the better tool for setup a script repeats - seeding data, resetting state - since it skips the
UI entirely.

The package comes from `-p`, `$VOLITION_PACKAGE`, or a `.volition` file in the working directory; the device from
`-s` or `$ANDROID_SERIAL`. That file also holds the aliases a project keeps typing:

```
app.komikku.dev

webgpu = go settings_webgpu
reader = wait reader.page>0 20
```

An alias stands for the start of a command line, and whatever is typed after it is passed on.

Without the script:

```
adb shell content call --uri content://<applicationId>.volition --method go --arg settings_webgpu
```

## From an agent

`mcp/volition-mcp` is the same thing over MCP, so an agent gets `where`, `ui`, `go`, `click`, `fill`, `wait` and the
rest as tools instead of guessing at coordinates in a screenshot:

```
claude mcp add volition -- /path/to/volition/mcp/volition-mcp
```

It takes the same `-p`, `-s` and `--launch` options as the CLI, reads the same `.volition` file, and every tool takes
a `package` for a session driving more than one app. Python 3, no dependencies. `screenshot` is there too, for when
what matters is how something looks rather than what it says.

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

`./gradlew :library:assembleRelease`. `sample/` is a small app that carries Volition, which is what the adapters and
the element reader are driven against: `./gradlew :sample:installDebug`, then `volition -p dev.mkonic.volition.sample
ui`.

## License

MIT. See [LICENSE](./LICENSE).

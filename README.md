# Volume Widget

A small Android app whose home-screen widget controls the phone's volume from a
single slim row. The widget resizes horizontally and is customised from the app.

```
Media   −   ▬▬▬▬▬▬───   47%   +   (40%)
```

## Widget

| Element        | Tap action                                                        |
| -------------- | ----------------------------------------------------------------- |
| `−` / `+`      | Lower / raise the volume by the configured number of steps        |
| Preset chip    | Set the **media** volume straight to the value saved in the app   |
| Label          | Switch between media and any extra volumes enabled in the app     |
| Volume number  | Mute / unmute                                                     |

## Settings (in the app)

A live preview at the top updates as you change anything.

- **Preset button** – show/hide, and the media volume it jumps to.
- **Volume** – steps per tap; show the raw step number (0, 1, 2…) instead of a
  percentage; enable ring, notification and alarm volumes.
- **Colours** – seven built-in gradients or your own (start hue, end hue,
  intensity); gradient or solid background; light/dark; background opacity.
- **Shape and text** – height, corner roundness, outline thickness, text size,
  and whether the level is drawn as a bar, as a fill across the widget, or not
  at all.
- Show/hide the volume name and number.
- **Add widget to home screen** – pins the widget without the launcher's
  widget list.

## Known limitations

- If the volume is changed with the hardware keys, the widget's display catches
  up on the next widget tap, when the app is opened, or within 30 minutes. The
  buttons always act on the real current volume.
- Ring and notification changes are ignored while Do Not Disturb is on.
- The widget cannot be drawn taller than its launcher row. The height cap only
  applies on Android 12+; on Android 10–11 the widget fills its row.

## Requirements

- Android 10 (API 29) or newer.
- To build: JDK 17, Gradle 8.7, Android SDK Platform 34 and Build-Tools 34.0.0.

## Building

`build.bat` expects the build tools inside a git-ignored `tools/` folder:

```
tools/jdk      JDK 17
tools/gradle   Gradle 8.7
tools/sdk      Android SDK (platforms;android-34, build-tools;34.0.0, platform-tools)
```

Then:

```
build.bat
```

The APK is written to `build\outputs\apk\debug\VolumeWidget-debug.apk`.
Alternatively, open the folder in Android Studio, or run `gradle assembleDebug`
with your own JDK 17 and `ANDROID_HOME`.

## Installing

Copy the APK to the phone and open it, or with USB debugging enabled:

```
tools\sdk\platform-tools\adb.exe install -r build\outputs\apk\debug\VolumeWidget-debug.apk
```

## Project layout

The project is a single Gradle module at the repository root, with no library
dependencies.

```
build.gradle, settings.gradle, gradle.properties   Gradle configuration
build.bat                                          Builds the debug APK with tools/
src/main/AndroidManifest.xml
src/main/java/com/lm/volumewidget/
    VolumeWidget.java    Widget provider: tap handling and drawing
    MainActivity.java    Settings screen with live preview
src/main/res/
    layout/widget.xml    Widget layout
    xml/widget_info.xml  Widget size and resize behaviour
    drawable/            App icon and preset chip
    values*/             Colours and themes (light and dark)
```

Settings are stored in the app's `SharedPreferences` (file `cfg`) and are read
by the widget every time it redraws.

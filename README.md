# BYD Split

**English** | [한국어](README.ko.md)

BYD Split opens two apps side by side in split-screen with a fixed ratio (e.g. 7:3) on BYD head units, in one tap. Navigation stays big on the left, music stays on the right. The mostly useless top status bar is hidden too, for maximum screen real estate.

It uses and depends on [Shizuku](https://github.com/rushiranpise/Shizuku-Next).

## Screenshots

Before:

![Before](screenshots/before.jpg)

After:

![After](screenshots/after.jpg)

Setup:

![Setup](screenshots/setup.jpg)

Home screen shortcut:

![Home screen shortcut](screenshots/shortcut.jpg)

Recommended Shizuku setup:

<img src="screenshots/shizuku.jpg" alt="Recommended Shizuku setup" height="600">

## Features

- Choose any two apps for the left and right panes
- Drag the divider to set the ratio, snapping from 2:8 to 8:2
- Hides the status bar while split (`wm overscan`) for more screen space
- Restores the status bar automatically when you switch to another app or go home

## Requirements

- BYD head unit running Android 10
- [Shizuku](https://github.com/rushiranpise/Shizuku-Next), started and running
  - The [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next) fork is recommended, since it can start automatically on boot

## Download

Download the latest APK from [Releases](https://github.com/arter97/BYDSplit/releases/latest).

## Usage

1. Install and start Shizuku.
2. Open BYD Split.
3. Tap the left box and choose an app, then do the same for the right box.
4. Drag the divider to the ratio you want.
5. Tap **Apply** and grant the Shizuku permission when asked.

From then on, launching BYD Split splits the screen right away.
To change the apps or ratio, use the **Settings** launcher shortcut (long-press the icon) or reinstall the app.

## How it works

1. Launches both apps.
2. Moves the left app's task into split-screen primary and the right app's into split-screen secondary.
3. Resizes both stacks with `am stack resize` to the chosen ratio.
4. Applies `wm overscan` to hide the status bar.
5. A foreground service registers a task stack listener. When an app other than the two is brought to front, it runs `wm overscan reset` and stops.

## Building

```sh
./gradlew assembleRelease
```

## License

Copyright 2026 Juhyung Park (arter97) <qkrwngud825@gmail.com>

Licensed under the [Apache License, Version 2.0](LICENSE).

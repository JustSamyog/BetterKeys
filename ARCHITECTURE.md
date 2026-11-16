# How Better Keys works

This document explains the whole system: how the app hears your keys and mouse, how sounds get picked and played, how settings and start on boot behave, and how the build and installer work. Read the README first if you just want to install and use it.

## Technologies used

| Piece | What is used and why |
|---|---|
| Language | Java 17 LTS, built with Maven, shaded into one runnable jar with all dependencies |
| Global input | JNativeHook 2.2.1, the standard pure native hook library for Java, no administrator rights needed |
| UI | Swing with FlatLaf 3.4 Flat Dark, styled through client properties, zero external UI toolkit |
| Audio | javax.sound.sampled `Clip` pools with mixer routing for output device selection |
| Persistence | Gson 2.10.1 for `config.json` and `pack.json` |
| Packaging | jpackage app image with its own bundled runtime, so end users need no Java installed |
| Installer | WinForms C# compiled with the Windows built in csc, embeds the whole app as one payload resource |
| Tray icons | Monochrome set, recolored at load time to match the palette |

## Big picture

```
Main                            entry point, installs the FlatLaf dark theme
└── TrayApp                     orchestrator: config, engine, hook, tray, window
    ├── config.AppConfig        app settings persisted as JSON
    ├── model.SoundpackManager  discovers and loads packs
    ├── model.Soundpack         one pack: metadata plus sound id to file map
    ├── audio.AudioEngine       clip pools, volume, mute, output routing
    ├── audio.WavGenerator      synthesizes the base sound set in memory
    ├── input.GlobalInputListener  global hook: decides which sound to play
    └── ui.*                    tray menu, main window, dialogs, icon loading
```

The hook package never touches UI. Sound decisions happen in `input`, playback in `audio`, appearance in `ui`, and persistence in `config`, so each part can change without dragging the others along.

## Global input pipeline

Java cannot see keys outside its own windows, so the app registers a system level keyboard and mouse hook through JNativeHook. On Windows that means the low level `WH_KEYBOARD_LL` and `WH_MOUSE_LL` hooks, which deliver every key and button event of the whole session to the app without administrator rights.

A single class, `GlobalInputListener`, implements both listener interfaces and turns events into sound ids:

1. A key press arrives with a native virtual key code.
2. The code is translated to a stable id such as `VC_A` or `VC_SPACE`.
3. The active soundpack resolves the id: a direct per key mapping when one was set in the Key Mapper, a dedicated sound for space, enter, backspace, tab and escape, otherwise a random `key_N` variation drawn from the pack's variation pool.
4. The id goes to `AudioEngine` for playback.

### Hold and spam behavior

Repeated press events that the OS fires while a key is physically held are filtered with a per key held set, `heldKeys`, built from `ConcurrentHashMap.newKeySet`:

- The first press of a key adds it to the set and plays once.
- Further press events for a key already in the set are dropped before any sound logic runs.
- A real release removes the key, so the next press can sound again.
- Entries are per key, so holding one key while tapping others never disturbs the held key's single sound.
- A 200 ms debounce layered on top absorbs presses that arrive too soon after a release, so double acting or worn switches still produce one sound per physical press.

### Mouse clicks

Click sounds trigger in the pressed callback, not the clicked callback. On Windows, JNativeHook reports right clicks in the pressed callback under `BUTTON2`, and the clicked callback never reports `BUTTON3` for them, which is why naive click to sound mappings leave right click silent. Playing in the pressed callback for any non zero button code covers left, right, and middle clicks, including clicks synthesized by other software.

## Sound engine

`AudioEngine` is built directly on the Java Sound API:

- Every sound id owns a small pool of preloaded `Clip`s, so the same sound can overlap itself during fast typing instead of cutting the previous hit short.
- Audio work runs on its own thread, never on the hook thread, so loading a wav never delays key handling.
- Master volume, mute, and output routing are runtime states. Changing them takes effect immediately, no restart.

`AudioEngine.getOutputLines()` enumerates the real sound devices visible to Java Sound. Picking one routes playback to that mixer; leaving it on System Default uses the OS routing you already have.

## Soundpacks

A pack is a folder with `pack.json` and wav files, exactly as the bundled pack ships it:

```json
{
  "name": "Cherry MX Red ABS",
  "author": "JustSamyog",
  "volume": 0.85,
  "sounds": {
    "key_01": "sounds/key_01.wav",
    "space_press": "sounds/key_01.wav",
    "mouse_click": "sounds/key_07.wav"
  }
}
```

- Ids matching `key_N` form the random variation pool used for normal typing.
- `space_press`, `enter_press`, `backspace_press`, `tab_press`, `escape_press`, `default_press`, `default_release`, `mouse_click`, and `mouse_release` have reserved meanings.
- The Key Mapper dialog writes explicit `VC_*` entries into this map for per key control.
- If a pack is missing a file, `WavGenerator` synthesizes the base set in memory as a fallback, so the app never goes silent over an incomplete pack.
- The bundled `Cherry MX Red ABS` pack lives inside the jar under `/soundpacks/`. User packs are scanned from `%USERPROFILE%\.betterkeys\soundpacks\`.

### Converted packs

Packs exported from other keyboard sound tools (a single `sound.ogg` plus a `config.json` with per key timing ranges in milliseconds) are supported directly. On load, such a folder is detected by the presence of `config.json` with an `audio_file` that `pack.json` still references. The converter in `PackConverter` then:

1. Decodes the OGG through the bundled Vorbis SPI (the shaded jar also carries an MP3 SPI for other sources) into PCM, with a size cap as a guard rail.
2. Cuts one small wav per timing range into `sounds/pk_<key>_<n>.wav`. Multiple ranges for one key become variants, and keys that map to the same button (like the two shifts) merge into one shared variant pool.
3. Rewrites `pack.json` with `VC_*` entries pointing at the slices and a `keyVariants` map the input listener uses to pick a random variant per press. Names the app does not capture (the numpad, for example) are skipped and reported.

Conversion runs once per pack, is skipped on every later load, and preserves name, author, and volume. If something is missing or unsupported, the tray bubble says so in plain words instead of failing silently.

## Tray and main window

- The tray icon opens a `JPopupMenu` positioned with placement math that right aligns it above the taskbar, the way native Windows tray menus behave. The menu also closes itself when you click anywhere else, using the same global hook with an AWT fallback when the hook is unavailable.
- Tray icons are loaded from `/icons/tray/`, recolored to the app ink `#f0f0f2` while keeping their alpha, and rendered at physical pixel sizes derived from the current display scale, so they stay crisp on 100% and 200% DPI setups alike. `IconLoader` also carries hand built vector fallbacks in case a resource is ever missing.
- The main window is FlatLaf Flat Dark styled through client properties on a strict monochrome palette (`#f0f0f2` ink, `#232327` surfaces, `#1a1a1c` fields) with Comic Sans MS as the brand face. Section cards cover soundpack, output, volume and mute, and actions.
- Mute, volume, and pack changes all take effect instantly and save immediately.

## Config and start on boot

Settings persist in `%USERPROFILE%\.betterkeys\config.json` via Gson:

```json
{
  "mute": false,
  "volume": 0.85,
  "selectedSoundpack": "Cherry MX Red ABS",
  "selectedAudioLine": "Primary Sound Driver",
  "startOnBoot": true
}
```

Every change is written back right away, so a hard shutdown never loses your settings.

Start on boot writes a `BetterKeysDesktop` value under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` pointing at the installed exe. It is deliberately conservative:

- Only the packaged exe may register. Development runs from the jar never touch the value, because a jar path as a boot target would break startup and trip security software.
- The entry is only rewritten when it actually differs from the current value, so ordinary launches never look like startup item tampering.
- Every launch with start on boot enabled self heals the entry, so reinstalling or moving the app keeps boot working.
- Unchecking the box deletes the entry.

On boot, Windows launches the exe with no interaction needed. The app reads the config, finds the pack you selected before shutdown, restores it along with volume, output device, and mute state, and shows a tray bubble. You never have to pick anything again.

## Packaging and installer

The release pipeline has three steps:

1. `mvn package`. Maven compiles the sources and the shade plugin merges the app plus JNativeHook, FlatLaf, and Gson into a single jar which also contains every resource: sounds, icons, and the bundled soundpack.
2. `jpackage --type app-image`. The jar becomes a Windows app image with a native `BetterKeys.exe` launcher, its own icon, and a bundled Java runtime, which is the reason end users never install Java.
3. The WinForms installer. A small C# winexe (single source file kept in the repo at `installer/Setup.cs`) embeds the whole app image as one compressed resource. When run, it closes a running instance, extracts the payload to `%LOCALAPPDATA%\Programs\BetterKeys`, and writes a "Better Keys" shortcut into the Start Menu Programs folder, which is how the app becomes findable through Windows search. It supports `/dir=` to pick a folder, `/norun` to skip launching, and `/silent` for unattended installs.

No installer framework is used, which keeps the setup exe small and fully under control.

## FAQ

**Does it record what I type?**
No. The hook receives raw key events to know when to play a sound, and that is all. Nothing is logged, stored, or sent anywhere, there is no network code at all.

**Does it need administrator rights?**
No. Hooks, config, and the startup entry all live in user space.

**Will it slow down my games?**
The hook delegates to the OS low level hooks and the audio work runs off the hook thread, so it stays out of the way. You can also pick a dedicated output device or mute anytime.

**Can I use my own sounds?**
Yes, that is the point of packs. Any folder of wavs with a `pack.json` mounted under the soundpacks folder shows up in the tray and the main window after a restart of the app. Folders exported from other keyboard sound tools (`sound.ogg` + `config.json`) are converted automatically, no manual work needed.



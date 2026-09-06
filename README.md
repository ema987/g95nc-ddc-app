# G95NC DDC/CI Controller App

A lightweight, standalone Desktop application built with Kotlin and Compose Multiplatform to control the Samsung Odyssey G95NC monitor (PBP layouts, inputs, and audio sources) across Windows, Linux, and macOS.

This app is a frontend and extension of the reverse-engineered scripts from the [g95nc-ddc](https://github.com/mwd102/g95nc-ddc) repository. 

## Features
- **Cross-Platform**: Runs on Windows, Linux, and macOS.
- **System Tray / Menu Bar**: Lives in the tray for quick access.
- **Presets**: Save and quickly apply your favorite layout and source combinations with one click.
- **Standalone on Windows**: Uses JNA to bind directly to the Win32 `dxva2.dll` API — no Python or vendor software required.
- **Linux & macOS**: Seamlessly integrates with `ddcutil` and `betterdisplaycli`.

## Requirements
- **Windows**: No external requirements.
- **Linux**: [ddcutil](https://www.ddcutil.com/) must be installed.
- **macOS**: [betterdisplaycli](https://github.com/waydabber/betterdisplaycli) must be installed.

## Building and Running
The app is built using Gradle and Compose Multiplatform.

To run the app directly:
```bash
./gradlew run
```

To package a standalone executable/installer for your current OS:
```bash
./gradlew package
```
(This will output to `build/compose/binaries/main/`)

## Architecture
- `MonitorController`: Interface defining the core interactions.
- `Win32Dxva2Controller`: JNA implementation for Windows.
- `DdcutilController`: `ProcessBuilder` implementation for Linux.
- `BetterDisplayCliController`: `ProcessBuilder` implementation for macOS.

## Design notes
- **No background status polling, by design.** The dashboard fetches the monitor's status once
  when it opens, and otherwise only re-fetches on an explicit user action (Refresh, applying a
  layout/source/preset). It deliberately does **not** poll on a timer to keep itself in sync with
  changes made outside the app (e.g. switching inputs from the monitor's own remote/OSD). DDC/CI
  is a slow, easily-congested channel on every OS this app targets - background polling would mean
  three cross-platform code paths (a subprocess spawn on Linux/macOS, a native DXVA2 call on
  Windows) continuously contending with the monitor's own on-screen menu and with each other. If
  the on-screen state and the app's displayed state drift apart, hit Refresh.

## Known limitations / open investigations

### 3-way PBP layouts never get a third pane's status (needs real-hardware data to fix)

`DdcStatusReader.readStatus()` (see the `KNOWN GAP` comment in that file) decodes VCP `0xE3`'s
16-bit readback into exactly two byte values - a high byte and a low byte - and maps them to
`PaneChannel.PANE_1`/`PANE_2`. That's everything a 2-way split needs, but a 3-way split
(`PBP_3_*` layouts) has a third pane whose source is never read at all: `PaneChannel.PANE_3` is
simply absent from `MonitorStatus.paneSources` for every 3-way layout today. Visible effects:
- The rightmost pane in the layout diagram/preset thumbnails always shows "No signal" in 3-way,
  regardless of what's actually plugged in.
- `Preset.matchesStatus()` can never return true for a 3-way preset (it compares the full
  `paneSources` map), so the "Active" badge never lights up for one.
- Applying a 3-way preset writes all 3 panes via `setSource()`, but the safety-countdown revert
  and "Save Current as Preset" only ever capture 2 of the 3 panes back.

**Why this hasn't just been "fixed" outright:** the [g95nc-ddc protocol notes](https://github.com/mwd102/g95nc-ddc/blob/main/docs/protocol.md)
only vaguely describe the 3-way `E3` readback - "high byte `0xc0` in 3PBP, `0x10` in 2-way; the
low bytes encode current pane sources" - with no exact bit layout. A single VCP read is 16 bits;
if the high byte really is a fixed mode flag in both cases, only 8 bits are left to encode
*three* independent pane sources, which doesn't obviously fit the write-side source codes
(`HDMI1=0x00, HDMI2=0x01, HDMI3=0x02, DP=0x10`) as-is. Guessing a bit layout and shipping it would
risk being confidently wrong (or, worse, making 3-way status resolution retry-until-timeout
instead of just missing one pane) - this project's own convention (established the hard way, more
than once this session - see the E8 audio masking fix) is to verify protocol behavior against the
real monitor rather than guess it.

**What's needed to actually fix this** (requires the real monitor, so it's parked for now):
1. **Sanity-check the current 2-way decode first.** It's *possible* pane 1's decode has been
   silently wrong this whole time and we haven't noticed because it happened to always be
   DisplayPort - `SourceCode.DP.e3Code` is `0x10`, the exact value the protocol notes claim is a
   fixed 2-way mode flag. Test: in a 2-way layout, use the app's own pane-click picker to set the
   *left* pane to something other than DisplayPort (e.g. HDMI1), then check whether the diagram's
   label actually updates. If it does, the current high-byte/low-byte decode is confirmed fine for
   2-way and this concern can be dropped; if it doesn't, this is a second, more serious bug beyond
   the missing third pane.
2. **Capture a raw 3-way `E3` reading.** Switch to any `PBP_3_*` layout via the Change Layout
   picker, then get the raw value `getvcp E3` (or the `betterdisplaycli`/DXVA2 equivalent) returns.
   The easiest way to get this without manually reconstructing the app's own bus/tag discovery: add
   a temporary debug `println` right after `readVcp("E3")` in `DdcStatusReader.kt`'s 3-way branch,
   run via `./gradlew run` from a terminal so the value prints there, switch layouts, and read it
   off. Repeat with a couple of different real per-pane source combinations (change one pane at a
   time, ideally via the monitor's own OSD/remote so the app's own `setSource()` isn't the only
   thing you're testing) to see how the raw value moves as sources change - that's the actual key
   to reverse-engineering the bit layout.
3. **Cross-check against what the E8 investigation already taught us**: E8 (audio, a documented
   single-byte/3-option feature) turned out to carry an undocumented non-zero high byte on real
   hardware (observed `0x1002`, i.e. only the low byte was the real value) - direct evidence this
   monitor's firmware puts non-obvious bytes in VCP readbacks beyond what the docs describe. It's
   plausible 3-way's "extra" pane isn't packed at a byte boundary at all (e.g. a different,
   read-side-only encoding than the write-side source codes) - only real captured values across a
   few combinations can settle this safely.
4. Once the real layout is known: update the E3 decode block in `DdcStatusReader.kt` to populate
   `PaneChannel.PANE_3` (and fix panes 1/2 too if step 1 finds a problem), and add regression tests
   to `DdcStatusReaderTest.kt` using the real captured values as fixtures.

### The app can show "Off/Single Source" when the monitor never actually left PBP

Observed live (2026-09-06), on macOS/`betterdisplaycli`: on at least one occurrence, the app's
displayed status flipped to Off/Single Source ("No signal") shortly after an audio-pane switch,
but the physical monitor **stayed in PBP the entire time** (confirmed directly by the user watching
the real screen) - this was a false status read, not a real mode change. The most likely mechanism:
this file already documents that "the DDC bus can echo stale/busy values (`0x00`/`0x3900`) for a
while after a source or layout switch" - the audio write-verify-retry loop below (up to 3 write+
read cycles) adds real extra traffic right before the next status check, plausibly leaving the bus
in exactly that echoing state for long enough to satisfy the old confirmation count
(`zeroConfirmations`) with *stale* readings, not a genuine settled one.

**Mitigation applied**: `BetterDisplayCliController.getStatus()` raised `zeroConfirmations` from 3
to 5 (and `maxAttempts` 15 → 20 to give the extra confirmations room) - requiring more consecutive
matching reads before trusting a "Single Source" result makes a transient noisy burst far less
likely to be mistaken for a real one. Not yet re-verified live given how much back-and-forth this
already took in one session; if "Off/Single Source" still shows up spuriously, this number may need
to go even higher, or the audio retry loop's write traffic may need to be reduced instead.

**This casts some doubt on the earlier-documented "monitor drops PBP, `50/50` write doesn't
recover it" finding below** - it's now unclear whether every one of those occurrences was a real
hardware mode change or partly this same false-read phenomenon. One occurrence in this session
*was* accompanied by the physical display actually losing signal and macOS locking the session as a
result, which a misread status alone can't explain - so a real mode-drop mechanism likely still
exists separately. Both are left documented until there's enough real-hardware data to tell them
apart reliably.

#### Original finding: the monitor can drop out of PBP entirely, and won't necessarily accept the "50/50" write back

What's notable: **explicitly writing `Layout.PBP_2_50_50` (`0xE2 = 0x03`) to recover from an
Off/Single Source state appeared not to work, twice, in two separate app sessions** - the write
completed without error, but the monitor's readback stayed on Off/Single Source. Manually selecting
**`Layout.PBP_2_LAST` (`0xE2 = 0x01`, "PBP 2-way (last split)")** instead appeared to work
immediately, no power cycle or physical monitor control needed.

This suggested recovering *into* a 2-way PBP state from Off/Single Source might need the generic
"just turn PBP back on" command (`0x01`) rather than a specific split-ratio command (`0x03` etc.) -
possibly because the monitor's PBP subsystem needs to be "kickstarted" via the simpler command
before it'll accept a more specific one. Not yet confirmed:
- Whether `0x03` fails specifically *from* Off/Single Source, or more generally - or whether it
  never actually failed, and the readback was just stale (see above).
- Whether other specific-ratio codes (`0x02`, `0x04`, `0x05`, and the 3-way codes) have the same
  problem, or just `0x03`.
- Whether this is state-dependent (e.g. only right after an unplanned drop to Off/Single Source,
  not from a normal, deliberate switch to Off/Single Source).
- What actually *causes* a genuine unplanned drop to Off/Single Source in the first place - the
  observed occurrences happened during a heavy, unrelated background disk I/O load (a large file
  copy), which may or may not be a contributing factor to general DDC/CI bus instability on this
  monitor.

No code change has been made for the write-recovery side of this yet - it needs more real-hardware
data (ideally gathered once the read-side false-positive above is actually ruled out) before a safe
fix (e.g. "prefer `PBP_2_LAST` when recovering from Off/Single Source") can be justified rather than
guessed.

### The DDC/CI channel itself can degrade under sustained heavy use, independent of any app code

Observed live (2026-09-06), after a very long session of DDC traffic (many hundreds of read/write
commands over several hours of testing): the audio source still would not reliably switch even with
the settle-confirmed write-verify-retry logic above. Diagnosed by querying `betterdisplaycli`
directly from a terminal, bypassing the app entirely:

```
betterdisplaycli get --tagID=7 --feature=ddcAlt --vcp=0xE2   # 14593 (0x3901, plausible)
betterdisplaycli get --tagID=7 --feature=ddcAlt --vcp=0xE2   # 0 (same tag, same code, moments later)
betterdisplaycli get --tagID=7 --feature=ddcAlt --vcp=0xE2   # 14593 again
```

The same tag, same VCP code, queried repeatedly with nothing in between, returned different answers
across consecutive calls. That rules out a tag-selection bug (this monitor exposes itself as two
separate macOS display identifiers, tagID 6 and 7, and one theory going in was that the app's
tag-discovery might be locking onto the "wrong" one for a given feature - the instability shows up
even holding the tag and VCP code fixed, so that's not the whole story) - this is the DDC/CI
channel itself failing to return trustworthy answers, independent of anything this app's code does.

Restarting the `BetterDisplay.app` helper (`osascript -e 'tell application "BetterDisplay" to
quit'`, then relaunch) partially, transiently improved things but did not fully resolve the
instability in this session. Not yet tried: a longer cooldown period with zero DDC traffic at all,
or a physical power-cycle of the monitor.

**Practical implication**: this app - and its `getvcp`/`setvcp`-based recovery via more retries -
cannot fix a degraded DDC/CI channel. If switching still doesn't stick after a reasonable number of
tries, further automated retries are unlikely to help and just add more traffic to an already
struggling channel; a `BetterDisplay.app` restart or monitor power-cycle is the next thing to try,
not more software-side polling.

# Fox's Foxtail

A cosmetic Minecraft mod by **FoxLemon** that adds a fox tail to the player, with adjustable angles and spring-based movement.

This repository contains the **NeoForge version**, currently under development. Features and animation behaviour may change between releases.

## Current target

| Component | Version |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge development target | 26.1.2.109 |
| Java | 25 |
| Mod | 0.1.5.alpha.2 |

The JAR from this project is for NeoForge. Other Minecraft versions and loaders require a separate compatible build.

## Features

- A textured fox tail attached to the player's torso, supporting normal and slim player models.
- A root, middle, and tip model hierarchy.
- Movement-driven spring bending, stationary-turn lag, and a twist response to torso roll.
- Separate middle and tip springs: the tip follows the middle with half-strength extra bend.
- Leg avoidance and smooth root-angle changes for crouching, swimming, crawling, elytra flight, sleeping, and riding.
- The tail flashes with the player when hurt.
- Adjustable root elevation from **−90° to +90°**.
- A custom settings screen with sliders, a player preview, Reset defaults, Save, and Cancel.
- An optional key binding to open the settings screen, unassigned by default.

Movement-driven physics currently runs for the local player only. Other players can render with tails, but do not receive their own movement-driven simulation.

## Installation

1. Set up a Minecraft **26.1.2** instance with NeoForge **26.1.2.109**.
2. Place the built mod JAR in that instance's `mods` folder.
3. Launch Minecraft and switch to third person to see the tail.

The tail rendering and settings run on the client. Other players need the mod on their own clients to see its effects. Compatibility with animation mods and modpacks still needs individual testing.

## Settings

Open **Mods → Fox's Foxtail → Config**. You can also assign **Open Tail Settings** under **Options → Controls → Key Binds → Fox's Foxtail**; no key is assigned by default.

| Setting | Effect |
| --- | --- |
| Movement strength | How strongly movement and torso roll drive the spring. |
| Frequency | How quickly the spring responds; higher values feel stiffer. |
| Damping | How much the spring's oscillation is reduced. |
| Response | How the spring reacts initially to changes in its target. |
| Maximum target bend | Limits the target angle per axis; spring overshoot can exceed it. |
| Root angle | Raises or lowers the whole tail. The middle and tip inherit this angle. |

The preview uses a repeating test impulse to demonstrate the draft settings. Join a world to see the player preview. **Save** applies and stores changes; **Cancel** or Escape discards them. **Reset defaults** resets the draft, which must still be saved.

## Building from source

Use **JDK 25** and run the Gradle wrapper from the project directory.

### macOS / Linux

```sh
bash ./gradlew build
```

### Windows

```powershell
.\gradlew.bat build
```

The mod JAR is written to `build/libs/`. With the current project settings, its name is `foxsfoxtail-0.1.5.alpha.2.jar`. Change `mod_version` in `gradle.properties` when preparing a new version.

To launch the development client:

```sh
bash ./gradlew runClient
```

On Windows, use `.\gradlew.bat runClient`. The development game's files are stored in `run/`.

## Project layout

- `src/main/java/net/foxlemon/foxsfoxtail/` — mod entry point and configuration.
- `src/main/java/net/foxlemon/foxsfoxtail/client/` — rendering, model animation, physics, and settings screen.
- `src/main/resources/assets/foxsfoxtail/` — texture and language resources.
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata; Gradle fills in values from `gradle.properties`.
- `model/fox_tail.bbmodel` — editable Blockbench model.

## Credits and license

Created by **FoxLemon**, using the NeoForge MDK and Blockbench.

The project declares **MIT** in its mod metadata. See `TEMPLATE_LICENSE.txt` for the NeoForge MDK template's original license notice.

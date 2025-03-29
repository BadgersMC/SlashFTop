# SlashFTop - Minecraft Server Faction Top Plugin
A Minecraft server plugin for tracking and ranking factions based on spawners and valuable blocks, integrated with RoseStacker and Vault!

[![Test and Release](https://github.com/thegeekedgamer/SlashFTop/actions/workflows/test-and-release.yml/badge.svg)](https://github.com/thegeekedgamer/SlashFTop/actions/workflows/test-and-release.yml)

## Features
### Core Functionality 🛠️
* Tracks spawners and valuable blocks placed by players in faction-claimed areas.
* Integrates with [RoseStacker](https://www.spigotmc.org/resources/rosestacker.84994/) to handle stacked spawners and blocks.
* Uses [Vault](https://www.spigotmc.org/resources/vault.34315/) for economy integration to calculate faction worth.
* Provides faction ranking based on the total value of tracked spawners and blocks.

### GitHub Actions 🎬
* Automated builds, testing, and artifact uploading.
* Runs unit tests on Ubuntu and Windows environments with Java 21.

### Gradle Builds 🏗️
* Gradle build system for dependency management and JAR creation.
* JUnit 5 and Mockito for unit testing.
* Supports Java 21 for modern development.

### Config Files 📁
* `plugin.yml` with autofill name, version, and main class.
* Gradle build configuration with Spigot, RoseStacker, and Vault dependencies.
* `.gitignore` for common Gradle files.

## Usage
### Requirements
- **Minecraft Server**: Spigot/Paper 1.20.4 (or compatible versions).
- **Dependencies**:
  - [RoseStacker](https://www.spigotmc.org/resources/rosestacker.84994/) (for stacked spawners and blocks).
  - [Vault](https://www.spigotmc.org/resources/vault.34315/) (for economy integration).
  - A factions plugin compatible with `FactionUtils` (e.g., FactionsUUID).

### Installation
1. Download the latest `SlashFTop-<version>.jar` from the [Releases](https://github.com/thegeekedgamer/SlashFTop/releases) page.
2. Place the JAR file in your server’s `plugins/` directory.
3. Ensure RoseStacker and Vault are installed and configured.
4. Start your server to generate the plugin’s configuration files.
5. Configure the plugin as needed (e.g., set valuable blocks in the config).

### Release Info
#### Spigot/Paper Version Mapping
| Spigot/Paper | SlashFTop Version |
|--------------|-------------------|
| 1.20.4       | 1.0.0+            |

To use this plugin, ensure your server is running Spigot or Paper 1.20.4. Future versions will support newer Minecraft versions as needed.

#### Release and Versioning Strategy
Stable versions are tagged `vX.Y.Z` and have an associated [release](https://github.com/thegeekedgamer/SlashFTop/releases).

| Event          | Plugin Version Format | CI Action                        | GitHub Release Draft? |
|----------------|-----------------------|----------------------------------|-----------------------|
| Push to `main` | 1.0.0-SNAPSHOT        | Build, test, and upload artifact | No                    |
| Tag `vX.Y.Z`   | X.Y.Z                 | Build, test, and upload artifact | Release               |

## Building Locally
Thanks to [Gradle](https://gradle.org/), building locally is easy. Run the following command:

```bash
./gradlew build

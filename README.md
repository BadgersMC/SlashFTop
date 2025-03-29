# SlashFTop - Minecraft Server Faction Top Plugin
This plugin is for modern faction servers in need of a *proper* /ftop system. Unlike other implementations, this has been build with optimization in mind, which is why currently it only supports **RoseStacker**. This ftop plugin features an aging system, which allows a configurable amount of days required for a spawner stack/block stack to be placed, in order for it to achieve its max value. **This updates live.** Unlike the other ftop plugins, SlashFTop does not scan each block in a chunk periodically. Spawner and Block stacks are updated with place and break events. Naturally this means players who just go and claim a spawner will not get the value tracked, which is A OK in my book. 

Players use /ftop to open a GUI of player heads, representing the leaders of each Faction. Hovering over these heads provides information about that Factions assets, and their current value.
![image](https://github.com/user-attachments/assets/32e688e2-a1c2-48b9-9783-bb8c54ed57f4)

This menu live updates, meaning you will see the values update every tick to reflect the current values. This will show a life reflection of spawners and blocks aging to their full value.
![image](https://github.com/user-attachments/assets/d80d12e1-343e-4835-85bf-aa876f73c412)

Clicking a head will show that factions Asset breakdown. Each head represents a stack.

![image](https://github.com/user-attachments/assets/816b90d1-d419-414d-b482-857dfe3f8384)

Blocks and Spawners havea configurable grace period after placement, in case players want to move the stack, or it was an accidental placement.

In this case after 5 minutes, the spawner will "lock" and players will be required to pay a configurable amount of the spawners current aged value to pick up the spawner (or stack).


## Usage
### Requirements
- **Minecraft Server**: Spigot/Paper 1.21.1 (For now. Future builds may include support back to 1.16. Dont ask for 1.8.8 support)
- **Dependencies**:
  - [RoseStacker](https://modrinth.com/plugin/rosestacker) (for stacked spawners and blocks).
  - [Vault](https://www.spigotmc.org/resources/vault.34315/) (for economy integration).
  - [FactionsUUID](https://www.spigotmc.org/resources/factionsuuid.1035/) (well.... duh)

### Installation
1. Download the latest `SlashFTop-<version>.jar` from the [Releases](https://github.com/thegeekedgamer/SlashFTop/releases) page.
2. Place the JAR file in your server’s `plugins/` directory.
3. Ensure RoseStacker, Vault, and FactionsUUID (or a fork) are installed and configured.
4. Start your server to generate the plugin’s configuration files.
5. Configure the plugin as needed (e.g., set valuable blocks, age time, etc. in the config).


To use this plugin, ensure your server is running Spigot or Paper 1.21.1. Future versions will support newer (and some older) Minecraft versions as needed.

## Building Locally
Thanks to [Gradle](https://gradle.org/), building locally is easy. Run the following command:

```bash
./gradlew build

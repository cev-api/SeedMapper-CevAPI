# Seedmapper 2.21.x (MC1.21.11) - Modified by CevAPI

Original Repo: https://github.com/xpple/SeedMapper/

## Relationship to upstream

This project is a friendly, independent fork of Seedmapper. I have only proposed one feature to the upstream, which was [accepted](https://github.com/xpple/SeedMapper/commit/fb3a3bd0b2d657ac54b35c39fffa990e02b35da0), but I am welcome to them incorporating further changes. I will sporatically maintain this project and re-base/sync with the upstream project.

## Compiling & Running

Original instructions apply.

## Improvements

### Zoom
Zoom further out on the SeedMap. Limited to approx 100,000 which is insane already. 

You can adjust your own maximum zoom (I recommend 6000) using the command ```/sm:config Zoom set 6000```.

![Zoomies](https://i.imgur.com/utIgDkp.png)

### World Border Limit
Limit the SeedMap and Minimap to a server's world border size so the map only shows that area (centered at 0,0).

Set it with ```/sm:config WorldBorder set 8000``` to show a border from -8000 to +8000 on both axes for all dimensions. Use 0 to disable.

You can also override per dimension:
- ```/sm:config WorldBorderOverworld set 8000```
- ```/sm:config WorldBorderNether set 8000```
- ```/sm:config WorldBorderEnd set 8000```

Per-dimension values take priority when set; use 0 to fall back to the global border. Border values are saved per server.

### Datapack Structures Import
Import a datapack URL so its custom structures appear on the SeedMap. Datapack structures render as solid colored squares (one color per structure) and show up in the datapack toggle list so you can enable/disable them like vanilla features. You can also right‑click a datapack structure and mark it complete/incomplete (a green tick is drawn on the icon).

Datapack structures load differently to vanilla: vanilla features are built-in, while datapack structures are parsed from the provided pack and then cached. They load in the background and appear progressively as tiles are processed.

Version mismatch handling: if the server version doesn't match the client, SeedMapper avoids loading registries that frequently fail across versions (notably enchantments and enchantment providers). This lets the worldgen data load even when other datapack content is incompatible.

Caching scope: datapack structures are cached for the current game session. Switching dimensions or servers keeps the cached structures available, but restarting the game will reload them.

Import a datapack:
- ```/sm:datapack import <url>```

Save the last imported URL for the current server (by IP):
- ```/sm:datapack save```

Load the saved URL for the current server:
- ```/sm:datapack load```

Read the current datapack URL (last imported or saved for this server):
- ```/sm:datapack read```

Change the datapack structure color scheme (applies immediately):
- ```/sm:datapack colorscheme 1``` - current scheme
- ```/sm:datapack colorscheme 2``` - secondary scheme
- ```/sm:datapack colorscheme 3``` - third scheme
- ```/sm:datapack colorscheme random``` - generate and persist a vibrant, high-contrast palette until you run this command again

Change the datapack structure icon style (applies immediately):
- ```/sm:datapack iconstyle 1``` - small flat colored squares (default)
- ```/sm:datapack iconstyle 2``` - large flat colored squares 
- ```/sm:datapack iconstyle 3``` - colored potion bottle icons

Enable or disable auto-loading on join:
- ```/sm:datapack autoload true```
- ```/sm:datapack autoload false```

Autoload uses the saved URL for the server you are joining but will prefer the cached datapack copy if it exists. The URL and cache is keyed by the server address.


![Datapack](https://i.imgur.com/65pVVqs.png)

### Double Click Recenter
Double clicking anywhere on the map will recenter the map to the players location.

### Directional Arrow
This change was accepted upstream, however in my fork I have adjusted the size of it as well as the icon itself giving it a white fill.

![Arrow](https://i.imgur.com/pkodE8d.png)

### World Presets

This has been implemented by upstream, but my way does not require you to input the seed again. Both methods are available to use in this fork.

If the server you're on uses anything other than the default world preset (Large Biomes, Single Biome, No Beta Ocean, Force Ocean Variants, ~~Amplified~~, ~~Superflat~~) this will greatly change the world generation. Change the preset to match the server in order to produce an accurate seedmap. 

Note that Amplified and Superflat biomes are not implemented yet and are placeholders.

- ```/sm:preset list``` — show available presets  
- ```/sm:preset set <id>``` — set SeedMapper’s preset 

### Seed Map Minimap

This has been implemented in the [upstream](https://github.com/xpple/SeedMapper/commit/fb3a3bd0b2d657ac54b35c39fffa990e02b35da0)!

My fork still remains different, has different commands and retains the opacity functionality.

- Run ``` /sm:minimap ``` to open a live SeedMap minimap in the top-left corner of the HUD.  
  - Use ``` /sm:minimap on/off ``` or  to explicitly control whether it is shown.

- The minimap:
  - Renders the same features you selected on the main map.
  - Tracks your current position in real time.
  - Ideal for overlaying over Xaeros Minimap (Default settings suits size 152).

- Position & size:
  - Move it horizontally with ``` /sm:config SeedMapMinimapOffsetX ```
  - Move it vertically with ``` /sm:config SeedMapMinimapOffsetY ```
  - Change width with ``` /sm:config SeedMapMinimapWidth ```
  - Change height with ``` /sm:config SeedMapMinimapHeight ```

- Display options:
  - Rotate the map with the player’s facing using ``` /sm:config SeedMapMinimapRotateWithPlayer ```
  - Adjust zoom independently from the main map via ``` /sm:config SeedMapMinimapPixelsPerBiome ```
  - Scale feature icons with ``` /sm:config SeedMapMinimapIconScale ```
  - Fine tune the background opacity with ``` /sm:config SeedMapMinimapOpacity ``` without affecting icon readability.

![Map1](https://i.imgur.com/w5U6Aux.png) ![Map2](https://i.imgur.com/MXqXY5n.png)

### Location/Feature Isolation
Ctrl+Click a location icon in SeedMap to isolate it (hide all other icons). Ctrl+Click the same icon again to restore your previous icon set.

You can Ctrl+Click other locations without losing your original selection, as long as you toggle the same icon off again (Ctrl+Click twice) and you don’t add or remove any locations in between.

Special case: Ctrl+Clicking the spawn icon also auto-centers the map on spawn. Your player icon will also remain on, so long as it was enabled to begin with.

### Icon Text
This has been implemented in the [upstream](https://github.com/xpple/SeedMapper/commit/ccc9ec0e044465518e96e9b8d7ac458f671af1c5)! However my variant also covers the icons on the map itself.

When hovering over location icons in the SeedMap it will display text telling you what the locations are.

![Text](https://i.imgur.com/A5gCXgP.png)

### Added Elytra/End Ship Locations
This has now been implemented by upstream. They have unified both End City Ships and End Cities together. I have accepted this change but altered it to retain my Elytra icon, this allows you to explicitly look only for Elytras. The command ```/sm:locate feature end_city_ship``` also still applies.

![Elytra](https://i.imgur.com/fFxoFX4.png)

### Export SeedMap
- Added **Export JSON** button on the top right of the SeedMap screen which will export all selected locations to a JSON in the folder ```SeedMapper/exports/<Server IP>_<Seed>-<Date/Time>.json```.
- Added **Export Xaero** button on the top right of the SeedMap screen which will export all selected locations into Xaero World Map waypoints for the server you're in. Disconnect from the server you're in and reconnect and the waypoints will appear in Xaero.

### Improved ESP
Configurable ESP settings allowing for custom colors, fill and transparency.

Example: ```/sm:config ESP BlockHighlightESP set outlineColor #ff0000 outlineAlpha 0.5 fillEnabled true fillColor #00ff00 fillAlpha 0.35```

![ESP](https://i.imgur.com/LaHAJnI.png)

### Auto Clear Highlights
When an existing ESP highlight is already rendered and the user makes a new highlight run (on a new block/area) the highlights are now automatically cleared/refreshed. This allows air/lava checks to apply on new results.

### Improved Waypoints
Supports [Wurst7-CevAPI](https://github.com/cev-api/Wurst7-CevAPI) waypoints, Xaero Waypoints and its own waypoint system via right click context menu. 

Can now finally remove SeedMapper waypoints with via a right click context menu.

Added the ability to set the waypoint compass overlay to be manually toggled instead of automatic with the command ```/sm:config ManualWaypointCompassOverlay set true/false```. When set to true you have to right click on a waypoint to manually enable the waypoint compass for that particular waypoint. This makes your screen less cluttered when you have multiple waypoints.

I also added the ability to import waypoints from [Wurst7-CevAPI](https://github.com/cev-api/Wurst7-CevAPI). Will soon be adding Xaero waypoint as well.

![Map](https://i.imgur.com/1qDgQw7.png)

### Highlight Timeout Setting
Can now change the default 5 minute render timeout with ```/sm:config ESP Timeout```

### Loot Table Browser

Can now visually search and browse through the loot table for the given map area. Click the 'Loot Table' button on the seed map to open the browser. You can also make ESP highlights or waypoints to the chests or simply copy the coordinates. Enchantments will be highlighted with colors and icons. 

![LootTable](https://i.imgur.com/lnT5LsP.png)

### Export Loot Table
Can now export the entire loot table for the map you're viewing (or any other dimension) via the command ```/sm:exportloot <radius> [dimension] [structures/all]```.

Exported data will be located in ```SeedMapper/loot/<Server IP>_<Seed>-<Date/Time>.json```

### Auto Apply SeedCracker Seed
If the server you're in already has a seed in the database, or it has just been cracked it will be auto applied and saved. 

You can enable/disable this with ```/sm:config AutoApplySeedCrackerSeed true/false```

### Mark As Complete
Can now right click on locations and mark them as complete/incomplete which adds a green tick over the icon.

![Complete Me](https://i.imgur.com/tITHz8W.png)

### OreAirCheck Expanded
Now also skips highlights when an ore position is lava‑filled (same logic as air check).

### Notes
If using the original SeedMapper after using my fork you must erase my ```config.json``` first due to the mismatch of settings.

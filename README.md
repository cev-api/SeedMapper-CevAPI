# Seedmapper 2.17.x (MC1.21.10)- Modified by CevAPI

Original Repo: https://github.com/xpple/SeedMapper/

## Relationship to upstream

This project is a friendly, independent fork of Seedmapper. I have not proposed any improvements or features to the upstream but I am welcome to them incorporating my changes. I will sporatically maintain this project and re-base/sync with the upstream project.

## Compiling & Running

Original instructions do not apply. Clone and compile as normal. I have included the Cubiome and JExtract files already.

As for running the mod, the main difference between this mod and upstream is that it will NOT function under Java 23, it must be on Java 21 with the enable preview flag. See below.

## Improvements

### Java 21
Project now builds against Java 21 LTS, however, Minecraft/Fabric must have the flag ```--enable-preview``` added to the Java arguments. 

This tells the JVM to accept preview bytecode and APIs. Functionally nothing else changes in Minecraft, Fabric or any of the other mods you have installed.


![Flag](https://i.imgur.com/rOsP2H0.png)

### Zoom
Zoom further out on the SeedMap. Limited to approx 30,000x30,000. I suggest enabling the clear cache setting below.

![Zoomies](https://i.imgur.com/utIgDkp.png)

### Directional Arrow
Around your player icon in the SeedMap there will be a little arrow showing you which direction you're facing. This is toggleable with ```/sm:config ShowPlayerDirectionArrow```.

![Arrow](https://i.imgur.com/pkodE8d.png)

### World Presets

If the server you're on uses anything other than the default world preset (Amplified, Superflat, Large Biomes, Single Biome) this will greatly change the world generation. Change the preset to match the server in order to produce an accurate seedmap.

- /sm:preset list — show available presets  
- /sm:preset set <id> — set SeedMapper’s preset 

### Seed Map Minimap

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

### Memory Handling
Added a config option ```/sm:config ClearSeedMapCachesOnClose``` to clear tiles, per‑world locations and any other relevant caches. When enabled, this prevents FPS dips from garbage collection on large caches after zooming out far and then closing the map. Opening the map again will result it in being loaded like its the first time, at smaller zoom levels this isn't a problem.

### Icon Text
When hovering over location icons in the SeedMap it will display text telling you what the locations are.

![Text](https://i.imgur.com/A5gCXgP.png)

### Added Elytra/End Ship Locations
~~Can now find Elytra via locating End Ships with the locate command ```/sm:locate feature end_city_ship``` or simply selecting the Elytra icon in the SeedMap~~

This has now been implemented by upstream. They have unified both End City Ships and End Cities together. I have accepted this change but altered it to retain my Elytra icon, this allows you to explicitly look only for Elytras. The command ```/sm:locate feature end_city_ship``` also still applies.

![Elytra](https://i.imgur.com/fFxoFX4.png)

### Export SeedMap
- Added **Export JSON** button on the top right of the SeedMap screen which will export all selected locations to a JSON
- Added **Export Xaero** button on the top right of the SeedMap screen which will export all selected locations into Xaero World Map waypoints for the server you're in. Disconnect from the server you're in and reconnect and the waypoints will appear in Xaero.

### Improved ESP

Configurable ESP settings allowing for custom colors, fill (imperfect) and transparency.

Example: ```/sm:config blockhighlightesp set outlineColor #ff0000 outlineAlpha 0.5 fillEnabled true fillColor #00ff00 fillAlpha 0.35```

![ESP](https://i.imgur.com/S9KeYpR.png)

### Highlight Timeout Setting

Can now change the default 5 minute render timeout with ```/sm:config esptimeout```

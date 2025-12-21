# Seedmapper 2.18.x (MC1.21.11) - Modified by CevAPI

Original Repo: https://github.com/xpple/SeedMapper/

## Relationship to upstream

This project is a friendly, independent fork of Seedmapper. I have not proposed any improvements or features to the upstream but I am welcome to them incorporating my changes. I will sporatically maintain this project and re-base/sync with the upstream project.

## Compiling & Running

Original instructions apply.

## Improvements

### Zoom
Zoom further out on the SeedMap. Limited to approx 100,000 which is insane already. 

You can adjust your own maximum zoom (I recommend 6000) using the command ```/sm:config Zoom set 6000```.

![Zoomies](https://i.imgur.com/utIgDkp.png)

### Directional Arrow
This change was accepted upstream, however in my fork I have adjusted the size of it as well as the icon itself giving it a white fill.

![Arrow](https://i.imgur.com/pkodE8d.png)

### World Presets

If the server you're on uses anything other than the default world preset (Large Biomes, Single Biome, ~~Amplified~~, ~~Superflat~~) this will greatly change the world generation. Change the preset to match the server in order to produce an accurate seedmap. 

Note that Amplified and Superflat biomes are not implemented yet and are placeholders.

- ```/sm:preset list``` — show available presets  
- ```/sm:preset set <id>``` — set SeedMapper’s preset 

### Seed Map Minimap

This is soon to be accepted by upstream!

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

### Icon Text
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

Example: ```/sm:config BlockHighlightESP set outlineColor #ff0000 outlineAlpha 0.5 fillEnabled true fillColor #00ff00 fillAlpha 0.35```

![ESP](https://i.imgur.com/LaHAJnI.png)

### Improved Waypoints
Supports [Wurst7-CevAPI](https://github.com/cev-api/Wurst7-CevAPI) waypoints, Xaero Waypoints and its own waypoint system via right click context menu. 

Can now finally remove SeedMapper waypoints with via a right click context menu.

![Map](https://i.imgur.com/1qDgQw7.png)

### Highlight Timeout Setting
Can now change the default 5 minute render timeout with ```/sm:config EspTimeoutMinutes```

### Export Loot Table
Can now export the entire loot table for the map you're viewing by clicking ```Export Loot``` or via commands such as ```/sm:exportloot <radius> [dimension] [structures/all]```.

Exported data will be located in ```SeedMapper/loot/<Server IP>_<Seed>-<Date/Time>.json```

### Auto Apply SeedCracker Seed
If the server you're in already has a seed in the database it will be auto applied and saved.

You can enable/disable this with ```/sm:config AutoApplySeedCrackerSeed true/false```

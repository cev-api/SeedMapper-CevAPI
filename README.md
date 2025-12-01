# Seedmapper 2.16.x (MC1.21.10)- Modified by CevAPI

Original Repo: https://github.com/xpple/SeedMapper/

## Relationship to upstream

This project is a friendly, independent fork of Seedmapper. I have not proposed any improvements or features to the upstream but I am welcome to them incorporating my changes. I will sporatically maintain this project and re-base/sync with the upstream project.

## Installation and pre-requisites

See original [repo](https://github.com/xpple/SeedMapper) for the project. A precompiled version of my fork will be made available for download.

## Improvements

![Screenshot](https://i.imgur.com/m1lzryd.png)

### Java 21
Project now builds against Java 21 LTS, however, Minecraft/Fabric must have the flag ```--enable-preview``` added to the Java arguments. 

This tells the JVM to accept preview bytecode and APIs. Functionally nothing else changes in Minecraft, Fabric or any of the other mods you have installed.

![Flag](https://i.imgur.com/rOsP2H0.png)

### Zoom
Zoom further out on the SeedMap

### Memory Handling
Added a config option ```/sm:config ClearSeedMapCachesOnClose``` to clear tiles, per‑world locations and any other relevant caches. When enabled, this prevents FPS dips from garbage collection on large caches after zooming out far and then closing the map.

### Icon Text
When hovering over location icons in the SeedMap it will display text telling you what the locations are.

### Added Elytra/End Ship Locations
Can now find Elytra via locating End Ships with the locate command ```/sm:locate feature end_city_ship``` or simply selecting the Elytra icon in the SeedMap

### Export SeedMap
- Added **Export JSON** button on the top right of the SeedMap screen which will export all selected locations to a JSON
- Added **Export Xaero** button on the top right of the SeedMap screen which will export all selected locations into Xaero World Map waypoints for the server you're in. Disconnect from the server you're in and reconnect and the waypoints will appear in Xaero.

### Improved ESP

Configurable ESP settings allowing for custom colors, fill (imperfect) and transparency.

Example: ```/sm:config blockhighlightesp set outlineColor #ff0000 outlineAlpha 0.5 fillEnabled true fillColor #00ff00 fillAlpha 0.35```

### Highlight Timeout Setting

Can now change the default 5 minute render timeout with ```/sm:config esptimeout```

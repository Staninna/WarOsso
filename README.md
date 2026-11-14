# WarOsso

Type a house number, and WarOsso walks you to that door: which block, which stairwell or intercom,
which floor, and the how-manyth door on the gallery. It is made for one residential complex on
Bulkpad in Breda, and for one phone running GrapheneOS.

Hold the phone upright and the camera shows an arrow on the ground plus a label on every entrance.
Lay it flat and you get a map with your position and a line to the entrance.

- 100% free software, GPL-3.0, and so is every library in it.
- No Google services. Location comes from the phone's own GPS, the map from
  [OpenFreeMap](https://openfreemap.org) through [MapLibre](https://maplibre.org).
- No tracking, no telemetry, no account. The network is used for map tiles, and for the address
  list when you ask for a refresh. The map tiles of the complex are kept for offline use.
- English and Dutch.

## Install

Releases are signed APKs on this repository's Releases page.
[Obtainium](https://obtainium.imranr.dev) can install and update from there.

The app needs Android 17.

## Setting up

The app ships with the public address list of the complex: house numbers from BAG and building
outlines, both as mapped in OpenStreetMap. What it cannot know is where the entrances are and which
doors they lead to. You add that once, in Setup:

1. Tap the map where a stairwell or shared front door is.
2. Say whether it is a stairwell or an intercom door.
3. Add its floors. The app proposes them from the address list; you check the order of the doors,
   left to right as you face the building.

Setup shows which numbers no entrance covers yet. Settings can export your setup to
`warosso.json` and import it again.

## Building

    ./gradlew :app:assembleDebug        # debug build with a position simulator in Settings
    ./gradlew :app:testDebugUnitTest    # the unit tests

`tools/fetch-seed.sh` downloads the address list again from the Overpass API and rewrites
`app/src/main/assets/seed.json`.

`SPEC.md` describes what the app does and why.

## Licence and credits

WarOsso is licensed under the [GNU General Public License 3.0](LICENSE).

- Address and building data © OpenStreetMap contributors, under the Open Database License.
- Map tiles © OpenMapTiles, served by OpenFreeMap.
- The typeface is Google Sans Flex, under the [SIL Open Font License 1.1](licenses/GoogleSansFlex-OFL.txt).
- Icons are Material Symbols, under the Apache License 2.0.

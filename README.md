# WarOsso

Type a house number, and WarOsso walks you to that door: which block, which stairwell or intercom,
which floor, and the how-manyth door on the gallery. It is made for one residential complex on
Bulkpad in Breda, and for one phone running GrapheneOS.

Hold the phone upright and the camera shows an arrow on the ground plus a label on every entrance.
Lay it flat and you get a map with your position and a line to the entrance.

The camera view is as good as the phone's GPS and compass, no better: expect a label to be a few
metres off its door. The map is the steadier of the two.

A compass can be plain wrong, next to a car or with a magnet in the phone's case. When the labels
sit in the wrong place, drag them sideways onto the real entrances. The phone then holds that
direction on its gyroscope until you tap "Use compass". When you walk one way and the app thinks
the phone points the other, it says so: point the phone the way you walk and tap "Fix".

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
2. Say whether it is a stairwell, an intercom door or a plain door.
3. Add its floors. The app proposes them from the address list; you check the order of the doors,
   left to right as you face the building.

Where the ground-floor homes have their own front door on the street, they need no entrance. Open
Buildings, tap the building and switch on "Ground floor needs no entrance". Every number of that
building that no entrance lists is then walked to directly, to its point in the address list.

A building with several stairwells needs its floors only once. Open the entrance and tap the
copy button: the copy lands next to it on the map with the same floors and doors. Drag it to the
other stairwell. Every number then goes through the entrance closest to its address, and moving
either marker moves the split. Where a stairwell comes out partway along a gallery, the directions
say which way to turn there and count the doors from the stairs.

Setup shows which numbers are not covered yet. Settings can export your setup to
`warosso.json` and import it again.

## Building

    ./gradlew :app:assembleDebug        # debug build with a position simulator in Settings
    ./gradlew :app:testDebugUnitTest    # the unit tests

`tools/fetch-seed.sh` downloads the address list again from the Overpass API and rewrites
`app/src/main/assets/seed.json`.

## Licence and credits

WarOsso is licensed under the [GNU General Public License 3.0](LICENSE).

- Address and building data © OpenStreetMap contributors, under the Open Database License.
- Map tiles © OpenMapTiles, served by OpenFreeMap.
- The typeface is Google Sans Flex, under the [SIL Open Font License 1.1](licenses/GoogleSansFlex-OFL.txt).
- Icons are Material Symbols, under the Apache License 2.0.

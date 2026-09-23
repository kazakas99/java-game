# java-game

A 2D tile-based game built with [FXGL](https://github.com/AlmasB/FXGL) 21.1 on Java 21,
with a built-in level editor.

## Running

Open the project in IntelliJ and run `com.example.GameApp`, or:

```
mvn compile exec:java -Dexec.mainClass=com.example.GameApp
```

The window is 800x600. The maps are larger than that, so the camera scrolls.

## Controls

### Playing

| Key | Action |
|-----|--------|
| `W` `A` `S` `D` | move |
| `F1` | toggle the level editor |

Collect every coin to advance to the next level. Trees, rocks and water block movement.

### Editing (`F1`)

| Input | Action |
|-------|--------|
| click a palette tile | choose the tile to paint |
| click / drag on the map | paint |
| `W` `A` `S` `D` | pan the camera |
| `F5` | save the current level to `levels/levelN.csv` |
| `F9` | reload the current level from disk |
| `F1` | back to playing |

Painting updates collision immediately — paint water and the character cannot walk
there as soon as you switch back.

Clicks below y=530 on screen are ignored so that palette clicks do not also paint the
map underneath. To edit tiles near the bottom of a map, pan down first; edit-mode
panning is deliberately not clamped to the map bounds.

## Levels

Each level is three files:

| File | Location | Purpose |
|------|----------|---------|
| `levelN.tmx` | `assets/levels/` | Tiled source, for editing in [Tiled](https://www.mapeditor.org/) |
| `levelN.csv` | `assets/text/levels/` | tile grid the game actually renders and collides against |
| `levelN_items.csv` | `assets/text/levels/` | player spawn and coin positions, as `type,col,row` |

The two directories are not a mistake: FXGL's `loadText` resolves relative to
`assets/text/`, while image assets resolve relative to `assets/textures/`.

`.csv` holds 0-based tileset indices, the same convention Tiled exports. The game adds
1 on load to get GIDs, so the numbers in the code match what Tiled displays.

To add a level: edit the `.tmx` in Tiled, export as CSV to `assets/text/levels/`, write
an items file, and raise `MAX_LEVELS` in `GameApp`. Obstacle tiles must also be listed
in `isSolid`.

Levels saved with `F5` go to a `levels/` folder next to the project and take precedence
over the bundled ones. Delete that folder to revert.

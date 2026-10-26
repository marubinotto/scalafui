# fuidungeon

A small turn-based dungeon RPG built with Scalafui. Explore randomly generated
rooms and corridors, find each floor’s key, and descend as far as you can before
your HP runs out. **New expedition** chooses
the room count, terrain, and placements automatically; there is no setup screen
or save state.

<img alt="screenshot" src="screenshot.png" width="60%">

## Run locally

From the repository root, install the test dependencies once:

```sh
yarn install
```

Then start the example's development server:

```sh
cd examples/fuidungeon
yarn install
yarn dev
```

In another terminal, **from the repository root**, watch the Scala.js sources:

```sh
sbt '~exampleFuidungeon/fastLinkJS'
```

Open the local URL printed by Vite.

## Controls and objective

Click the map or Tab to focus it. Use arrow keys or WASD to move, and Space to
wait. Holding a key does not repeat actions. The on-screen direction and Wait
buttons dispatch the same actions; click the map again to return to keyboard
movement after using a button.

| Symbol | Meaning |
| --- | --- |
| 🤠 | You |
| `#` | Wall |
| `·` | Floor |
| 🔑 | Key |
| 🧪 | Healing potion |
| 🚪 | Next floor |

Collect this floor’s key and step onto 🚪 to automatically descend to a new
random floor. Without the key, the door behave like ordinary floor. Your HP,
cumulative turn count, and recent journal entries carry over. The key is consumed;
each floor has a new key, new enemies and potions, and a new starting position.
There is no healing between floors and enemies do not act on arrival.

## Implementation

- [Game.scala](src/main/scala/fuidungeon/Game.scala): immutable game state and pure
  turn rules, independent of the DOM. Action randomness is seeded from game state
  and advances only on valid turns, so identical states and actions reproduce
  identical outcomes.
- [Dungeon.scala](src/main/scala/fuidungeon/Dungeon.scala): bounded room generation,
  connections, cropping, and distance calculation. Initialization uses a local
  seeded random generator; the same seed reproduces the complete initial state.
- [Camera.scala](src/main/scala/fuidungeon/Camera.scala): pure pixel-offset calculation
  for tracking, edge clamping, and centering small maps.
- [Main.scala](src/main/scala/fuidungeon/Main.scala): Scalafui model/update/view and
  seed acquisition through `Cmd` for initialization and descent. Only the transition
  into descent emits a new seed command. Actions and restart are locked during
  initialization and descent.
- [index.css](index.css): clipped map and container-query viewport selection. Window
  dimensions are not part of the game model.

There are no additional game-engine dependencies or framework API extensions.

## Test and build

From the **repository root**:

```sh
sbt test exampleFuidungeon/test exampleFuidungeon/fastLinkJS
```

From **examples/fuidungeon**:

```sh
yarn build
yarn preview
```

The Vite production build invokes Scala.js full linking and writes `dist/`.
Tests cover seeded generation and placement, bounded fallback behavior, camera
edges and small maps, combat, item collection, floor transitions and progress
carryover, duplicate input during descent, death, and restart.

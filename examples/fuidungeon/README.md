# fuidungeon

A small turn-based dungeon RPG built with Scalafui. Explore randomly generated
rooms and corridors, find each floor’s key, and descend as far as you can before
your HP runs out. **New expedition** chooses
the room count, terrain, and placements automatically; there is no setup screen
or save state.

## Run locally

Requirements: sbt, Node.js 22.22.2+ (22.x), 24.15.0+ (24.x), or 26+, and Yarn
Classic 1.22.x. Keep `sbt` and Java available on your `PATH`, including to Node
child processes: the Vite plugin invokes sbt.

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
| 👺 | Goblin |
| 🔑 | Key |
| 🧪 | Healing potion |
| 🚪 | Next floor |

Collect this floor’s key and step onto 🚪 to automatically descend to a new
random floor. Without the key, the door behave like ordinary floor. Your HP,
cumulative turn count, and recent journal entries carry over. The key is consumed;
each floor has a new key, new goblins and potions, and a new starting position.
There is no healing between floors and goblins do not act on arrival.

The large **FLOOR 01** display shows the floor you have reached, starting at 1.
There is no final victory or floor limit: aim to reach as deep as possible. HP
reaching zero ends the expedition and displays your reached floor and total turns.
Difficulty does not increase with depth; the same generation and combat rules
apply on every floor. Records are not saved across expeditions or page reloads.

During a descent, actions and restart are temporarily locked. Keyboard focus stays
on the map if it was already there, and the camera follows the new starting
position. **New expedition** resets HP, floor, turns, and journal and generates a
new first floor.

## Turns and combat

- Start with 10 HP. Moving, attacking, or waiting spends one turn. Walking into a
  wall does not spend a turn.
- Walk toward a goblin to deal 2 damage without moving. Goblins have 4 HP; a
  defeated goblin cannot retaliate.
- After your action, surviving goblins act in their fixed ID order. An adjacent
  goblin deals 1 damage. Otherwise, if a route to you is at most five steps long,
  it moves one step closer. Other goblins block the route; they never overlap.
  Equal-length paths prefer up, right, down, then left. Moving does not also attack.
- Keys and potions are collected automatically. A potion immediately restores
  4 HP, capped at 10, and is consumed even at full health.
- Reaching the door with the key spends a turn and starts descent before goblins
  act. Death immediately stops the remaining goblin actions. The journal retains
  the six latest messages, including floor arrivals.

## Random dungeons and camera

Each expedition randomly targets 6–10 rooms, each 4–7 tiles wide and high. New
rooms attach above, right, below, or left of existing rooms, with 1–3 tiles between
them. Connected L-shaped corridors and up to two extra room connections create
branches and possible alternative routes. Corridors may cross other corridors
or rooms.

Generation has a bounded number of placement attempts. If it cannot reach the
target, it keeps at least six accepted rooms; if fewer than six fit, it builds a
six-room chain instead. The final map tightly encloses all floors with a one-tile
wall border. Width and height vary, up to 64 × 64 tiles. All floors are connected.
The door to the next floor is a floor farthest from the starting room's center by shortest path.

For `n` generated rooms, there are `n − 3` goblins, `ceil(n / 2)` potions, and one
key. Placements do not overlap or occupy the start or door. Goblins start at least
four path steps from you. Terrain reachability is guaranteed; identical difficulty
and survival through every placement are not guaranteed.

The camera follows you instantly and stops at map edges. Tiles remain 28 px:
a wide panel shows 15 × 15 tiles, while a panel narrower than 420 px shows 9 × 9.
A map dimension smaller than the viewport is centered. There is no manual map
scrolling, fog of war, exploration history, or minimap. Stats, controls, and notes
stay outside the moving map; on narrow screens they appear below it.

Emoji use the system’s built-in fonts and may look different across operating
systems and browsers. Walls and floors remain text symbols.

## Implementation

- [Game.scala](src/main/scala/fuidungeon/Game.scala): immutable game state and pure
  turn rules, independent of the DOM.
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

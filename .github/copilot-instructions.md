# Game2D Project Instructions

## Architecture & Core Concepts
- **Game Engine**: This is a custom 2D game engine built in Java. The core loop is managed by `GameCore.java`, which handles double buffering, timing, and the main `gameLoop()`.
- **Game Implementation**: `Game.java` extends `GameCore` and is the main entry point. It overrides `init()`, `update(long elapsed)`, and `draw(Graphics2D g)`.
- **Coordinate System & Scrolling**: The game world is larger than the screen. The player remains relatively stationary on screen while the world (tile map and background sprites) moves around them. This is achieved by calculating an offset (`xo`, `yo`) based on the player's position and applying it to all `draw()` calls.
- **Sprites & Animation**: `Sprite.java` manages entities with position, velocity, and an `Animation`. Animations are loaded from sprite sheets.
- **Tile Maps**: `TileMap.java` loads levels from text files (e.g., `maps/map.txt`). Collision detection must be handled manually by checking sprite bounding boxes against tile coordinates.

## Developer Workflows
- **Building**: The project does not use a build tool like Maven or Gradle. Compile the code using `javac`:
  ```bash
  javac -d handout\bin -sourcepath handout\src handout\src\Game.java
  ```
- **Running**: The game must be run from the `handout` directory so it can locate resources (`images/`, `maps/`, `sounds/`):
  ```bash
  cd handout
  java -cp bin Game
  ```

## Project-Specific Conventions
- **Collision Detection**: Do not rely on simple point checks. Use bounding box collision for Sprite-vs-Sprite (`boundingBoxCollision`) and AABB-vs-Tile checks for Sprite-vs-TileMap (`checkTileCollision`). When resolving tile collisions, check the X and Y axes separately to prevent getting stuck on corners.
- **Resource Loading**: Images and sounds are loaded relative to the working directory (e.g., `images/walk.png`). Ensure the working directory is correct when running.
- **Game State**: Keep game state variables (score, flags, lists of entities) in `Game.java`. Use `ArrayList<Sprite>` for managing multiple entities like enemies or clouds.
- **Debugging**: Use the `debug` flag in `Game.java` (toggled with 'B') to draw bounding boxes and tile collision highlights. This is crucial for tuning physics and collisions.

## Integration Points
- **Input**: Override `keyPressed` and `keyReleased` from `GameCore` to handle keyboard input.
- **Audio**: Use the `Sound` class for playing `.wav` files. For background music, MIDI files are supported.

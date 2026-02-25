# Game2D - Complete Technical Report

## Table of Contents
1. [Game Overview](#game-overview)
2. [Game Architecture](#game-architecture)
3. [How to Play](#how-to-play)
4. [Controls](#controls)
5. [Game Mechanics](#game-mechanics)
6. [Code Explanation](#code-explanation)
7. [Engine Classes](#engine-classes)

---

## Game Overview

**Game2D** is a custom Java 2D game engine with two playable game modes:

1. **Map 1 - Flappy Bird Style**: Infinite runner where the player controls a bird that must navigate through gaps between pipes
2. **Map 2 - Platformer**: Side-scrolling platformer with walking enemies and falling penguins

### Key Features
- Custom game engine (no external frameworks)
- Procedural infinite map generation
- Multiple enemy types (walkers, fallers)
- Two distinct gameplay modes
- Debug mode for collision visualization
- Sound effects and background music

### Recent Fixes and Notes
- Fixed malformed tile maps that caused white screen and map load errors by correcting row lengths and header dimensions in both map files.
- Confirmed game launches after map fixes; no map load exceptions during startup.
- Remaining missing specification items: novel sound filter and true parallax scrolling (clouds currently draw in screen space with no parallax effect).

---

## Game Architecture

### File Structure
```
handout/
├── src/
│   ├── Game.java                    # Main game implementation
│   └── game2D/
│       ├── GameCore.java            # Core game engine (base class)
│       ├── Sprite.java              # Entity management
│       ├── TileMap.java             # Level/tile management
│       ├── Tile.java                # Individual tile
│       ├── Animation.java           # Sprite animation
│       ├── Sound.java               # Audio playback
│       └── Velocity.java            # Movement vectors
├── images/                         # Game graphics
├── maps/                          # Level data (text files)
└── sounds/                        # Audio files
```

### Architecture Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                      Game (extends GameCore)                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ Player      │  │ TileMap     │  │ Sprites ArrayLists  │ │
│  │ (Sprite)    │  │             │  │ - clouds            │ │
│  │             │  │             │  │ - walkers           │ │
│  │             │  │             │  │ - fallers           │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ GameCore (Base Engine Class)                           │  │
│  │  - Double buffering                                     │  │
│  │  - Game loop (update/draw)                            │  │
│  │  - Input handling                                      │  │
│  │  - Resource loading                                    │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Core Concepts

1. **Game Loop**: Managed by `GameCore.gameLoop()` using double buffering
2. **Coordinate System**: Player stays centered, world scrolls around them
3. **Tile-based Maps**: Levels loaded from text files
4. **Sprite-based Entities**: All game objects extend `Sprite` class
5. **Animation System**: Frame-based animations from sprite sheets

---

## How to Play

### Starting the Game
```bash
# Compile
javac -d handout\bin -sourcepath handout\src handout\src\Game.java

# Run (from handout directory)
cd handout
java -cp bin Game
```

### Game Modes

#### Mode 1: Flappy Bird (Infinite Runner)
- The bird moves forward automatically
- Press UP/W/SPACE to flap (fly upward)
- Navigate through gaps between pipes
- Avoid hitting pipes or the ground
- Score = time survived

#### Mode 2: Platformer
- Side-scrolling platformer
- Use LEFT/RIGHT or A/D to move
- Jump with UP/W/SPACE
- Reach the end of the map to complete

### Game States
- **Menu**: Press 1 or 2 to select level
- **Playing**: Active gameplay
- **Game Over**: Press SPACE to restart or M for menu
- **Level Complete**: Press M for menu

---

## Controls

| Key | Action |
|-----|--------|
| UP / W / SPACE | Flap/Jump |
| LEFT / A | Move Left (Map 2) |
| RIGHT / D | Move Right (Map 2) |
| S | Play sound effect (Map 1) |
| B | Toggle Debug Mode |
| ESC | Quit Game |
| M | Return to Menu |
| 1 | Select Map 1 (from menu) |
| 2 | Select Map 2 (from menu) |

---

## Game Mechanics

### Physics Constants
```java
float lift = 0.005f;        // Upward force when flapping
float gravity = 0.0002f;   // Downward acceleration
float fly = -0.06f;        // Flap impulse strength
float moveSpeed = 0.06f;   // Forward movement speed
float jumpPower = -0.19f;  // Jump strength (Map 2)
float walkSpeed = 0.08f;   // Walking speed (Map 2)
```

### Collision Detection

1. **Sprite vs Sprite**: Bounding box collision
   ```java
   boundingBoxCollision(s1, s2)
   ```

2. **Sprite vs TileMap**: AABB collision with tile grid
   - Check X and Y axes separately
   - Different behavior per map:
     - Map 1: Any collision = game over
     - Map 2: Stop at walls, land on platforms

### Enemy Types

1. **Walkers**: Move left-right between pillars
2. **Fallers (Penguins)**: Fall from sky at intervals
   - Map 1: Every 4 seconds
   - Map 2: Every 3 seconds

### Procedural Generation
- Infinite pipe generation in Map 1
- Pipes generated 30 columns ahead of player
- Gap position smoothed to prevent impossible jumps

---

## Code Explanation

### Game.java - Main Game Class

#### Constants (Lines 28-82)
- Screen dimensions: 640x480
- Physics values: gravity, lift, jump power
- Game state flags: debug, gameOver, inMenu

#### Key Variables
```java
Sprite player;              // The main character
TileMap tmap;              // Current level map
ArrayList<Sprite> clouds;  // Background clouds
ArrayList<Sprite> walkers; // Ground enemies
ArrayList<Sprite> fallers; // Falling enemies
int currentLevel;          // 1 or 2
```

#### Core Methods

1. **init()** (Line 108)
   - Loads tile map from file
   - Creates player sprite
   - Initializes cloud positions
   - Loads sounds
   - Sets up mouse listener

2. **initialiseGame()** (Line ~550)
   - Resets player position
   - Clears enemies
   - Resets score and lives
   - Spawns initial enemies

3. **update(long elapsed)** (Line ~600)
   - Updates player physics
   - Scrolls tile map
   - Updates all sprites
   - Handles collision detection
   - Spawns new enemies

4. **draw(Graphics2D g)** (Line ~900)
   - Draws background
   - Draws tile map
   - Draws all sprites
   - Draws UI (score, lives)
   - Draws debug info if enabled

5. **keyPressed(KeyEvent e)** (Line 1195)
   - Handles all keyboard input
   - Maps keys to game actions
   - Controls player movement

#### Collision Methods

1. **boundingBoxCollision(Sprite s1, Sprite s2)** (Line 1224)
   - Checks if two sprites overlap
   - Returns boolean

2. **checkTileCollision(Sprite s, TileMap tmap, float oldX, float oldY)** (Line 1240)
   - Checks sprite against tile grid
   - Handles different collision responses per map
   - Uses separate X/Y axis checking

### GameCore.java - Engine Base Class

The `GameCore` class provides the foundation:

1. **Double Buffering**: Uses offscreen buffer for smooth rendering
2. **Game Loop**: Calls update() and draw() at 60 FPS
3. **Resource Loading**: Methods to load images, sounds
4. **Input Handling**: Key and mouse event management

Key methods:
- `run(boolean full, int x, int y)` - Start game loop
- `gameLoop()` - Main loop calling update/draw
- `loadImage(String fileName)` - Load graphics
- `loadClip(String fileName)` - Load WAV sounds
- `loadMidi(String fileName)` - Load MIDI music

### Sprite.java

Represents any game entity:
- Position (x, y)
- Velocity (vx, vy)
- Animation
- Bounding box for collisions

### TileMap.java

Manages the game world:
- Loads maps from text files
- Provides tile lookup
- Supports infinite map expansion
- Each character in map file = different tile type

### Animation.java

Handles sprite animations:
- Load from sprite sheets
- Frame timing
- Loop control

---

## Engine Classes Summary

| Class | Purpose |
|-------|---------|
| **GameCore** | Base engine with game loop, buffering, input |
| **Game** | Main game implementation (extends GameCore) |
| **Sprite** | Game entity with position, velocity, animation |
| **TileMap** | Grid-based level data |
| **Tile** | Individual grid cell |
| **Animation** | Frame-based sprite animation |
| **Sound** | WAV audio playback |
| **Velocity** | Movement vector |

---

## Debug Mode

Press **B** to toggle debug mode, which displays:
- Player bounding box (red)
- Tile collision highlights (yellow)
- Enemy bounding boxes
- Player position coordinates

---

## Building & Running

```bash
# From project root
cd handout

# Compile
javac -d bin -sourcepath src src/Game.java

# Run
java -cp bin Game
```

**Important**: Must run from `handout` directory to find resources (images/, maps/, sounds/)

---

*Report generated for Game2D Java 2D Game Engine*

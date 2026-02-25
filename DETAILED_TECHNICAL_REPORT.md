# Game2D - Detailed Technical Report with Code Analysis

## Table of Contents
1. [Project Overview](#project-overview)
2. [Game Architecture](#game-architecture)
3. [Detailed Code Analysis](#detailed-code-analysis)
   - [Game.java - Main Game Implementation](#gamejava---main-game-implementation)
   - [GameCore.java - Engine Foundation](#gamecorejava---engine-foundation)
   - [Sprite.java - Entity System](#spritejava---entity-system)
   - [TileMap.java - Level Management](#tilemapjava---level-management)
   - [Animation.java - Animation System](#animationjava---animation-system)
   - [Tile.java - Tile Data Structure](#tilejava---tile-data-structure)
   - [Sound.java - Audio System](#soundjava---audio-system)
   - [Velocity.java - Movement Calculations](#velocityjava---movement-calculations)

---

## Project Overview

**Game2D** is a custom 2D game engine written in pure Java with no external game frameworks. It implements two distinct game modes:
- **Map 1**: Infinite Flappy Bird-style runner with procedural pipe generation
- **Map 2**: Side-scrolling platformer with finite level

### Key Technologies
- **Language**: Java
- **Graphics**: Java2D (Graphics2D)
- **Audio**: javax.sound.sampled (WAV), javax.sound.midi (MIDI)
- **Architecture**: Object-Oriented with inheritance-based design

---

## Game Architecture

### Architectural Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Application Layer                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Game.java                              │   │
│  │  - Main game logic and state management                  │   │
│  │  - Input handling                                         │   │
│  │  - Game modes (Menu, Map1, Map2, GameOver)              │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ↓ extends
┌─────────────────────────────────────────────────────────────────┐
│                         Engine Layer                             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  GameCore.java                            │   │
│  │  - Game loop (update/draw cycle)                         │   │
│  │  - Double buffering                                       │   │
│  │  - Window management                                      │   │
│  │  - Input event handling                                   │   │
│  │  - Resource loading                                       │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ↓ uses
┌─────────────────────────────────────────────────────────────────┐
│                      Component Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Sprite     │  │   TileMap    │  │     Animation        │  │
│  │ - Position   │  │ - Grid data  │  │ - Frame sequencing  │  │
│  │ - Velocity   │  │ - Collision  │  │ - Timing control    │  │
│  │ - Animation  │  │ - Rendering  │  │ - Sprite sheets     │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │    Tile      │  │    Sound     │  │     Velocity         │  │
│  │ - Character  │  │ - WAV files  │  │ - Speed & Angle     │  │
│  │ - Position   │  │ - Threading  │  │ - Vector math       │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow Architecture

```
User Input → KeyListener → Game.keyPressed()
                              ↓
                        Update Flags (flap, moveLeft, moveRight)
                              ↓
                        Game.update(elapsed)
                              ↓
                    ┌─────────┴─────────┐
                    ↓                   ↓
            Apply Physics        Update Entities
         (gravity, velocity)    (sprites, animations)
                    ↓                   ↓
                    └─────────┬─────────┘
                              ↓
                    Check Collisions
                (Sprite vs Sprite, Sprite vs TileMap)
                              ↓
                    Resolve Collisions
                (Position correction, game over, etc.)
                              ↓
                        Game.draw(g)
                              ↓
                    Draw layers in order:
                1. Background (sky color)
                2. Clouds (no offset - screen space)
                3. Walkers (with offset)
                4. Falling penguins (with offset)
                5. TileMap (with offset)
                6. Player (with offset)
                7. UI (score, lives, menus)
                              ↓
                    Display to screen (double buffered)
```

### Game State Machine

```
┌────────────┐
│   MENU     │ ─── Press 1 or 2 ──→ ┌────────────┐
│  (inMenu)  │                       │  PLAYING   │
└────────────┘                       │ (inMenu=   │
      ↑                              │  false)    │
      │                              └────────────┘
      │                                    │
      │                                    │
   Press M                        Collision/Death
      │                                    ↓
      │                              ┌────────────┐
      │                              │ GAME OVER  │
      └────────────────────────────  │ (gameOver= │
                                     │   true)    │
                                     └────────────┘
                                           │
                                      Press SPACE
                                           ↓
                                     Restart Game
```

---

## Detailed Code Analysis

---

## Game.java - Main Game Implementation

**Location**: `handout/src/Game.java`  
**Lines**: 1392 lines  
**Purpose**: Main game implementation that extends GameCore and implements game-specific logic

### Class Structure

```java
public class Game extends GameCore implements MouseListener 
{
    // Screen dimensions
    static int screenWidth = 640;
    static int screenHeight = 480;
    
    // Physics constants
    float lift = 0.005f;
    float gravity = 0.0002f;
    float fly = -0.06f;
    float moveSpeed = 0.06f;
    float jumpPower = -0.19f;
    float walkSpeed = 0.08f;
    
    // Game state
    boolean flap = false;
    boolean moveRight = false;
    boolean moveLeft = false;
    boolean debug = false;
    boolean gameOver = false;
    boolean inMenu = true;
    boolean levelComplete = false;
    boolean onGround = false;
    int currentLevel = 1;
    
    // Game entities
    Sprite player = null;
    ArrayList<Sprite> clouds = new ArrayList<Sprite>();
    ArrayList<Sprite> walkers = new ArrayList<Sprite>();
    ArrayList<Sprite> fallers = new ArrayList<Sprite>();
    TileMap tmap = new TileMap();
    
    // Game metrics
    long total;
    int lives = 3;
    long fallerTimer = 0;
    
    // Procedural generation
    int generatedUpToColumn = 0;
    int nextPipeColumn = 0;
    int lastGapTop = -1;
}
```

### Function Breakdown

#### 1. main() - Application Entry Point

**Lines**: 93-98  
**Purpose**: Creates and starts the game application

```java
public static void main(String[] args) {
    Game gct = new Game();
    gct.init();
    // Start in windowed mode with the given screen height and width
    gct.run(false, screenWidth, screenHeight);
}
```

**What it does**:
1. Creates a new `Game` instance
2. Calls `init()` to load resources
3. Calls `run()` inherited from `GameCore` to start the game loop
4. Parameters: `false` = windowed mode, 640x480 = screen dimensions

---

#### 2. init() - Resource Initialization

**Lines**: 108-216  
**Purpose**: Loads all game resources and sets up initial state

```java
public void init() {         
    Sprite s;
    
    // Auto-detect the base path for resources
    if (new File("maps/map.txt").exists()) {
        basePath = "";
    } else if (new File("handout/maps/map.txt").exists()) {
        basePath = "handout/";
    } else {
        System.err.println("Cannot find resource directory.");
    }
    
    // Load the tile map
    tmap.loadMap(basePath + "maps", "map.txt");
    
    // Set virtual resolution
    virtualWidth = screenWidth;
    virtualHeight = screenHeight;
    
    // Load animations
    landing = new Animation();
    landing.loadAnimationFromSheet(basePath + "images/landbird.png", 4, 1, 60);
    
    walk = new Animation();
    walk.loadAnimationSeries(basePath + "images/walk.png", 3, 4, 100, 6, 5);
    
    // Initialize player
    player = new Sprite(landing);
    
    // Create cloud animations
    Animation ca1 = new Animation();
    ca1.addFrame(loadImage(basePath + "images/clouds-png-13366.png"), 1000);
    // ... (creates 6 different cloud animations)
    
    // Create 50 clouds at random positions
    for (int c = 0; c < numClouds; c++) {
        s = new Sprite(cloudAnims[(int)(Math.random() * cloudAnims.length)]);
        s.setX((int)(Math.random() * screenWidth * 3));
        s.setY((int)(Math.random() * (screenHeight / 2)));
        float cloudSpeed = -(0.008f + (float)(Math.random() * 0.06f));
        s.setVelocityX(cloudSpeed);
        s.show();
        clouds.add(s);
    }
    
    // Load sound
    slowLoopClip = loadClip(basePath + "sounds/slow.wav");
    midiSequencer = loadMidi(basePath + "sounds/Wii Channels - Mii Channel.mid");
    
    initialiseGame();
}
```

**What it does**:
1. **Resource Path Detection**: Automatically finds the correct path to resources whether running from project root or handout folder
2. **Map Loading**: Loads the initial tile map from text file
3. **Animation Loading**: Loads sprite animations from sprite sheets using two methods:
   - `loadAnimationFromSheet()`: Loads all frames from a sprite sheet
   - `loadAnimationSeries()`: Loads a subset of frames starting from a specific index
4. **Cloud Generation**: Creates 50 background clouds with random positions and speeds
5. **Audio Loading**: Loads WAV files as Clips and MIDI files as Sequencers
6. **Game Start**: Calls `initialiseGame()` to set up the first level

---

#### 3. spawnWalkersBetweenPillars() - Walker Enemy Spawning

**Lines**: 200-260  
**Purpose**: Analyzes the tile map to find gaps between vertical pipe columns and spawns walker enemies that patrol those gaps

```java
public void spawnWalkersBetweenPillars() {
    walkers.clear();
    walkerLeftBounds.clear();
    walkerRightBounds.clear();
    
    int tw = tmap.getTileWidth();
    int th = tmap.getTileHeight();
    int mapW = tmap.getMapWidth();
    int mapH = tmap.getMapHeight();
    
    // Find vertical pipe columns
    boolean[] isPipeColumn = new boolean[mapW];
    for (int col = 0; col < mapW; col++) {
        int solidCount = 0;
        for (int row = 0; row < mapH; row++) {
            Tile t = tmap.getTile(col, row);
            if (t != null && t.getCharacter() != '.') solidCount++;
        }
        isPipeColumn[col] = (solidCount >= mapH / 2);
    }
    
    // Walk across columns to find gaps
    int gapStart = -1;
    for (int col = 0; col <= mapW; col++) {
        boolean solid = (col >= mapW) || isPipeColumn[col];
        if (!solid && gapStart < 0) {
            gapStart = col;
        } else if (solid && gapStart >= 0) {
            int gapEnd = col;
            int gapWidth = gapEnd - gapStart;
            
            if (gapWidth >= 3) {
                // Create walker for this gap
                Animation walkerAnim = new Animation();
                walkerAnim.loadAnimationSeries(basePath + "images/walk.png", 
                                                3, 4, 150, 6, 5);
                Sprite walker = new Sprite(walkerAnim);
                
                // Store patrol boundaries
                float leftBound = (gapStart + 1) * tw;
                float rightBound = (gapEnd - 1) * tw;
                float px = (gapStart + gapWidth / 2.0f) * tw;
                float py = mapH * th - walker.getHeight();
                
                walker.setPosition(px, py);
                walker.setVelocityX(0.01f);
                walker.show();
                
                walkers.add(walker);
                walkerLeftBounds.add(leftBound);
                walkerRightBounds.add(rightBound);
            }
            gapStart = -1;
        }
    }
}
```

**Algorithm**:
1. **Identify Pipe Columns**: Loop through each column and count solid tiles. If >= 50% solid, mark as pipe column
2. **Find Gaps**: Scan horizontally to find sequences of non-pipe columns
3. **Spawn Walkers**: For each gap >= 3 tiles wide:
   - Create walker sprite with walking animation
   - Position at center of gap, on ground level
   - Store left/right boundaries for patrol behavior
   - Add to walkers list

**Data Structures**:
- `walkers`: ArrayList of Sprite objects
- `walkerLeftBounds`: ArrayList of Float (minimum X position per walker)
- `walkerRightBounds`: ArrayList of Float (maximum X position per walker)

---

#### 4. spawnFallingPenguin() - Falling Enemy Spawning (Map 1)

**Lines**: 268-315  
**Purpose**: Spawns a falling penguin enemy ahead of the player on a pipe column

```java
public void spawnFallingPenguin() {
    Animation penguinAnim = new Animation();
    penguinAnim.loadAnimationFromSheet(basePath + "images/penguin.png", 4, 1, 150);
    Sprite penguin = new Sprite(penguinAnim);
    
    penguin.setScale(1.0f);
    
    int tw = tmap.getTileWidth();
    int th = tmap.getTileHeight();
    int mapH = tmap.getMapHeight();
    int playerCol = (int)(player.getX() / tw);
    
    // Look for pipe columns ahead of player
    ArrayList<Integer> pipeColumns = new ArrayList<Integer>();
    for (int col = playerCol + 15; col < playerCol + 25; col++) {
        if (col >= tmap.getMapWidth()) break;
        
        // Check if this column contains any pipe tiles
        boolean hasPipe = false;
        for (int row = 0; row < mapH; row++) {
            char tile = tmap.getTileChar(col, row);
            if (tile != '.') {
                hasPipe = true;
                break;
            }
        }
        
        if (hasPipe)
            pipeColumns.add(col);
    }
    
    // Spawn on random pipe column
    if (pipeColumns.size() > 0) {
        int chosenCol = pipeColumns.get((int)(Math.random() * pipeColumns.size()));
        float spawnX = chosenCol * tw + tw / 2;
        penguin.setPosition(spawnX, -30);
        penguin.setVelocityY(0.04f);
        penguin.setVelocityX(0);
        penguin.show();
        fallers.add(penguin);
    }
}
```

**Algorithm**:
1. **Load Animation**: Create penguin animation from sprite sheet (4 frames, 1 row)
2. **Find Valid Spawn Locations**: Search 15-25 tiles ahead of player for pipe columns
3. **Random Selection**: Choose a random pipe column from candidates
4. **Spawn Above Screen**: Position penguin above visible area (-30 pixels)
5. **Set Velocity**: Falling speed (0.04 pixels/ms), no horizontal movement

**Why spawn on pipes?** To ensure the penguin is visible and fair - spawning in gaps would be invisible until too late

---

#### 5. generatePipesAhead() - Procedural Pipe Generation

**Lines**: 369-410  
**Purpose**: Generates pipe obstacles ahead of the player to create infinite gameplay

```java
public void generatePipesAhead() {
    int tw = tmap.getTileWidth();
    int playerCol = (int)(player.getX() / tw);
    int targetCol = playerCol + GENERATE_AHEAD; // 30 tiles ahead
    
    while (generatedUpToColumn < targetCol) {
        generatedUpToColumn++;
        
        // Expand tile map if needed
        if (generatedUpToColumn >= tmap.getMapWidth() - 2)
            tmap.expandWidth(100);
        
        // Place pipe at scheduled column
        if (generatedUpToColumn >= nextPipeColumn) {
            int mapH = tmap.getMapHeight();
            int gapSize = 3 + (int)(Math.random() * 2); // 3 or 4 rows
            int minTop = 1;
            int maxTop = mapH - gapSize - 1;
            
            // Smooth gap position transition
            if (lastGapTop >= 0) {
                minTop = Math.max(minTop, lastGapTop - 2);
                maxTop = Math.min(maxTop, lastGapTop + 2);
            }
            if (maxTop < minTop) maxTop = minTop;
            
            int gapTop = minTop + (int)(Math.random() * (maxTop - minTop + 1));
            generatePipeColumn(nextPipeColumn, gapTop, gapSize);
            lastGapTop = gapTop;
            
            // Optionally spawn walker
            int prevPipeCol = nextPipeColumn - PIPE_SPACING;
            if (prevPipeCol > 0 && Math.random() < 0.5)
                spawnWalkerInGap(prevPipeCol, nextPipeColumn);
            
            nextPipeColumn += PIPE_SPACING;
        }
    }
}
```

**Algorithm**:
1. **Calculate Target**: Determine how far ahead to generate (player position + 30 tiles)
2. **Expand Map**: If approaching map boundary, expand tile map by 100 columns
3. **Generate Pipes**: For each scheduled pipe column:
   - Calculate gap size (3-4 rows randomly)
   - Constrain gap position to be reachable from previous gap (±2 rows)
   - Call `generatePipeColumn()` to write tiles
   - Optionally spawn walker enemy (50% chance)
   - Schedule next pipe (PIPE_SPACING = 7 tiles away)

**Key Variables**:
- `generatedUpToColumn`: Furthest column already generated
- `nextPipeColumn`: Next column that should have a pipe
- `lastGapTop`: Remember last gap position for smoothing
- `PIPE_SPACING`: Constant gap between pipes (7 columns)
- `GENERATE_AHEAD`: How far ahead to generate (30 columns)

**Gap Smoothing**: Prevents impossible jumps by constraining new gap to be within ±2 rows of previous gap

---

#### 6. generatePipeColumn() - Write Single Pipe Column

**Lines**: 418-439  
**Purpose**: Writes a single vertical pipe column into the tile map with a gap

```java
public void generatePipeColumn(int col, int gapTop, int gapSize) {
    int mapH = tmap.getMapHeight();
    int gapBot = gapTop + gapSize + 1; // First row of bottom pipe
    
    for (int row = 0; row < mapH; row++) {
        if (row < gapTop)
            tmap.setTileChar('p', col, row);      // Top pipe body
        else if (row == gapTop)
            tmap.setTileChar('b', col, row);      // Bottom cap of top pipe
        else if (row < gapBot)
            tmap.setTileChar('.', col, row);      // Gap (bird flies here)
        else if (row == gapBot)
            tmap.setTileChar('t', col, row);      // Top cap of bottom pipe
        else
            tmap.setTileChar('p', col, row);      // Bottom pipe body
    }
}
```

**Pipe Structure**:
```
Row  Char  Description
0    'p'   Top pipe body
1    'p'   Top pipe body
2    'p'   Top pipe body
3    'b'   Bottom cap (gapTop)
4    '.'   Gap (bird can fly through)
5    '.'   Gap
6    '.'   Gap
7    't'   Top cap (gapBot)
8    'p'   Bottom pipe body
9    'p'   Bottom pipe body
```

**Character Mapping** (defined in map file):
- `'p'` = pipe body tile
- `'b'` = bottom cap (rounded bottom of top pipe)
- `'t'` = top cap (rounded top of bottom pipe)
- `'.'` = empty space

---

#### 7. initialiseGame() - Game Reset

**Lines**: 611-648  
**Purpose**: Resets game state and loads appropriate level

```java
public void initialiseGame() {
    total = 0;
    lives = 3;
    gameOver = false;
    levelComplete = false;
    fallerTimer = 0;
    
    // Load appropriate map
    if (currentLevel == 1) {
        tmap.loadMap(basePath + "maps", "map.txt");
        generatedUpToColumn = tmap.getMapWidth() - 1;
        nextPipeColumn = generatedUpToColumn + PIPE_SPACING;
        lastGapTop = -1;
        spawnWalkersBetweenPillars();
        generatePipesAhead(); // Pre-generate pipes
    } else {
        tmap.loadMap(basePath + "maps", "map2.txt");
        generatedUpToColumn = -1; // Disable procedural generation
        walkers.clear();
    }
    
    player.setPosition(200, 200);
    player.setVelocity(0, 0);
    player.show();
    
    fallers.clear();
    
    startSlowLoop();
    startTheme();
}
```

**Behavior Differences**:

**Map 1 (Infinite Runner)**:
- Loads `map.txt`
- Enables procedural pipe generation
- Pre-generates 30 columns of pipes ahead
- Spawns walkers in gaps
- Penguins spawn on pipes

**Map 2 (Platformer)**:
- Loads `map2.txt`
- Disables procedural generation (`generatedUpToColumn = -1`)
- Clears walkers (platformer has different enemy placement)
- Penguins spawn on platforms

---

#### 8. handleCrash() - Death Handler

**Lines**: 702-725  
**Purpose**: Handles player death - lose a life or trigger game over

```java
public void handleCrash() {
    lives--;
    total = 0; // Reset score
    Sound sound = new Sound(basePath + "sounds/caw.wav"); 
    sound.start();
    
    if (lives <= 0) {
        gameOver = true;
        stopSlowLoop();
        stopTheme();
    } else {
        player.setPosition(200, 200);
        player.setVelocity(0, 0);
        player.show();
        fallers.clear();
        fallerTimer = 0;
        spawnWalkersBetweenPillars();
    }
}
```

**What it does**:
1. **Decrement Lives**: Reduce life counter
2. **Reset Score**: Score = time survived, reset to 0
3. **Play Sound**: Caw sound effect for crash
4. **Game Over Check**:
   - If lives <= 0: Set gameOver flag, stop audio
   - Else: Reset player position, clear enemies, respawn walkers

---

#### 9. update(long elapsed) - Main Game Loop

**Lines**: 968-1092  
**Purpose**: Called every frame to update game state

```java
public void update(long elapsed) {
    // Skip if in menu or game over
    if (inMenu || gameOver || levelComplete) return;
    
    total += elapsed; // Increase score
    
    // Apply gravity
    player.setVelocityY(player.getVelocityY() + (gravity * elapsed));
    
    player.setAnimationSpeed(1.0f);
    
    // Map-specific controls
    if (currentLevel == 1) {
        // Flappy Bird: fly upward, auto-move forward
        if (flap) {
            player.setAnimationSpeed(1.8f);
            player.setVelocityY(fly); // -0.06
        }
        player.setVelocityX(moveSpeed); // 0.06
    } else {
        // Platformer: walk left/right, jump
        float xVel = 0;
        if (moveRight) {
            xVel = walkSpeed; // 0.08
            player.setAnimationSpeed(1.5f);
        }
        if (moveLeft) {
            xVel = -walkSpeed;
            player.setAnimationSpeed(1.5f);
        }
        player.setVelocityX(xVel);
        
        // Jump only when on ground
        if (flap && onGround) {
            player.setVelocityY(jumpPower); // -0.19
            player.setAnimationSpeed(1.8f);
            onGround = false;
        }
    }
    
    // Update clouds (wrap around screen)
    for (Sprite s : clouds) {
        s.update(elapsed);
        if (s.getX() + s.getWidth() < 0) {
            s.setX(screenWidth + (int)(Math.random() * 200));
            s.setY((int)(Math.random() * (screenHeight / 2)));
            s.setVelocityX(-(0.008f + (float)(Math.random() * 0.06f)));
        }
    }
    
    // Update walkers (patrol between bounds)
    for (int i = 0; i < walkers.size(); i++) {
        Sprite w = walkers.get(i);
        float leftBound = walkerLeftBounds.get(i);
        float rightBound = walkerRightBounds.get(i);
        
        w.update(elapsed);
        
        // Bounce at boundaries
        float walkerSpeed = 0.01f;
        if (w.getX() <= leftBound) {
            w.setX(leftBound);
            w.setVelocityX(walkerSpeed);
        } else if (w.getX() + w.getWidth() >= rightBound) {
            w.setX(rightBound - w.getWidth());
            w.setVelocityX(-walkerSpeed);
        }
        
        // Check collision with player
        if (boundingBoxCollision(player, w)) {
            handleCrash();
            return;
        }
    }
    
    // Spawn falling penguins (Map 2 only)
    if (currentLevel == 2) {
        fallerTimer += elapsed;
        if (fallerTimer >= fallerIntervalMap2) { // 3000ms
            fallerTimer = 0;
            spawnFallingPenguinMap2();
        }
    }
    
    // Update falling penguins
    for (int i = fallers.size() - 1; i >= 0; i--) {
        Sprite f = fallers.get(i);
        
        // Apply gravity in map2
        if (currentLevel == 2 && f.getVelocityY() >= 0) {
            f.setVelocityY(f.getVelocityY() + (gravity * elapsed * 0.5f));
        }
        
        // Check if penguin landed on platform (map2)
        if (currentLevel == 2 && f.getVelocityY() > 0) {
            int tw = tmap.getTileWidth();
            int th = tmap.getTileHeight();
            int penguinCol = (int)((f.getX() + f.getWidth()/2) / tw);
            int penguinBottomRow = (int)((f.getY() + f.getHeight()) / th);
            
            if (penguinBottomRow < tmap.getMapHeight()) {
                char tileBelow = tmap.getTileChar(penguinCol, penguinBottomRow);
                if (tileBelow != '.') {
                    // Landed - convert to patrolling
                    f.setVelocityY(0);
                    f.setVelocityX(0.015f);
                    int platformTopY = penguinBottomRow * th - (int)f.getHeight();
                    f.setY(platformTopY);
                }
            }
        }
        
        // Patrol edge detection (for landed penguins)
        if (Math.abs(f.getVelocityY()) < 0.001f && Math.abs(f.getVelocityX()) > 0) {
            // ... edge detection logic ...
            // Reverse direction at platform edges
        }
        
        f.update(elapsed);
        
        // Remove if fell off bottom
        if (f.getY() > tmap.getPixelHeight()) {
            fallers.remove(i);
            continue;
        }
        
        // Check collision with player
        if (boundingBoxCollision(player, f)) {
            float playerBottom = player.getY() + player.getHeight();
            float penguinMiddle = f.getY() + f.getHeight() / 2;
            
            // Stomp detection
            if (player.getVelocityY() > 0 && playerBottom < penguinMiddle) {
                fallers.remove(i);
                player.setVelocityY(-0.1f); // Bounce
                continue;
            } else {
                handleCrash();
                return;
            }
        }
    }
    
    // Save old position
    float oldX = player.getX();
    float oldY = player.getY();
    
    // Update player position
    player.update(elapsed);
    
    // Procedural generation for map1
    if (currentLevel == 1) {
        generatePipesAhead();
        cleanUpBehindPlayer();
    }
    
    // Check level completion (map2)
    if (currentLevel == 2 && player.getX() >= tmap.getPixelWidth() - 100) {
        levelComplete = true;
        stopSlowLoop();
        stopTheme();
        return;
    }
    
    // Collision detection
    handleScreenEdge(player, tmap, elapsed);
    checkTileCollision(player, tmap, oldX, oldY);
}
```

**Update Order**:
1. Score increment (time-based)
2. Gravity application
3. Input-based movement (different per map)
4. Cloud updates (background parallax effect)
5. Walker updates (patrol behavior)
6. Penguin spawning timer
7. Penguin updates (falling, landing, patrolling)
8. Player position update
9. Procedural generation (map 1 only)
10. Collision detection

**Physics Constants**:
- `gravity = 0.0002f`: Downward acceleration per millisecond²
- `fly = -0.06f`: Flap impulse (instant upward velocity)
- `moveSpeed = 0.06f`: Forward speed (map 1)
- `jumpPower = -0.19f`: Jump impulse (map 2)
- `walkSpeed = 0.08f`: Walking speed (map 2)

---

#### 10. draw(Graphics2D g) - Rendering

**Lines**: 733-865  
**Purpose**: Draws the current game state to the screen

```java
public void draw(Graphics2D g) {
    // Draw menu
    if (inMenu) {
        drawMenu(g);
        return;
    }
    
    // Calculate camera offset (keep player centered)
    int xo = -(int)player.getX() + 200;
    int yo = -(int)player.getY() + 200;
    
    // Sky background
    g.setColor(new Color(207, 236, 247));
    g.fillRect(0, 0, getWidth(), getHeight());
    
    // Draw clouds (no offset - screen space)
    for (Sprite s : clouds) {
        s.setOffsets(0, 0);
        s.draw(g);
    }
    
    // Draw walkers (world space)
    for (Sprite w : walkers) {
        w.setOffsets(xo, yo);
        w.draw(g);
    }
    
    // Draw falling penguins (world space)
    for (Sprite f : fallers) {
        f.setOffsets(xo, yo);
        f.draw(g);
    }
    
    // Draw tile map (world space)
    drawTileMap(g, xo, yo);
    
    // Draw player (world space)
    player.setOffsets(xo, yo);
    player.draw(g);
    
    // UI: Score and lives
    String msg = String.format("Score: %d", total/100);
    g.setColor(Color.darkGray);
    g.drawString(msg, getWidth() - 150, 50);
    
    String livesMsg = String.format("Lives: %d", lives);
    g.setColor(Color.red);
    g.drawString(livesMsg, getWidth() - 150, 70);
    
    // Level complete overlay
    if (levelComplete) {
        g.setColor(new Color(0, 255, 0, 150));
        g.fillRect(0, 0, getWidth(), getHeight());
        // ... text rendering ...
    }
    
    // Game over overlay
    if (gameOver) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, 0, getWidth(), getHeight());
        // ... text rendering ...
    }
    
    // Debug mode
    if (debug) {
        tmap.drawBorder(g, xo, yo, Color.black);
        g.setColor(Color.red);
        player.drawBoundingBox(g);
        g.drawString(String.format("Player: %.0f,%.0f", 
                                   player.getX(), player.getY()),
                     getWidth() - 100, 90);
        drawCollidedTiles(g, tmap, xo, yo);
    }
    
    // Draw border lines
    g.setColor(Color.darkGray);
    g.fillRect(0, 0, screenWidth, 3); // Top
    int bottomLineY = yo + tmap.getPixelHeight();
    g.fillRect(0, bottomLineY - 3, screenWidth, 3); // Bottom
}
```

**Rendering Layers** (back to front):
1. **Background**: Light blue fill
2. **Clouds**: Draw in screen space (offset = 0, 0) for parallax effect
3. **Walkers**: World space (offset = camera position)
4. **Falling Penguins**: World space
5. **Tile Map**: World space
6. **Player**: World space (appears centered due to offset)
7. **UI Elements**: Screen space (score, lives)
8. **Overlays**: Semi-transparent (game over, level complete)
9. **Debug Info**: Bounding boxes, coordinates

**Camera System**:
```java
int xo = -(int)player.getX() + 200;
int yo = -(int)player.getY() + 200;
```
- Player stays at screen position (200, 200)
- World scrolls around player
- Offset calculation: `screenX = worldX + xo`

---

#### 11. checkTileCollision() - Collision Detection & Resolution

**Lines**: 1251-1350  
**Purpose**: Detects and resolves collisions between sprites and the tile map

```java
public void checkTileCollision(Sprite s, TileMap tmap, float oldX, float oldY) {
    collidedTiles.clear();
    
    float sx = s.getX();
    float sy = s.getY();
    float sw = s.getWidth();
    float sh = s.getHeight();
    
    float tileWidth = tmap.getTileWidth();
    float tileHeight = tmap.getTileHeight();
    
    // ===== CHECK X AXIS COLLISION =====
    s.setY(oldY); // Temporarily revert Y
    
    int leftTile = (int)Math.floor(sx / tileWidth);
    int rightTile = (int)Math.floor((sx + sw - 1) / tileWidth);
    int topTile = (int)Math.floor(oldY / tileHeight);
    int bottomTile = (int)Math.floor((oldY + sh - 1) / tileHeight);
    
    boolean collisionX = false;
    for (int y = topTile; y <= bottomTile; y++) {
        for (int x = leftTile; x <= rightTile; x++) {
            Tile t = tmap.getTile(x, y);
            if (t != null && t.getCharacter() != '.') {
                collidedTiles.add(t);
                collisionX = true;
            }
        }
    }
    
    if (collisionX) {
        if (s == player && currentLevel == 1) {
            // Map1: hitting sides = game over
            handleCrash();
            return;
        } else if (s == player && currentLevel == 2) {
            // Map2: stop at walls
            if (s.getVelocityX() > 0) { // Moving right
                s.setX(rightTile * tileWidth - sw);
            } else if (s.getVelocityX() < 0) { // Moving left
                s.setX((leftTile + 1) * tileWidth);
            }
            s.setVelocityX(0);
        }
    }
    
    // ===== CHECK Y AXIS COLLISION =====
    sx = s.getX(); // Update in case X was modified
    s.setY(sy); // Restore Y
    
    leftTile = (int)Math.floor(sx / tileWidth);
    rightTile = (int)Math.floor((sx + sw - 1) / tileWidth);
    topTile = (int)Math.floor(sy / tileHeight);
    bottomTile = (int)Math.floor((sy + sh - 1) / tileHeight);
    
    boolean collisionY = false;
    for (int y = topTile; y <= bottomTile; y++) {
        for (int x = leftTile; x <= rightTile; x++) {
            Tile t = tmap.getTile(x, y);
            if (t != null && t.getCharacter() != '.') {
                collidedTiles.add(t);
                collisionY = true;
            }
        }
    }
    
    if (collisionY) {
        if (s == player && currentLevel == 1) {
            // Map1: any Y collision = fatal
            handleCrash();
            return;
        }
        
        // Map2: platform collision - stop movement
        if (s.getVelocityY() > 0) { // Falling
            s.setY(bottomTile * tileHeight - sh);
            if (s == player && currentLevel == 2) {
                onGround = true; // Enable jumping
            }
        } else if (s.getVelocityY() < 0) { // Jumping up
            s.setY((topTile + 1) * tileHeight);
        }
        s.setVelocityY(0);
    } else if (s == player && currentLevel == 2) {
        onGround = false; // In air
    }
}
```

**Algorithm**:

**Why separate X and Y axis checks?**
- Prevents corner-sticking bugs
- Allows sliding along walls while falling
- More natural platformer feel

**Step 1: X-Axis Collision**
1. Temporarily revert sprite Y position to old value
2. Calculate which tiles the sprite overlaps horizontally
3. Check each tile for solid collision
4. If collision:
   - Map 1: Game over
   - Map 2: Push sprite out of wall, stop horizontal movement

**Step 2: Y-Axis Collision**
1. Update sprite X (may have changed in step 1)
2. Restore sprite Y position to new value
3. Calculate which tiles overlap vertically
4. Check tiles
5. If collision:
   - Map 1: Game over
   - Map 2: If falling → land on platform, set onGround = true
   - Map 2: If jumping → hit ceiling, stop upward movement

**Tile Overlap Calculation**:
```java
int leftTile = (int)Math.floor(sx / tileWidth);
int rightTile = (int)Math.floor((sx + sw - 1) / tileWidth);
```
- Left edge of sprite → leftmost tile
- Right edge of sprite (sx + sw - 1) → rightmost tile
- Same for top/bottom

---

#### 12. boundingBoxCollision() - Sprite vs Sprite Collision

**Lines**: 1232-1238  
**Purpose**: AABB (Axis-Aligned Bounding Box) collision detection

```java
public boolean boundingBoxCollision(Sprite s1, Sprite s2) {
    return ((s1.getX() + s1.getWidth()) > s2.getX() &&
            (s1.getX() < (s2.getX() + s2.getWidth())) &&
            (s1.getY() + s1.getHeight()) > s2.getY() &&
            (s1.getY() < (s2.getY() + s2.getHeight())));
}
```

**Algorithm**: AABB Collision Test
```
Collision occurs if ALL of these are true:
1. s1's right edge > s2's left edge
2. s1's left edge < s2's right edge
3. s1's bottom edge > s2's top edge
4. s1's top edge < s2's bottom edge
```

**Visual**:
```
    s1          s2
┌──────┐    ┌──────┐
│      │    │      │
│  ●───┼────┼───●  │  ← Overlapping region = collision
│      │    │      │
└──────┘    └──────┘
```

---

#### 13. keyPressed() - Input Handling

**Lines**: 1203-1218  
**Purpose**: Handles keyboard input events

```java
public void keyPressed(KeyEvent e) { 
    int key = e.getKeyCode();
    
    switch (key) {
        case KeyEvent.VK_1:
            if (inMenu) { currentLevel = 1; inMenu = false; initialiseGame(); }
            break;
        case KeyEvent.VK_2:
            if (inMenu) { currentLevel = 2; inMenu = false; initialiseGame(); }
            break;
        case KeyEvent.VK_M:
            if (gameOver || levelComplete) {
                inMenu = true;
                gameOver = false;
                levelComplete = false;
                stopSlowLoop();
                stopTheme();
            }
            break;
        case KeyEvent.VK_UP:
        case KeyEvent.VK_W:
            if (!inMenu) flap = true;
            break;
        case KeyEvent.VK_LEFT:
        case KeyEvent.VK_A:
            if (!inMenu) moveLeft = true;
            break;
        case KeyEvent.VK_RIGHT:
        case KeyEvent.VK_D:
            if (!inMenu) moveRight = true;
            break;
        case KeyEvent.VK_SPACE:
            if (gameOver) {
                initialiseGame();
            } else if (!inMenu) {
                flap = true;
            }
            break;
        case KeyEvent.VK_S:
            if (!inMenu && currentLevel == 1) {
                Sound s = new Sound(basePath + "sounds/caw.wav");
                s.start();
            }
            break;
        case KeyEvent.VK_ESCAPE:
            stop();
            break;
        case KeyEvent.VK_B:
            debug = !debug; // Toggle debug mode
            break;
    }
}
```

**Input Mapping**:
| Key | Action | Context |
|-----|--------|---------|
| 1 | Start Map 1 | Menu only |
| 2 | Start Map 2 | Menu only |
| M | Return to menu | Game over or level complete |
| UP/W | Flap/Jump | In game |
| LEFT/A | Move left | In game (map 2) |
| RIGHT/D | Move right | In game (map 2) |
| SPACE | Flap (game) / Restart (game over) | Context-sensitive |
| S | Play sound effect | Map 1 only |
| ESC | Quit game | Any time |
| B | Toggle debug mode | Any time |

**keyReleased()** (Lines 1353-1370):
```java
public void keyReleased(KeyEvent e) { 
    int key = e.getKeyCode();
    switch (key) {
        case KeyEvent.VK_UP:
        case KeyEvent.VK_W:
            flap = false;
            break;
        case KeyEvent.VK_LEFT:
        case KeyEvent.VK_A:
            moveLeft = false;
            break;
        case KeyEvent.VK_RIGHT:
        case KeyEvent.VK_D:
            moveRight = false;
            break;
        case KeyEvent.VK_SPACE:
            flap = false;
            break;
    }
}
```
- Clears movement flags when keys released
- Allows smooth continuous movement

---

#### 14. Audio Management Functions

**loadClip()** - Load WAV File (Lines 520-533)
```java
public Clip loadClip(String filename) {
    try {
        File file = new File(filename);
        AudioInputStream stream = AudioSystem.getAudioInputStream(file);
        AudioFormat format = stream.getFormat();
        DataLine.Info info = new DataLine.Info(Clip.class, format);
        Clip clip = (Clip)AudioSystem.getLine(info);
        clip.open(stream);
        return clip;
    } catch (Exception e) {
        System.err.println("Failed to load clip: " + filename);
        return null;
    }
}
```
- Loads WAV files into memory
- Returns Clip object for playback control
- Used for sound effects (wing flap, crash sound)

**loadMidi()** - Load MIDI File (Lines 538-552)
```java
public Sequencer loadMidi(String filename) {
    try {
        Sequencer sequencer = MidiSystem.getSequencer();
        sequencer.open();
        File midiFile = new File(filename);
        Sequence sequence = MidiSystem.getSequence(midiFile);
        sequencer.setSequence(sequence);
        return sequencer;
    } catch (Exception e) {
        System.err.println("Failed to load MIDI: " + filename);
        return null;
    }
}
```
- Loads MIDI files for background music
- Returns Sequencer for playback control

**startTheme() / stopTheme()** - Background Music Control
```java
public void startTheme() {
    if (midiSequencer != null) {
        try {
            midiSequencer.setTickPosition(0);
            midiSequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
            midiSequencer.start();
        } catch (Exception e) {
            System.err.println("Failed to start MIDI: " + e);
        }
    }
}

public void stopTheme() {
    if (midiSequencer != null) {
        midiSequencer.stop();
    }
}
```

---

## GameCore.java - Engine Foundation

**Location**: `handout/src/game2D/GameCore.java`  
**Lines**: 203 lines  
**Purpose**: Abstract base class providing game loop, rendering, and input handling

### Class Structure

```java
public abstract class GameCore extends JFrame implements KeyListener {
    private boolean stop;
    private long startTime;
    private long currTime;
    private long elapsedTime;
    private long frames;
    
    private BufferedImage buffer = null;  // Offscreen buffer
    private Graphics2D bg = null;         // Graphics context for buffer
    
    protected int virtualWidth = 512;
    protected int virtualHeight = 384;
}
```

### Function Breakdown

#### 1. run() - Game Initialization

**Lines**: 68-77  
**Purpose**: Starts the game by initializing and entering the game loop

```java
public void run(boolean full, int x, int y) {
    try {
        init(full, x, y);
        gameLoop();
    } finally { 
        // Cleanup
    }
}
```

**Parameters**:
- `full`: true for fullscreen, false for windowed
- `x`: Screen width in pixels
- `y`: Screen height in pixels

---

#### 2. gameLoop() - Main Game Loop

**Lines**: 120-161  
**Purpose**: The heart of the engine - continuous update/draw cycle

```java
public void gameLoop() {
    startTime = System.currentTimeMillis();
    currTime = startTime;
    frames = 1;
    
    Graphics2D g;
    stop = false;
    
    // Create offscreen buffer at virtual resolution
    buffer = new BufferedImage(virtualWidth, virtualHeight, 
                               BufferedImage.TYPE_INT_RGB);
    bg = (Graphics2D)buffer.createGraphics();
    bg.setClip(0, 0, virtualWidth, virtualHeight);
    
    while (!stop) {
        elapsedTime = System.currentTimeMillis() - currTime;
        currTime += elapsedTime;
        
        // Call overridden update method
        update(elapsedTime);
        
        // Get graphics device
        g = (Graphics2D)getGraphics();
        
        if (g != null) {
            // Draw to offscreen buffer
            draw(bg);
            
            // Scale buffer to window size
            Insets insets = getInsets();
            int w = getWidth() - insets.left - insets.right;
            int h = getHeight() - insets.top - insets.bottom;
            
            g.drawImage(buffer, insets.left, insets.top, 
                        w + insets.left, h + insets.top,
                        0, 0, virtualWidth, virtualHeight, null);
        }
        
        frames++;
        
        // Sleep to control frame rate
        try { Thread.sleep(10); } catch (InterruptedException ex) { }
    }
    System.exit(0);
}
```

**Game Loop Structure**:
```
┌─────────────────────────────────┐
│  While (!stop)                  │
│                                 │
│  1. Calculate elapsed time      │
│  2. update(elapsedTime)        │
│  3. draw(bg) to buffer         │
│  4. Scale buffer to screen     │
│  5. Sleep 10ms                 │
│                                 │
└─────────────────────────────────┘
```

**Double Buffering**:
1. Create offscreen `BufferedImage` at virtual resolution (640x480)
2. Draw everything to offscreen buffer
3. Scale buffer to actual window size in one operation
4. Display result

**Benefits**:
- No flickering (all drawing happens offscreen)
- Resolution independence (virtual 640x480 scales to any window size)
- Smooth animation

---

#### 3. getFPS() - Performance Measurement

**Lines**: 168-172  
**Purpose**: Calculates current frames per second

```java
public float getFPS() {
    if (currTime - startTime <= 0) return 0.0f;
    return (float)frames / ((currTime - startTime) / 1000.0f);
}
```

**Calculation**:
```
FPS = Total Frames / Total Seconds
    = frames / ((currTime - startTime) / 1000)
```

---

#### 4. loadImage() - Image Loading

**Lines**: 103-106  
**Purpose**: Loads an image from file

```java
public Image loadImage(String fileName) { 
    return new ImageIcon(fileName).getImage(); 
}
```

Uses `ImageIcon` from Swing to load PNG, JPG, GIF files.

---

#### 5. Abstract Methods

**update(long elapsedTime)** - Must be overridden by subclass
```java
public void update(long elapsedTime) { /* do nothing */ }
```

**draw(Graphics2D g)** - Must be overridden by subclass
```java
public abstract void draw(Graphics2D g);
```

---

## Sprite.java - Entity System

**Location**: `handout/src/game2D/Sprite.java`  
**Lines**: 457 lines  
**Purpose**: Represents any game entity with position, velocity, and animation

### Class Structure

```java
public class Sprite {
    private Animation anim;           // Current animation
    
    // Position (pixels)
    private float x;
    private float y;
    
    // Velocity (pixels per millisecond)
    private float dx;
    private float dy;
    
    // Dimensions
    private float height;
    private float width;
    private float radius;
    
    // Transform
    private double xscale;
    private double yscale;
    private double rotation;
    
    // Rendering
    private boolean render;
    private int xoff = 0;  // Draw offset
    private int yoff = 0;
}
```

### Function Breakdown

#### 1. Constructor

**Lines**: 49-57  
```java
public Sprite(Animation anim) {
    this.anim = anim;
    render = true;
    xscale = 1.0f;
    yscale = 1.0f;
    rotation = 0.0f;
}
```

Creates sprite with given animation, default scale, no rotation.

---

#### 2. update() - Position Update

**Lines**: 145-158  
**Purpose**: Updates position and animation based on elapsed time

```java
public void update(long elapsedTime) {
    if (!render) return;
    
    // Update position based on velocity
    x += dx * elapsedTime;
    y += dy * elapsedTime;
    
    // Update animation frame
    anim.update(elapsedTime);
    
    // Recalculate dimensions
    width = getWidth();
    height = getHeight();
    
    if (width > height)
        radius = width / 2.0f;
    else
        radius = height / 2.0f;
}
```

**Physics**: 
```
new_x = old_x + (velocity_x * elapsed_time)
new_y = old_y + (velocity_y * elapsed_time)
```

Example:
- velocity_x = 0.06 pixels/millisecond
- elapsed_time = 16 milliseconds (60 FPS)
- movement = 0.06 * 16 = 0.96 pixels per frame

---

#### 3. setPosition() / setVelocity()

**Lines**: 189-209  
```java
public void setPosition(float x, float y) {
    setX(x);
    setY(y);
}

public void setVelocity(float dx, float dy) {
    this.dx = dx;
    this.dy = dy;
}
```

Getters and setters for position and velocity.

---

#### 4. setScale() - Scaling

**Lines**: 301-322  
```java
public void setScale(float scx, float scy) {
    xscale = scx;
    yscale = scy;
}

public void setScale(float s) {
    xscale = s;
    yscale = s;
}
```

**Scaling Examples**:
- `setScale(1.0f)` = normal size
- `setScale(0.5f)` = half size
- `setScale(2.0f)` = double size
- `setScale(-1.0f, 1.0f)` = horizontally flipped

---

#### 5. draw() - Rendering

**Lines**: 386-392  
**Purpose**: Draws sprite to screen at current position plus offset

```java
public void draw(Graphics2D g) {
    if (!render) return;
    g.drawImage(getImage(), (int)x + xoff, (int)y + yoff, null);
}
```

**Offset System**:
- `xoff`, `yoff` set via `setOffsets()`
- Allows camera scrolling without changing actual position
- World position = (x, y)
- Screen position = (x + xoff, y + yoff)

---

#### 6. drawTransformed() - Advanced Rendering

**Lines**: 422-441  
**Purpose**: Draws sprite with rotation and scaling applied

```java
public void drawTransformed(Graphics2D g) {
    if (!render) return;
    
    AffineTransform transform = new AffineTransform();
    
    // Handle flipping
    float shiftx = 0;
    float shifty = 0;
    if (xscale < 0) shiftx = getWidth();
    if (yscale < 0) shifty = getHeight();
    
    // Apply transformations
    transform.translate(Math.round(x) + shiftx + xoff, 
                        Math.round(y) + shifty + yoff);
    transform.scale(xscale, yscale);
    transform.rotate(rotation, 
                     getImage().getWidth(null) / 2, 
                     getImage().getHeight(null) / 2);
    
    g.drawImage(getImage(), transform, null);
}
```

**Transformation Order**:
1. Translate to position
2. Scale
3. Rotate around center

---

## TileMap.java - Level Management

**Location**: `handout/src/game2D/TileMap.java`  
**Lines**: 401 lines  
**Purpose**: Manages grid-based tile maps loaded from text files

### Class Structure

```java
public class TileMap {
    private Tile[][] tmap;        // 2D array of tiles
    private int mapWidth = 0;     // Width in tiles
    private int mapHeight = 0;    // Height in tiles
    private int tileWidth = 0;    // Tile width in pixels
    private int tileHeight = 0;   // Tile height in pixels
    
    // Character to image mapping
    private Map<String, Image> imagemap = new HashMap<String, Image>();
}
```

### File Format

Example `map.txt`:
```
10 5 32 32
// First line: width height tileWidth tileHeight
#b=orangeblock.png
#c=greencircle.png
#g=glasses.png
#map
bbbbbbbbbb
b........b
b..g.....b
bccccccccb
bbbbbbbbbb
```

### Function Breakdown

#### 1. loadMap() - Load Map from File

**Lines**: 102-224  
**Purpose**: Loads a tile map from a text file

```java
public boolean loadMap(String folder, String mapfile) {
    String path = folder + "/" + mapfile;
    int row = 0;
    
    try {
        BufferedReader in = new BufferedReader(new FileReader(path));
        String line = "";
        String[] vals;
        
        imagemap.clear();
        
        // Read header (dimensions)
        line = in.readLine();
        vals = line.split(" ");
        if (vals.length != 4) {
            System.err.println("Incorrect number of parameters");
            in.close();
            return false;
        }
        
        mapWidth = Integer.parseInt(vals[0]);
        mapHeight = Integer.parseInt(vals[1]);
        tileWidth = Integer.parseInt(vals[2]);
        tileHeight = Integer.parseInt(vals[3]);
        
        // Read character-to-image mappings
        while ((line = in.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) continue;    // Skip comments
            if (trimmed.startsWith("#map")) break;     // Found map data
            
            if (trimmed.charAt(0) == '#') {
                String ch = "" + trimmed.charAt(1);
                String fileName = trimmed.substring(3, trimmed.length());
                Image img = new ImageIcon(folder + "/" + fileName).getImage();
                
                if (img != null)
                    imagemap.put(ch, img);
                else
                    System.err.println("Failed to load image '" + fileName + "'");
            }
        }
        
        // Allocate tile array
        if ((mapWidth > 0) && (mapHeight > 0)) {
            tmap = new Tile[mapWidth][mapHeight];
        } else {
            System.err.println("Incorrect image map dimensions.");
            return false;
        }
        
        // Read map data
        if (trimmed.startsWith("#map")) {
            row = 0;
            while ((line = in.readLine()) != null) {
                if (line.trim().startsWith("//")) continue;
                
                if (line.length() != mapWidth) {
                    System.err.println("Incorrect line length in map");
                    continue;
                }
                
                for (int col = 0; col < mapWidth && col < line.length(); col++)
                    tmap[col][row] = new Tile(line.charAt(col), 
                                               col * tileWidth, 
                                               row * tileHeight);
                row++;
                
                if (row >= mapHeight) break;
            }
        }
        
        in.close();
        
    } catch (Exception e) {
        System.err.println("Failed to read tile map '" + path + "':" + e);
        return false;
    }
    
    if (row != mapHeight) {
        System.err.println("Map failed to load. Incorrect rows in map");
        return false;
    }
    
    return true;
}
```

**Loading Steps**:
1. Read header line (width, height, tile size)
2. Read character mappings (#b=file.png)
3. Load images into HashMap
4. Allocate 2D tile array
5. Read map data line by line
6. Create Tile object for each character

---

#### 2. getTile() / getTileChar() - Tile Access

**Lines**: 338-354  
```java
public char getTileChar(int x, int y) {
    if (!valid(x, y)) return '?';
    return tmap[x][y].getCharacter();
}

public Tile getTile(int x, int y) {
    if (!valid(x, y)) return null;
    return tmap[x][y];
}

public boolean valid(int x, int y) {
    return (x >= 0 && y >= 0 && x < mapWidth && y < mapHeight);
}
```

---

#### 3. setTileChar() - Modify Tiles

**Lines**: 325-334  
**Purpose**: Changes a tile's character (used for procedural generation)

```java
public boolean setTileChar(char ch, int x, int y) {
    if (!valid(x, y)) return false;
    tmap[x][y].setCharacter(ch);
    return true;
}
```

Used by `generatePipeColumn()` to write new pipes into the map.

---

#### 4. expandWidth() - Dynamic Map Expansion

**Lines**: 357-376  
**Purpose**: Expands the tile map width for infinite generation

```java
public void expandWidth(int additionalColumns) {
    int newWidth = mapWidth + additionalColumns;
    Tile[][] newMap = new Tile[newWidth][mapHeight];
    
    // Copy existing tiles
    for (int x = 0; x < mapWidth; x++)
        for (int y = 0; y < mapHeight; y++)
            newMap[x][y] = tmap[x][y];
    
    // Fill new columns with empty tiles
    for (int x = mapWidth; x < newWidth; x++)
        for (int y = 0; y < mapHeight; y++)
            newMap[x][y] = new Tile('.', x * tileWidth, y * tileHeight);
    
    tmap = newMap;
    mapWidth = newWidth;
}
```

**Algorithm**:
1. Allocate new larger array
2. Copy old tiles
3. Fill new columns with '.' (empty)
4. Replace old array

---

#### 5. draw() - Render Tile Map

**Lines**: 384-406  
**Purpose**: Draws visible portion of tile map

```java
public void draw(Graphics2D g, int xoff, int yoff) {
    if (g == null) return;
    
    Image img = null;
    Rectangle rect = (Rectangle)g.getClip();
    int xc, yc;
    
    for (int r = 0; r < mapHeight; r++) {
        for (int c = 0; c < mapWidth; c++) {
            img = getTileImage(c, r);
            if (img == null) continue;
            
            xc = xoff + c * tileWidth;
            yc = yoff + r * tileHeight;
            
            // Only draw if on screen
            if (xc + tileWidth < 0 || xc >= rect.x + rect.width) continue;
            if (yc + tileHeight < 0 || yc >= rect.y + rect.height) continue;
            
            g.drawImage(img, xc, yc, null);
        }
    }
}
```

**Optimization**: Only draws tiles within clipping rectangle (visible area).

---

## Animation.java - Animation System

**Location**: `handout/src/game2D/Animation.java`  
**Lines**: 395 lines  
**Purpose**: Manages frame-based sprite animations

### Class Structure

```java
public class Animation {
    private ArrayList<AnimFrame> frames;  // Animation frames
    private int currFrameIndex;           // Current frame
    private long animTime;                // Current time in animation
    private long totalDuration;           // Total animation duration
    private float animSpeed = 1.0f;       // Playback speed multiplier
    
    private boolean loop = true;          // Loop animation?
    private boolean looped = false;       // Has looped once?
    private boolean play = true;          // Is playing?
    private int stopFrame = -1;           // Frame to stop at
    
    private class AnimFrame {
        Image image;
        long endTime;
    }
}
```

### Function Breakdown

#### 1. addFrame() - Add Animation Frame

**Lines**: 54-59  
```java
public synchronized void addFrame(Image image, long duration) {
    totalDuration += duration;
    frames.add(new AnimFrame(image, totalDuration));
}
```

**Example**:
```java
Animation walk = new Animation();
walk.addFrame(img1, 100);  // Frame 0: 0-100ms
walk.addFrame(img2, 100);  // Frame 1: 100-200ms
walk.addFrame(img3, 100);  // Frame 2: 200-300ms
// totalDuration = 300ms
```

---

#### 2. update() - Advance Animation

**Lines**: 78-118  
**Purpose**: Updates animation based on elapsed time

```java
public synchronized void update(long elapsedTime) {
    if (!play) return;  // Paused
    
    elapsedTime = (long)(elapsedTime * animSpeed);
    
    if (frames.size() > 1) {
        animTime += elapsedTime;
        
        if (animTime >= totalDuration) {
            if (loop) {
                animTime = animTime % totalDuration;
                currFrameIndex = 0;
            } else {
                animTime = totalDuration;
            }
            looped = true;
        }
        
        while (animTime > getFrame(currFrameIndex).endTime) {
            currFrameIndex++;
        }
        
        // Check for stop frame
        if (currFrameIndex == stopFrame) {
            play = false;
            stopFrame = -1;
        }
    }
}
```

**Animation Timing**:
```
Frame 0: 0-100ms
Frame 1: 100-200ms
Frame 2: 200-300ms

If animTime = 150ms:
  → currFrameIndex = 1 (frame 1)
  
If animTime = 320ms and loop=true:
  → animTime = 320 % 300 = 20ms
  → currFrameIndex = 0
```

---

#### 3. loadAnimationFromSheet() - Load All Frames

**Lines**: 183-192  
**Purpose**: Loads entire sprite sheet as animation

```java
public void loadAnimationFromSheet(String fileName, int columns, int rows, 
                                    int frameDuration) {
    Image sheet = new ImageIcon(fileName).getImage();
    Image[] images = getImagesFromSheet(sheet, columns, rows);
    
    for (int i = 0; i < images.length; i++) {
        addFrame(new ImageIcon(images[i]).getImage(), frameDuration);
    }
}
```

**Example**:
```java
// Load landbird.png: 4 columns × 1 row, 60ms per frame
animation.loadAnimationFromSheet("images/landbird.png", 4, 1, 60);
```

Result: 4 frames, each displayed for 60ms.

---

#### 4. loadAnimationSeries() - Load Subset of Frames

**Lines**: 268-277  
**Purpose**: Loads specific frames from sprite sheet

```java
public void loadAnimationSeries(String fileName, int columns, int rows, 
                                 long frameDuration, int from, int numFrames) {
    ArrayList<Integer> frameIndex = new ArrayList<>();
    for (int i = from; i < from + numFrames; i++) 
        frameIndex.add(i);
        
    ArrayList<BufferedImage> imagesList = 
        loadSubsetFromSpriteSheet(fileName, columns, rows, frameIndex);
    
    for (BufferedImage img : imagesList) 
        addFrame(img, frameDuration);
}
```

**Example**:
```java
// Load walk.png: 3×4 grid, starting at frame 6, load 5 frames
animation.loadAnimationSeries("images/walk.png", 3, 4, 100, 6, 5);
```

Sprite sheet layout (3 columns × 4 rows = 12 frames):
```
[0] [1] [2]
[3] [4] [5]
[6] [7] [8]   ← Load frames 6-10
[9][10][11]
```

---

#### 5. getImagesFromSheet() - Split Sprite Sheet

**Lines**: 207-256  
**Purpose**: Splits sprite sheet into individual frame images

```java
private Image[] getImagesFromSheet(Image sheet, int columns, int rows) {
    Image[] split = new Image[rows * columns];
    int count = 0;
    
    int width = sheet.getWidth(null) / columns;
    int height = sheet.getHeight(null) / rows;
    
    for (int i = 0; i < rows; i++) {
        for (int j = 0; j < columns; j++) {
            // Create crop filter
            ImageFilter cropper = new CropImageFilter(j * width, i * height, 
                                                      width, height);
            
            // Apply filter to create cropped image
            FilteredImageSource cropped = 
                new FilteredImageSource(sheet.getSource(), cropper);
            
            split[count] = Toolkit.getDefaultToolkit().createImage(cropped);
            count++;
        }
    }
    
    return split;
}
```

**Algorithm**:
1. Calculate frame dimensions (sheet width/columns, height/rows)
2. For each row and column:
   - Create CropImageFilter for that region
   - Apply filter to extract sub-image
   - Store in array

---

#### 6. setAnimationSpeed() - Control Playback Speed

**Lines**: 367-371  
```java
public void setAnimationSpeed(float rate) {
    animSpeed = rate;
}
```

**Speed Examples**:
- `1.0f` = normal speed
- `2.0f` = 2x faster
- `0.5f` = half speed
- `1.8f` = 80% faster (used when flapping)

---

## Tile.java - Tile Data Structure

**Location**: `handout/src/game2D/Tile.java`  
**Lines**: 56 lines  
**Purpose**: Simple data class representing a single tile

### Class Structure

```java
public class Tile {
    private char character = ' ';  // Character from map file
    private int xc = 0;            // X coordinate in pixels
    private int yc = 0;            // Y coordinate in pixels
}
```

### Functions

```java
public Tile(char c, int x, int y) {
    character = c;
    xc = x;
    yc = y;
}

public char getCharacter() { return character; }
public void setCharacter(char character) { this.character = character; }
public int getXC() { return xc; }
public int getYC() { return yc; }
```

**Properties**:
- `character`: Tile type ('p' = pipe, '.' = empty, 'b' = bottom cap, etc.)
- `xc, yc`: Top-left pixel position of tile in world space

---

## Sound.java - Audio System

**Location**: `handout/src/game2D/Sound.java`  
**Lines**: 41 lines  
**Purpose**: Plays WAV sound effects in separate thread

### Class Structure

```java
public class Sound extends Thread {
    String filename;
    boolean finished;
}
```

### Function Breakdown

#### run() - Play Sound

**Lines**: 18-35  
**Purpose**: Plays WAV file (called automatically by start())

```java
public void run() {
    try {
        File file = new File(filename);
        AudioInputStream stream = AudioSystem.getAudioInputStream(file);
        AudioFormat format = stream.getFormat();
        DataLine.Info info = new DataLine.Info(Clip.class, format);
        Clip clip = (Clip)AudioSystem.getLine(info);
        clip.open(stream);
        clip.start();
        Thread.sleep(100);
        while (clip.isRunning()) { 
            Thread.sleep(100); 
        }
        clip.close();
    } catch (Exception e) { }
    finished = true;
}
```

**Usage**:
```java
Sound s = new Sound("sounds/caw.wav");
s.start();  // Plays in background thread
```

**Threading**: Each sound plays in its own thread, allowing multiple sounds to play simultaneously.

---

## Velocity.java - Movement Calculations

**Location**: `handout/src/game2D/Velocity.java`  
**Lines**: 158 lines  
**Purpose**: Helper class for vector-based movement (angle + speed)

### Class Structure

```java
public class Velocity {
    private double angle;   // Angle in radians
    private double dangle;  // Angle in degrees
    private double speed;   // Speed magnitude
    private double dx;      // X component
    private double dy;      // Y component
}
```

### Function Breakdown

#### setVelocity() - Set Speed and Angle

**Lines**: 71-77  
```java
public void setVelocity(double s, double a) {
    speed = s;
    dangle = a;
    angle = Math.toRadians(a);
    reCalc();
}
```

#### reCalc() - Calculate Components

**Lines**: 52-62  
**Purpose**: Converts speed/angle to dx/dy components

```java
private void reCalc() {
    angle = Math.toRadians(dangle);
    dx = speed * Math.cos(angle);
    dy = speed * Math.sin(angle);
}
```

**Vector Math**:
```
       dx
    ────────►
   │        /
dy │      /  ← velocity vector
   │    /
   ▼  / angle
     /
```

**Example**:
```java
Velocity v = new Velocity();
v.setVelocity(10.0, 45.0);  // 10 pixels/ms at 45°

v.getdx() = 10 * cos(45°) = 7.07
v.getdy() = 10 * sin(45°) = 7.07
```

---

## Summary

This game engine demonstrates:

1. **Object-Oriented Design**: Clear separation of concerns (GameCore → Game, Sprite, TileMap, Animation)
2. **Game Loop**: Fixed time-step update with variable rendering
3. **Double Buffering**: Smooth animation without flicker
4. **Collision Detection**: Both AABB (sprite-sprite) and grid-based (sprite-tile)
5. **Procedural Generation**: Infinite pipe spawning with difficulty balancing
6. **State Management**: Menu, playing, game over, level complete states
7. **Resource Management**: Dynamic loading/unloading of entities
8. **Audio System**: Background music and sound effects
9. **Animation System**: Sprite sheet loading and frame management
10. **Camera System**: World scrolling with offset-based rendering

**Total Lines of Code**: ~2700 lines across 8 classes

---

## File Summary

| File | Lines | Purpose |
|------|-------|---------|
| Game.java | 1392 | Main game implementation |
| GameCore.java | 203 | Engine base class |
| Sprite.java | 457 | Entity management |
| TileMap.java | 401 | Level/grid system |
| Animation.java | 395 | Animation management |
| Tile.java | 56 | Tile data structure |
| Sound.java | 41 | Audio playback |
| Velocity.java | 158 | Vector calculations |
| **Total** | **3103** | |

---

*End of Detailed Technical Report*


import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.io.File;
import javax.sound.sampled.*;
import javax.sound.midi.*;

import game2D.*;

// Game demonstrates how we can override the GameCore class
// to create our own 'game'. We usually need to implement at
// least 'draw' and 'update' (not including any local event handling)
// to begin the process. You should also add code to the 'init'
// method that will initialise event handlers etc. 

// Student ID: ???????


@SuppressWarnings("serial")


public class Game extends GameCore implements MouseListener 
{
	// Useful game constants
	static int screenWidth = 640;
	static int screenHeight = 480;

	// Game constants
    float 	lift = 0.005f;
    float	gravity = 0.0002f;   // Gravity pulls bird down
    float	fly = -0.06f;        // Flap impulse (upward)
    float	moveSpeed = 0.06f;   // Constant forward speed
    float	jumpPower = -0.19f;  // Jump impulse for map2 (increased for higher jumps)
    float	walkSpeed = 0.08f;   // Walking speed for map2
    
    // Game state flags
    boolean flap = false;
    boolean moveRight = false;
    boolean moveLeft = false;
    boolean debug = false;
    boolean gameOver = false;
    boolean inMenu = true;        // Start in level selection menu
    boolean levelComplete = false; // Track if map2 is completed
    boolean onGround = false;     // Track if player is on ground (for map2 jumping)
    int currentLevel = 1;         // 1 = infinite map, 2 = platformer map

    // Game resources
    Animation landing,walk;
    
    Sprite	player = null;
    ArrayList<Sprite> 	clouds = new ArrayList<Sprite>();
    ArrayList<Sprite> 	walkers = new ArrayList<Sprite>();   // Walk sprites between pillars
    ArrayList<Float>    walkerLeftBounds = new ArrayList<Float>();   // Left patrol bound per walker
    ArrayList<Float>    walkerRightBounds = new ArrayList<Float>();  // Right patrol bound per walker
    ArrayList<Sprite> 	fallers = new ArrayList<Sprite>();   // Penguins that fall from sky
    ArrayList<Tile>		collidedTiles = new ArrayList<Tile>();

    TileMap tmap = new TileMap();	// Our tile map, note that we load it in init()
    
    long total;         			// The score will be the total time elapsed since a crash
    int lives = 3;                  // Number of tries the player has
    long fallerTimer = 0;           // Timer for spawning falling penguins
    long fallerInterval = 4000;     // Spawn a new penguin every 4 seconds (map1)
    long fallerIntervalMap2 = 3000; // Spawn faster in map2 (every 3 seconds)
    int lastPenguinSpawnCol = -99;  // Track last penguin spawn column to spread them out

    // Sound clips
    Clip slowLoopClip = null;       // Looping wing flap sound
    Sequencer midiSequencer = null; // MIDI background theme music

    // Base path for resources (auto-detected)
    String basePath = "";

    // Infinite pipe generation
    int generatedUpToColumn = 0;    // Farthest column we have generated content for
    int nextPipeColumn = 0;         // Next column index where a pipe will be placed
    int lastGapTop = -1;            // Gap top row of the last generated pipe (for smoothing)
    static final int PIPE_SPACING = 7;    // Columns between pipe pairs
    static final int GENERATE_AHEAD = 30; // Generate this many tile columns ahead of the player


    /**
	 * The obligatory main method that creates
     * an instance of our class and starts it running
     * 
     * @param args	The list of parameters this program might use (ignored)
     */
    public static void main(String[] args) {

        Game gct = new Game();
        gct.init();
        // Start in windowed mode with the given screen height and width
        gct.run(false,screenWidth,screenHeight);
    }

    /**
     * Initialise the class, e.g. set up variables, load images,
     * create animations, register event handlers.
     * 
     * This shows you the general principles but you should create specific
     * methods for setting up your game that can be called again when you wish to 
     * restart the game (for example you may only want to load animations once
     * but you could reset the positions of sprites each time you restart the game).
     */
    public void init()
    {         
        Sprite s;	// Temporary reference to a sprite

        // Auto-detect the base path for resources
        if (new File("maps/map.txt").exists()) {
            basePath = "";
        } else if (new File("handout/maps/map.txt").exists()) {
            basePath = "handout/";
        } else {
            System.err.println("Cannot find resource directory. Please run from the game2d or handout folder.");
        }

        // Load the tile map and print it out so we can check it is valid
        tmap.loadMap(basePath + "maps", "map.txt");
        
        // Set the virtual resolution for drawing
        virtualWidth = screenWidth;
        virtualHeight = screenHeight;
        
        // Set window size to match virtual resolution
        setSize(screenWidth, screenHeight);
        setResizable(false);
        setVisible(true);
        
        // Register mouse listener for mouse click events
        addMouseListener(this);

        // Create a set of background sprites that we can 
        // rearrange to give the illusion of motion
        
        landing = new Animation();
        landing.loadAnimationFromSheet(basePath + "images/landbird.png", 4, 1, 60);

        // Example of using loadAnimationSeries to load a sequence of images
        // from a sprite sheet. This will load 5 images from sprite sheet 
        // "images/walk.png" starting at position 6 (where the top left image
        // is 0). Parameters (3,4) indicate the sprite sheet consists of a grid
        // of 3 columns and 4 rows. To switch to this animation, press the A key.
        walk = new Animation();
        walk.loadAnimationSeries(basePath + "images/walk.png", 3, 4, 100, 6, 5);

        // Initialise the player with an animation
        player = new Sprite(landing);
        
        // Load six different cloud animations
        Animation ca1 = new Animation();
        ca1.addFrame(loadImage(basePath + "images/clouds-png-13366.png"), 1000);
        Animation ca2 = new Animation();
        ca2.addFrame(loadImage(basePath + "images/clouds-png-13369.png"), 1000);
        Animation ca3 = new Animation();
        ca3.addFrame(loadImage(basePath + "images/rain-png-34472.png"), 1000);
        Animation ca4 = new Animation();
        ca4.addFrame(loadImage(basePath + "images/real-clouds-png-13387.png"), 1000);
        Animation ca5 = new Animation();
        ca5.addFrame(loadImage(basePath + "images/real-clouds-png-13389.png"), 1000);
        Animation ca6 = new Animation();
        ca6.addFrame(loadImage(basePath + "images/real-clouds-png-13394.png"), 1000);
        Animation[] cloudAnims = { ca1, ca2, ca3, ca4, ca5, ca6 };
        
        
        // Create 50 clouds at random positions above the top of the pillars
        int numClouds = 50;
        for (int c=0; c<numClouds; c++)
        {
        	s = new Sprite(cloudAnims[(int)(Math.random() * cloudAnims.length)]);
        	s.setX((int)(Math.random() * screenWidth * 3)); // Spread wide
        	s.setY((int)(Math.random() * (screenHeight / 2))); // Top half of screen only (above pillars)
        	float cloudSpeed = -(0.008f + (float)(Math.random() * 0.06f));
        	s.setVelocityX(cloudSpeed); // Random speeds right-to-left
        	s.show();
        	clouds.add(s);
        }
        
        // Load sound clips
        slowLoopClip = loadClip(basePath + "sounds/slow.wav");
        midiSequencer = loadMidi(basePath + "sounds/Wii Channels - Mii Channel.mid");

        // Start the game immediately
        initialiseGame();
        
        System.out.println(tmap);
    }

    /**
     * You will probably want to put code to restart a game in
     * a separate method so that you can call it when restarting
     * the game when the player loses.
     */
    /**
     * Scans the tile map to find horizontal gaps between pipe columns
     * and spawns a walk sprite in each gap. The walker moves left-right slowly.
     */
    public void spawnWalkersBetweenPillars()
    {
    	walkers.clear();
    	walkerLeftBounds.clear();
    	walkerRightBounds.clear();
    	
    	int tw = tmap.getTileWidth();
    	int th = tmap.getTileHeight();
    	int mapW = tmap.getMapWidth();
    	int mapH = tmap.getMapHeight();
    	
    	// Find vertical pipe columns by checking if a column has mostly solid tiles
    	boolean[] isPipeColumn = new boolean[mapW];
    	for (int col = 0; col < mapW; col++) {
    		int solidCount = 0;
    		for (int row = 0; row < mapH; row++) {
    			Tile t = tmap.getTile(col, row);
    			if (t != null && t.getCharacter() != '.') solidCount++;
    		}
    		isPipeColumn[col] = (solidCount >= mapH / 2);
    	}
    	
    	// Walk across columns to find gaps between pipe columns
    	int gapStart = -1;
    	for (int col = 0; col <= mapW; col++) {
    		boolean solid = (col >= mapW) || isPipeColumn[col];
    		if (!solid && gapStart < 0) {
    			gapStart = col;
    		} else if (solid && gapStart >= 0) {
    			int gapEnd = col;
    			int gapWidth = gapEnd - gapStart;
    			
    			if (gapWidth >= 3) {
    				// Walker walks on the ground (bottom of the map)
    				
    				// Use walk.png (green creature) for walkers between pillars
    				int walkerSpeed = 150;
    				Animation walkerAnim = new Animation();
    				walkerAnim.loadAnimationSeries(basePath + "images/walk.png", 3, 4, walkerSpeed, 6, 5);
    				Sprite walker = new Sprite(walkerAnim);
    				
    				// Store the left and right pixel bounds for the walker's patrol area
    				float leftBound = (gapStart + 1) * tw;
    				float rightBound = (gapEnd - 1) * tw;
    				float px = (gapStart + gapWidth / 2.0f) * tw;
    				float py = mapH * th - walker.getHeight(); // Position so walker stands on bottom border
    				walker.setPosition(px, py);
    				float walkerVelocity = 0.01f;
    				walker.setVelocityX(walkerVelocity); // Slow left-right movement
    				walker.show();
    				walkers.add(walker);
    				walkerLeftBounds.add(leftBound);
    				walkerRightBounds.add(rightBound);
    			}
    			gapStart = -1;
    		}
    	}
    }
    
    /**
     * Spawn a falling penguin ahead of the player so it is visible and dodgeable.
     * The penguin drops from the top of the screen at a position ahead of the player.
     * IMPORTANT: Only spawns on solid pipe columns to avoid unfair gaps.
     */
    public void spawnFallingPenguin()
    {
    	int penguinSpeed = 150;
    	Animation penguinAnim = new Animation();
    	penguinAnim.loadAnimationFromSheet(basePath + "images/penguin.png", 4, 1, penguinSpeed);
    	Sprite penguin = new Sprite(penguinAnim);
    	
    	// Scale penguin to full size (150x150 pixels)
    	penguin.setScale(1.0f);
    	
    	int tw = tmap.getTileWidth();
    	int th = tmap.getTileHeight();
    	int mapH = tmap.getMapHeight();
    	int playerCol = (int)(player.getX() / tw);
    	
    	// Look for pipe columns (not gaps) ahead of the player
    	ArrayList<Integer> pipeColumns = new ArrayList<Integer>();
    	for (int col = playerCol + 15; col < playerCol + 25; col++)
    	{
    		if (col >= tmap.getMapWidth()) break;
    		
    		// Check if this column has a pipe (not a gap) in the middle rows
    		// A pipe column will have 'p' or 't'/'b' tiles, not '.' gaps
    		boolean hasGap = false;
    		for (int row = 3; row < mapH - 3; row++)
    		{
    			char tile = tmap.getTileChar(col, row);
    			if (tile == '.')
    			{
    				hasGap = true;
    				break;
    			}
    		}
    		
    		// Only add columns that are NOT gaps (have solid pipes)
    		if (!hasGap)
    			pipeColumns.add(col);
    	}
    	
    	// If we found pipe columns, spawn on one of them
    	if (pipeColumns.size() > 0)
    	{
    		int chosenCol = pipeColumns.get((int)(Math.random() * pipeColumns.size()));
    		float spawnX = chosenCol * tw + tw / 2; // Center of the tile column
    		penguin.setPosition(spawnX, -30); // Start above the visible screen
    		float fallSpeed = 0.04f;
    		penguin.setVelocityY(fallSpeed);  // Fall slowly enough to dodge
    		penguin.setVelocityX(0);      // Falls straight down
    		penguin.show();
    		fallers.add(penguin);
    	}
    	// If no pipe columns found, don't spawn (skip this spawn attempt)
    }
    
    /**
     * Spawn a falling penguin for map2 - targets platform tops ahead of player.
     * Penguins fall onto platforms to create obstacles.
     */
    public void spawnFallingPenguinMap2()
    {
    	int penguinSpeed = 150;
    	Animation penguinAnim = new Animation();
    	penguinAnim.loadAnimationFromSheet(basePath + "images/penguin.png", 4, 1, penguinSpeed);
    	Sprite penguin = new Sprite(penguinAnim);
    	
    	// Scale penguin to full size (150x150 pixels)
    	penguin.setScale(1.0f);
    	
    	int tw = tmap.getTileWidth();
    	int th = tmap.getTileHeight();
    	int mapW = tmap.getMapWidth();
    	int mapH = tmap.getMapHeight();
    	int playerCol = (int)(player.getX() / tw);
    	
    	// Find platform tiles ahead of the player (exclude bottom 2 rows which is ground)
    	ArrayList<Integer> platformColumns = new ArrayList<Integer>();
    	for (int col = playerCol + 8; col < Math.min(playerCol + 20, mapW); col++)
    	{
    		// Don't spawn too close to last penguin spawn location (give at least 5 tiles space)
    		if (Math.abs(col - lastPenguinSpawnCol) < 5) {
    			continue;
    		}
    		
    		// Check if this column has a platform (solid tile not on ground level)
    		for (int row = 0; row < mapH - 2; row++)
    		{
    			char tile = tmap.getTileChar(col, row);
    			// If we find a solid tile that has empty space above it
    			if (tile != '.' && row > 0)
    			{
    				char above = tmap.getTileChar(col, row - 1);
    				if (above == '.')
    				{
    					platformColumns.add(col);
    					break; // Found a platform top in this column
    				}
    			}
    		}
    	}
    	
    	// If we found platforms, spawn above one of them
    	if (platformColumns.size() > 0)
    	{
    		int chosenCol = platformColumns.get((int)(Math.random() * platformColumns.size()));
    		float spawnX = chosenCol * tw + tw / 2; // Center of the tile column
    		penguin.setPosition(spawnX, -50); // Start above the visible screen
    		float fallSpeed = 0.035f;
    		penguin.setVelocityY(fallSpeed);  // Fall slowly
    		penguin.setVelocityX(0);      // Falls straight down
    		penguin.show();
    		fallers.add(penguin);
    		lastPenguinSpawnCol = chosenCol; // Remember where we spawned
    	}
    }
    
    /**
     * Procedurally generates pipe columns ahead of the player so the game
     * never runs out of obstacles. Expands the tile map as needed.
     */
    public void generatePipesAhead()
    {
    	int tw = tmap.getTileWidth();
    	int playerCol = (int)(player.getX() / tw);
    	int targetCol = playerCol + GENERATE_AHEAD;
    	
    	while (generatedUpToColumn < targetCol)
    	{
    		generatedUpToColumn++;
    		
    		// Expand tile map if we are about to run out of columns
    		if (generatedUpToColumn >= tmap.getMapWidth() - 2)
    			tmap.expandWidth(100);
    		
    		// Place a pipe at the scheduled column
    		if (generatedUpToColumn >= nextPipeColumn)
    		{
    			int mapH = tmap.getMapHeight();
    			int gapSize = 3 + (int)(Math.random() * 2); // 3 or 4 rows
    			int minTop = 1;
    			int maxTop = mapH - gapSize - 1;
    			
    			// Smooth gap position so consecutive pipes are reachable
    			if (lastGapTop >= 0)
    			{
    				minTop = Math.max(minTop, lastGapTop - 2);
    				maxTop = Math.min(maxTop, lastGapTop + 2);
    			}
    			if (maxTop < minTop) maxTop = minTop;
    			
    			int gapTop = minTop + (int)(Math.random() * (maxTop - minTop + 1));
    			generatePipeColumn(nextPipeColumn, gapTop, gapSize);
    			lastGapTop = gapTop;
    			
    			// Optionally spawn a walker in the gap after this pipe
    			int prevPipeCol = nextPipeColumn - PIPE_SPACING;
    			if (prevPipeCol > 0 && Math.random() < 0.5)
    				spawnWalkerInGap(prevPipeCol, nextPipeColumn);
    			
    			nextPipeColumn += PIPE_SPACING;
    		}
    	}
    }
    
    /**
     * Writes a single pipe column into the tile map at the given column.
     * The top pipe runs from row 0 to gapTop (with 'b' cap at gapTop).
     * The bottom pipe runs from gapBot to the last row (with 't' cap at gapBot).
     * 
     * @param col      The tile column index
     * @param gapTop   The row where the top pipe ends (cap row)
     * @param gapSize  The number of empty rows forming the gap
     */
    public void generatePipeColumn(int col, int gapTop, int gapSize)
    {
    	int mapH = tmap.getMapHeight();
    	int gapBot = gapTop + gapSize + 1; // First row of bottom pipe (cap)
    	
    	for (int row = 0; row < mapH; row++)
    	{
    		if (row < gapTop)
    			tmap.setTileChar('p', col, row);      // Top pipe body
    		else if (row == gapTop)
    			tmap.setTileChar('b', col, row);      // Bottom cap of top pipe
    		else if (row < gapBot)
    			tmap.setTileChar('.', col, row);       // Gap (bird flies here)
    		else if (row == gapBot)
    			tmap.setTileChar('t', col, row);       // Top cap of bottom pipe
    		else
    			tmap.setTileChar('p', col, row);       // Bottom pipe body
    	}
    }
    
    /**
     * Spawns a walker (green creature) that patrols between two pipe columns.
     * 
     * @param leftPipeCol   Column index of the left pipe
     * @param rightPipeCol  Column index of the right pipe
     */
    public void spawnWalkerInGap(int leftPipeCol, int rightPipeCol)
    {
    	int tw = tmap.getTileWidth();
    	int th = tmap.getTileHeight();
    	int mapH = tmap.getMapHeight();
    	
    	int gapCols = rightPipeCol - leftPipeCol - 1;
    	if (gapCols < 3) return;
    	
    	int walkerSpeed = 150;
    	Animation walkerAnim = new Animation();
    	walkerAnim.loadAnimationSeries(basePath + "images/walk.png", 3, 4, walkerSpeed, 6, 5);
    	Sprite walker = new Sprite(walkerAnim);
    	
    	float leftBound = (leftPipeCol + 1) * tw;
    	float rightBound = rightPipeCol * tw;
    	float px = ((leftPipeCol + rightPipeCol) / 2.0f) * tw;
    	float py = mapH * th - walker.getHeight(); // Position so walker stands on bottom border
    	
    	walker.setPosition(px, py);
    	float walkerVelocity = 0.01f;
    	walker.setVelocityX(walkerVelocity);
    	walker.show();
    	walkers.add(walker);
    	walkerLeftBounds.add(leftBound);
    	walkerRightBounds.add(rightBound);
    }
    
    /**
     * Removes walkers and fallers that are far behind the player
     * to prevent unbounded memory growth during long play sessions.
     */
    public void cleanUpBehindPlayer()
    {
    	float behindThreshold = player.getX() - 800; // 800px behind player
    	
    	// Clean up walkers
    	for (int i = walkers.size() - 1; i >= 0; i--)
    	{
    		if (walkers.get(i).getX() + walkers.get(i).getWidth() < behindThreshold)
    		{
    			walkers.remove(i);
    			walkerLeftBounds.remove(i);
    			walkerRightBounds.remove(i);
    		}
    	}
    	
    	// Clean up fallers that went off-screen horizontally
    	for (int i = fallers.size() - 1; i >= 0; i--)
    	{
    		if (fallers.get(i).getX() + fallers.get(i).getWidth() < behindThreshold)
    			fallers.remove(i);
    	}
    }
    
    /**
     * Loads a WAV file into a Clip so it can be started/stopped/looped.
     */
    public Clip loadClip(String filename)
    {
    	try {
    		File file = new File(filename);
    		AudioInputStream stream = AudioSystem.getAudioInputStream(file);
    		AudioFormat format = stream.getFormat();
    		DataLine.Info info = new DataLine.Info(Clip.class, format);
    		Clip clip = (Clip)AudioSystem.getLine(info);
    		clip.open(stream);
    		return clip;
    	} catch (Exception e) {
    		System.err.println("Failed to load clip: " + filename + " - " + e);
    		return null;
    	}
    }
    
    /**
     * Loads a MIDI file into a Sequencer so it can be played/stopped/looped.
     */
    public Sequencer loadMidi(String filename)
    {
    	try {
    		Sequencer sequencer = MidiSystem.getSequencer();
    		sequencer.open();
    		File midiFile = new File(filename);
    		Sequence sequence = MidiSystem.getSequence(midiFile);
    		sequencer.setSequence(sequence);
    		return sequencer;
    	} catch (Exception e) {
    		System.err.println("Failed to load MIDI: " + filename + " - " + e);
    		return null;
    	}
    }

    /**
     * Start looping the slow wing-flap sound.
     */
    public void startSlowLoop()
    {
    	if (slowLoopClip != null) {
    		slowLoopClip.setFramePosition(0);
    		slowLoopClip.loop(Clip.LOOP_CONTINUOUSLY);
    	}
    }

    /**
     * Stop the looping slow wing-flap sound.
     */
    public void stopSlowLoop()
    {
    	if (slowLoopClip != null) {
    		slowLoopClip.stop();
    	}
    }

    /**
     * Start looping the background theme music (MIDI).
     */
    public void startTheme()
    {
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

    /**
     * Stop the background theme music (MIDI).
     */
    public void stopTheme()
    {
    	if (midiSequencer != null) {
    		midiSequencer.stop();
    	}
    }

    public void initialiseGame()
    {
    	total = 0;
    	lives = 3;
    	gameOver = false;
    	levelComplete = false;
    	fallerTimer = 0;
    	
    	// Load the appropriate map based on selected level
    	if (currentLevel == 1) {
    		tmap.loadMap(basePath + "maps", "map.txt");
    		generatedUpToColumn = tmap.getMapWidth() - 1;
    		nextPipeColumn = generatedUpToColumn + 5;
    		lastGapTop = -1;
    		// Spawn walkers for infinite map
    		spawnWalkersBetweenPillars();
    	} else {
    		tmap.loadMap(basePath + "maps", "map2.txt");
    		generatedUpToColumn = -1; // Disable procedural generation for map2
    		walkers.clear(); // No walkers in map2
    		walkerLeftBounds.clear();
    		walkerRightBounds.clear();
    	}
    	      
        player.setPosition(200,200);
        player.setVelocity(0,0);
        player.show();
        
        // Clear falling penguins
        fallers.clear();

        // Start looping the slow wing flap sound and background theme
        startSlowLoop();
        startTheme();
        
    }
    
    /**
     * Reset walker positions back to their gaps
     */
    public void resetWalkers()
    {
    	int tw = tmap.getTileWidth();
    	int th = tmap.getTileHeight();
    	int mapW = tmap.getMapWidth();
    	int mapH = tmap.getMapHeight();
    	
    	boolean[] isPipeColumn = new boolean[mapW];
    	for (int col = 0; col < mapW; col++) {
    		int solidCount = 0;
    		for (int row = 0; row < mapH; row++) {
    			Tile t = tmap.getTile(col, row);
    			if (t != null && t.getCharacter() != '.') solidCount++;
    		}
    		isPipeColumn[col] = (solidCount >= mapH / 2);
    	}
    	
    	int walkerIdx = 0;
    	int gapStart = -1;
    	for (int col = 0; col <= mapW; col++) {
    		boolean solid = (col >= mapW) || isPipeColumn[col];
    		if (!solid && gapStart < 0) {
    			gapStart = col;
    		} else if (solid && gapStart >= 0) {
    			int gapEnd = col;
    			int gapWidth = gapEnd - gapStart;
    			
    			if (gapWidth >= 3 && walkerIdx < walkers.size()) {
    				Sprite walker = walkers.get(walkerIdx);
    				float px = (gapStart + gapWidth / 2.0f) * tw;
    				float py = mapH * th - walker.getHeight(); // Position so walker stands on bottom border
    				walker.setPosition(px, py);
    				walker.setVelocityX(0.01f);
    				walker.setVelocityY(0);
    				walkerIdx++;
    			}
    			gapStart = -1;
    		}
    	}
    }
    
    /**
     * Handle a crash - lose a life or game over
     */
    public void handleCrash()
    {
    	lives--;
    	total = 0; // Reset score when losing a life
    	Sound sound = new Sound(basePath + "sounds/caw.wav"); 
		sound.start();
		
    	if (lives <= 0) {
    		gameOver = true;
    		stopSlowLoop(); // Stop wing sound on game over
    		stopTheme();   // Stop background theme on game over
    	} else {
    		player.setPosition(200,200);
	        player.setVelocity(0,0);
	        player.show();
	        fallers.clear();
	        fallerTimer = 0;
	        // Re-spawn walkers for the initial map; dynamic walkers
	        // will be created again as the player progresses forward
	        spawnWalkersBetweenPillars();
    	}
    }
    

    
    /**
     * Draw the current state of the game. Note the sample use of
     * debugging output that is drawn directly to the game screen.
     */
    public void draw(Graphics2D g)
    {    	
    	// Draw level selection menu
    	if (inMenu) {
    		drawMenu(g);
    		return;
    	}
    	
    	// Be careful about the order in which you draw objects - you
    	// should draw the background first, then work your way 'forward'

    	// First work out how much we need to shift the view in order to
    	// see where the player is. To do this, we adjust the offset so that
        // it is relative to the player's position along with a shift
        int xo = -(int)player.getX() + 200;
        int yo = -(int)player.getY() + 200;

        g.setColor(new Color(207, 236, 247));
        g.fillRect(0, 0, getWidth(), getHeight());
        

        
        // Draw clouds independently on screen (no world offset, pure background)
        for (Sprite s: clouds)
        {
        	s.setOffsets(0, 0);  // Clouds are in screen space, not world space
        	s.draw(g);
        }
        
        // Draw walkers (green creatures between pillars)
        for (int i = 0; i < walkers.size(); i++)
        {
        	Sprite w = walkers.get(i);
        	w.setOffsets(xo,yo);
        	w.draw(g);
        }
        
        // Draw falling penguins
        for (int i = 0; i < fallers.size(); i++)
        {
        	Sprite f = fallers.get(i);
        	f.setOffsets(xo,yo);
        	f.draw(g);
        }

        // Apply offsets to tile map and draw it with level-specific colors
        drawTileMap(g, xo, yo); 

        // Apply offsets to player and draw 
        player.setOffsets(xo, yo);
        player.draw(g);
                
        
        // Show score and status information
        String msg = String.format("Score: %d", total/100);
        g.setColor(Color.darkGray);
        g.drawString(msg, getWidth() - 150, 50);
        
        String livesMsg = String.format("Lives: %d", lives);
        g.setColor(Color.red);
        g.drawString(livesMsg, getWidth() - 150, 70);
        
        // Draw level complete screen for map2
        if (levelComplete) {
        	// Semi-transparent overlay
        	g.setColor(new Color(0, 255, 0, 150));
        	g.fillRect(0, 0, getWidth(), getHeight());
        	
        	g.setColor(Color.white);
        	g.setFont(new Font("Arial", Font.BOLD, 28));
        	String winMsg = "LEVEL COMPLETE!";
        	int msgWidth = g.getFontMetrics().stringWidth(winMsg);
        	g.drawString(winMsg, (getWidth() - msgWidth) / 2, getHeight() / 2 - 30);
        	
        	g.setFont(new Font("Arial", Font.PLAIN, 14));
        	String scoreMsg = String.format("Final Score: %d", total/100);
        	int scoreWidth = g.getFontMetrics().stringWidth(scoreMsg);
        	g.drawString(scoreMsg, (getWidth() - scoreWidth) / 2, getHeight() / 2);
        	
        	String menuMsg = "Press M for menu or ESC to quit";
        	int menuWidth = g.getFontMetrics().stringWidth(menuMsg);
        	g.drawString(menuMsg, (getWidth() - menuWidth) / 2, getHeight() / 2 + 30);
        	
        	// Reset font
        	g.setFont(new Font("Dialog", Font.PLAIN, 12));
        }
        
        // Draw game over screen
        if (gameOver) {
        	// Semi-transparent overlay
        	g.setColor(new Color(0, 0, 0, 150));
        	g.fillRect(0, 0, getWidth(), getHeight());
        	
        	g.setColor(Color.white);
        	g.setFont(new Font("Arial", Font.BOLD, 24));
        	String gameOverMsg = "GAME OVER";
        	int msgWidth = g.getFontMetrics().stringWidth(gameOverMsg);
        	g.drawString(gameOverMsg, (getWidth() - msgWidth) / 2, getHeight() / 2 - 30);
        	
        	g.setFont(new Font("Arial", Font.PLAIN, 14));
        	String scoreMsg = String.format("Final Score: %d", total/100);
        	int scoreWidth = g.getFontMetrics().stringWidth(scoreMsg);
        	g.drawString(scoreMsg, (getWidth() - scoreWidth) / 2, getHeight() / 2);
        	
        	String restartMsg = "Press SPACE to restart or M for menu";
        	int restartWidth = g.getFontMetrics().stringWidth(restartMsg);
        	g.drawString(restartMsg, (getWidth() - restartWidth) / 2, getHeight() / 2 + 30);
        	
        	// Reset font
        	g.setFont(new Font("Dialog", Font.PLAIN, 12));
        }
        
        if (debug)
        {

        	// When in debug mode, you could draw borders around objects
            // and write messages to the screen with useful information.
            // Try to avoid printing to the console since it will produce 
            // a lot of output and slow down your game.
            tmap.drawBorder(g, xo, yo, Color.black);

            g.setColor(Color.red);
        	player.drawBoundingBox(g);
        
        	g.drawString(String.format("Player: %.0f,%.0f", player.getX(),player.getY()),
        								getWidth() - 100, 90);
        	
        	drawCollidedTiles(g, tmap, xo, yo);
        }
        
        // Draw top and bottom border lines
        int borderThick = 3;
        g.setColor(Color.darkGray);
        g.fillRect(0, 0, screenWidth, borderThick);                                          // Top line
        int bottomLineY = yo + tmap.getPixelHeight();  // Bottom of tile map in screen coords
        g.fillRect(0, bottomLineY - borderThick, screenWidth, borderThick);                   // Bottom line

    }
    
    /**
     * Custom tile map drawing.
     */
    public void drawTileMap(Graphics2D g, int xoff, int yoff)
    {
    	if (g == null) return;
    	
    	Image img = null;
    	Rectangle rect = (Rectangle)g.getClip();
    	int xc, yc;
    	
    	for (int r = 0; r < tmap.getMapHeight(); r++)
    	{
    		for (int c = 0; c < tmap.getMapWidth(); c++)
    		{
    			img = tmap.getTileImage(c, r);
    			if (img == null) continue;
    			xc = xoff + c * tmap.getTileWidth();
    			yc = yoff + r * tmap.getTileHeight();
    			
    			// Only draw the tile if it is on screen
    			if (xc + tmap.getTileWidth() < 0 || xc >= rect.x + rect.width) continue;
    			if (yc + tmap.getTileHeight() < 0 || yc >= rect.y + rect.height) continue;
    			
    			// Draw the tile
    			g.drawImage(img, xc, yc, null);
    		}
    	}
    }

    public void drawCollidedTiles(Graphics2D g, TileMap map, int xOffset, int yOffset)
    {
		if (collidedTiles.size() > 0)
		{	
			int tileWidth = map.getTileWidth();
			int tileHeight = map.getTileHeight();
			
			g.setColor(Color.blue);
			for (Tile t : collidedTiles)
			{
				g.drawRect(t.getXC()+xOffset, t.getYC()+yOffset, tileWidth, tileHeight);
			}
		}
    }
	
    /**
     * Draw the level selection menu
     */
    public void drawMenu(Graphics2D g)
    {
    	g.setColor(new Color(207, 236, 247));
    	g.fillRect(0, 0, getWidth(), getHeight());
    	
    	g.setColor(Color.black);
    	g.setFont(new Font("Arial", Font.BOLD, 32));
    	String title = "FLAPPY BIRD GAME";
    	int titleWidth = g.getFontMetrics().stringWidth(title);
    	g.drawString(title, (getWidth() - titleWidth) / 2, 100);
    	
    	g.setFont(new Font("Arial", Font.BOLD, 20));
    	g.setColor(Color.blue);
    	String level1 = "Press 1: Infinite Mode (Flappy Bird)";
    	int l1Width = g.getFontMetrics().stringWidth(level1);
    	g.drawString(level1, (getWidth() - l1Width) / 2, 200);
    	
    	g.setColor(Color.green);
    	String level2 = "Press 2: Platform Mode (Short & Simple)";
    	int l2Width = g.getFontMetrics().stringWidth(level2);
    	g.drawString(level2, (getWidth() - l2Width) / 2, 250);
    	
    	g.setFont(new Font("Arial", Font.PLAIN, 14));
    	g.setColor(Color.darkGray);
    	String info1 = "Map 1 Controls: UP/SPACE/CLICK to flap";
    	int info1Width = g.getFontMetrics().stringWidth(info1);
    	g.drawString(info1, (getWidth() - info1Width) / 2, 330);
    	
    	String info2 = "Map 2 Controls: A/D or LEFT/RIGHT to move, W/UP/SPACE to jump";
    	int info2Width = g.getFontMetrics().stringWidth(info2);
    	g.drawString(info2, (getWidth() - info2Width) / 2, 350);
    	
    	String info3 = "B = Debug Mode | ESC = Quit";
    	int info3Width = g.getFontMetrics().stringWidth(info3);
    	g.drawString(info3, (getWidth() - info3Width) / 2, 370);
    	
    	// Reset font
    	g.setFont(new Font("Dialog", Font.PLAIN, 12));
    }
    
    /**
     * Update any sprites and check for collisions
     * 
     * @param elapsed The elapsed time between this call and the previous call of elapsed
     */    
    public void update(long elapsed)
    {
    	// If in menu or game is over, don't update game logic
    	if (inMenu || gameOver || levelComplete) return;
    	
    	total += elapsed; // Increase score based on survival time
    	
        // Make adjustments to the speed of the sprite due to gravity
        player.setVelocityY(player.getVelocityY()+(gravity*elapsed));
    	    	
       	player.setAnimationSpeed(1.0f);
       	
       	if (currentLevel == 1) {
       		// Map1: Flappy Bird style - fly and auto-move forward
       		if (flap) 
       		{
       			player.setAnimationSpeed(1.8f);
       			player.setVelocityY(fly); // Flap upward
       		}
       		// Bird always moves forward at constant speed (like real Flappy Bird)
       		player.setVelocityX(moveSpeed);
       	} else {
       		// Map2: Platformer style - walk and jump
       		float xVel = 0;
       		if (moveRight) {
       			xVel = walkSpeed;
       			player.setAnimationSpeed(1.5f);
       		}
       		if (moveLeft) {
       			xVel = -walkSpeed;
       			player.setAnimationSpeed(1.5f);
       		}
       		player.setVelocityX(xVel);
       		
       		// Jump only when on ground
       		if (flap && onGround) {
       			player.setVelocityY(jumpPower);
       			player.setAnimationSpeed(1.8f);
       			onGround = false; // Leave ground
       		}
       	}
       		
       	
                
       	// Update clouds - move right to left, wrap around when off-screen
       	for (Sprite s: clouds) {
       		s.update(elapsed);
       		// When cloud goes off left edge, respawn on the right above the pillars
       		if (s.getX() + s.getWidth() < 0) {
       			s.setX(screenWidth + (int)(Math.random() * 200));
       			s.setY((int)(Math.random() * (screenHeight / 2))); // Top half only
       			s.setVelocityX(-(0.008f + (float)(Math.random() * 0.06f)));
       		}
       	}
       	
       	// Update walkers (green creatures walking between pillars)
       	for (int i = 0; i < walkers.size(); i++) {
       		Sprite w = walkers.get(i);
       		float leftBound = walkerLeftBounds.get(i);
       		float rightBound = walkerRightBounds.get(i);
       		
       		w.update(elapsed);
       		
       		// Bounce between left and right bounds of the gap
       		float walkerSpeed = 0.01f;
       		if (w.getX() <= leftBound) {
       			w.setX(leftBound);
       			w.setVelocityX(walkerSpeed); // Walk right
       		} else if (w.getX() + w.getWidth() >= rightBound) {
       			w.setX(rightBound - w.getWidth());
       			w.setVelocityX(-walkerSpeed); // Walk left
       		}
       		
       		if (boundingBoxCollision(player, w)) {
       			System.out.println("Player hit walker!");
       			handleCrash();
       			return;
       		}
       	}
       	
       	// Spawn falling penguins periodically
       	fallerTimer += elapsed;
       	long currentInterval = (currentLevel == 1) ? fallerInterval : fallerIntervalMap2;
       	if (fallerTimer >= currentInterval) {
       		fallerTimer = 0;
       		if (currentLevel == 1) {
       			spawnFallingPenguin();
       		} else {
       			spawnFallingPenguinMap2();
       		}
       	}
       	
       	// Update falling penguins
       	for (int i = fallers.size() - 1; i >= 0; i--) {
       		Sprite f = fallers.get(i);
       		
       		// Apply gravity to falling penguins in map2
       		if (currentLevel == 2 && f.getVelocityY() >= 0) {
       			f.setVelocityY(f.getVelocityY() + (gravity * elapsed * 0.5f));
       		}
       		
       		// In map2, check if penguin has landed on a platform and make it patrol
       		if (currentLevel == 2 && f.getVelocityY() > 0) {
       			// Check if penguin is about to land on a solid tile
       			int tw = tmap.getTileWidth();
       			int th = tmap.getTileHeight();
    			int penguinCol = (int)((f.getX() + f.getWidth()/2) / tw); // Center of penguin
    			int penguinBottomRow = (int)((f.getY() + f.getHeight()) / th);
    			
    			// Check tile below penguin
    			if (penguinBottomRow < tmap.getMapHeight()) {
    				char tileBelow = tmap.getTileChar(penguinCol, penguinBottomRow);
    				if (tileBelow != '.') {
    					// Penguin landed on a platform - make it patrol
    					f.setVelocityY(0);
    					f.setVelocityX(0.015f); // Start walking right
    					// Snap to top of platform - position penguin perfectly on platform surface
    					int platformTopY = penguinBottomRow * th - (int)f.getHeight();
    					f.setY(platformTopY);
    				}
    			}
    		}
    		
    		// If penguin is patrolling (no Y velocity), check edges
    		if (Math.abs(f.getVelocityY()) < 0.001f && Math.abs(f.getVelocityX()) > 0) {
    			int tw = tmap.getTileWidth();
    			int th = tmap.getTileHeight();
    			
    			// Check if penguin is about to walk off edge or hit a wall
    			// Use penguin center for more accurate collision
    			int penguinCenterCol = (int)((f.getX() + f.getWidth()/2) / tw);
    			int penguinBottomRow = (int)((f.getY() + f.getHeight()) / th);
    			
    			// Determine next position based on direction
    			int nextCol = penguinCenterCol;
    			if (f.getVelocityX() > 0) {
    				nextCol = (int)((f.getX() + f.getWidth()) / tw); // Check right edge
    			} else {
    				nextCol = (int)(f.getX() / tw); // Check left edge
    			}
    			
    			// Check for wall or edge ahead
    			if (nextCol >= 0 && nextCol < tmap.getMapWidth()) {
    				// Check tile at penguin's feet (where it would walk)
    				char tileAtFeet = tmap.getTileChar(nextCol, penguinBottomRow);
    				
    				// If no solid ground ahead, reverse direction
    				if (tileAtFeet == '.') {
    					f.setVelocityX(-f.getVelocityX());
    				}
    			} else {
    				// Reverse at map edges
    				f.setVelocityX(-f.getVelocityX());
    			}
    		}
    		
    		f.update(elapsed);
    		
    		// Remove penguin if it falls below the map
    		if (f.getY() > tmap.getPixelHeight()) {
    			fallers.remove(i);
    			continue;
    		}
    		
    		// Check collision with penguin
    		if (boundingBoxCollision(player, f)) {
    			// Check if player is stomping on penguin (jumping on top)
    			float playerBottom = player.getY() + player.getHeight();
    			float penguinTop = f.getY();
    			float penguinMiddle = f.getY() + f.getHeight() / 2;
    			
    			// If player is falling and hitting from above (player's bottom is above penguin's middle)
    			if (player.getVelocityY() > 0 && playerBottom < penguinMiddle) {
    				// Player stomped on penguin - remove it and bounce player
    				System.out.println("Stomped penguin!");
    				fallers.remove(i);
    				player.setVelocityY(-0.1f); // Small bounce
    				continue;
    			} else {
    				// Hit from side or bottom - crash
    				System.out.println("Player hit falling penguin!");
    				handleCrash();
    				return;
    			}
    		}
    	}
    	
    	// Save old position before updating
    	float oldX = player.getX();
    	float oldY = player.getY();
    	
    	// Now update the sprites animation and position
    	player.update(elapsed);
    	
	// Check if player completed map2
	if (currentLevel == 2 && player.getX() >= tmap.getPixelWidth() - 100) {
		levelComplete = true;
		stopSlowLoop();
		stopTheme();
		return;
	}
	
	// Then check for any collisions that may have occurred
	handleScreenEdge(player, tmap, elapsed);
	checkTileCollision(player, tmap, oldX, oldY);
}

/**
 * Checks and handles collisions with the edge of the screen. You should generally
 * use tile map collisions to prevent the player leaving the game area. This method
 * is only included as a temporary measure until you have properly developed your
 * tile maps.
 * 
 * @param s			The Sprite to check collisions for
 * @param tmap		The tile map to check 
 * @param elapsed	How much time has gone by since the last call
 */
public void handleScreenEdge(Sprite s, TileMap tmap, long elapsed)
{
	// This method just checks if the sprite has gone off the bottom screen.
	// Ideally you should use tile collision instead of this approach
	
	float difference = s.getY() + s.getHeight() - tmap.getPixelHeight();
	if (difference > 0)
	{
		// Put the player back on the map according to how far over they were
		s.setY(tmap.getPixelHeight() - s.getHeight() - (int)(difference)); 
		
		// Stop falling
		s.setVelocityY(0);
	}
	
	// Also check ceiling for flappy bird
	if (s.getY() < 0 && s == player) {
		System.out.println("Crashed into the ceiling!");
		handleCrash();
		return;
	}
}
    
    
     
    /**
     * Override of the keyPressed event defined in GameCore to catch our
     * own events
     * 
     *  @param e The event that has been generated
     */
    public void keyPressed(KeyEvent e) 
    { 
    	int key = e.getKeyCode();
    	
	switch (key)
	{
		case KeyEvent.VK_1      : if (inMenu) { currentLevel = 1; inMenu = false; initialiseGame(); } break;
		case KeyEvent.VK_2      : if (inMenu) { currentLevel = 2; inMenu = false; initialiseGame(); } break;
		case KeyEvent.VK_M      : if (gameOver || levelComplete) { inMenu = true; gameOver = false; levelComplete = false; stopSlowLoop(); stopTheme(); } break;
		case KeyEvent.VK_UP     : if (!inMenu) flap = true; break;
		case KeyEvent.VK_W      : if (!inMenu) flap = true; break;
		case KeyEvent.VK_LEFT   : if (!inMenu) moveLeft = true; break;
		case KeyEvent.VK_A      : if (!inMenu) moveLeft = true; break;
		case KeyEvent.VK_RIGHT  : if (!inMenu) moveRight = true; break;
		case KeyEvent.VK_D      : if (!inMenu) moveRight = true; break;
		case KeyEvent.VK_SPACE  : if (gameOver) { initialiseGame(); } else if (!inMenu) { flap = true; } break;
		case KeyEvent.VK_S 		: if (!inMenu && currentLevel == 1) { Sound s = new Sound(basePath + "sounds/caw.wav"); s.start(); } break;
		case KeyEvent.VK_ESCAPE : stop(); break;
		case KeyEvent.VK_B 		: debug = !debug; break; // Flip the debug state
		default :  break;
	}
    
    }

    /** Use the sample code in the lecture notes to properly detect
     * a bounding box collision between sprites s1 and s2.
     * 
     * @return	true if a collision may have occurred, false if it has not.
     */
    public boolean boundingBoxCollision(Sprite s1, Sprite s2)
    {
    	return ((s1.getX() + s1.getWidth()) > s2.getX() &&
    			(s1.getX() < (s2.getX() + s2.getWidth())) &&
    			(s1.getY() + s1.getHeight()) > s2.getY() &&
    			(s1.getY() < (s2.getY() + s2.getHeight())));
    }
    
    /**
     * Check and handles collisions with a tile map for the
     * given sprite 's'. Initial functionality is limited...
     * 
     * @param s			The Sprite to check collisions for
     * @param tmap		The tile map to check 
     */

    public void checkTileCollision(Sprite s, TileMap tmap, float oldX, float oldY)
    {
    	collidedTiles.clear();
    	
    	float sx = s.getX();
    	float sy = s.getY();
    	float sw = s.getWidth();
    	float sh = s.getHeight();
    	
    	float tileWidth = tmap.getTileWidth();
    	float tileHeight = tmap.getTileHeight();
    	
    	// Check X axis collision first
    	s.setY(oldY); // Temporarily revert Y to check X only
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
    			// In map1 (Flappy Bird), hitting sides is game over
    			System.out.println("Crashed into a pipe (X axis)!");
    			handleCrash();
    			return;
    		} else if (s == player && currentLevel == 2) {
    			// In map2 (platformer), stop at walls but don't crash
    			if (s.getVelocityX() > 0) { // Moving right
    				s.setX(rightTile * tileWidth - sw);
    			} else if (s.getVelocityX() < 0) { // Moving left
    				s.setX((leftTile + 1) * tileWidth);
    			}
    			s.setVelocityX(0);
    		} else {
    			// For other sprites
    			if (s.getVelocityX() > 0) { // Moving right
    				s.setX(rightTile * tileWidth - sw);
    			} else if (s.getVelocityX() < 0) { // Moving left
    				s.setX((leftTile + 1) * tileWidth);
    			}
    			s.setVelocityX(0);
    		}
    	}
    	
    	// Now check Y axis collision
    	sx = s.getX(); // Update sx in case it was modified
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
    			// In map1 (Flappy Bird), any Y collision is fatal
    			System.out.println("Crashed into a pipe (Y axis)!");
    			handleCrash();
    			return;
    		}
    		// In map2 (platformer), all platform collisions just stop movement, no crash
    		
    		// Stop movement and align sprite to tile
    		if (s.getVelocityY() > 0) { // Falling
    			s.setY(bottomTile * tileHeight - sh);
    			if (s == player && currentLevel == 2) {
    				onGround = true; // Player landed on platform
    			}
    		} else if (s.getVelocityY() < 0) { // Jumping up - hit ceiling
    			s.setY((topTile + 1) * tileHeight);
    		}
    		s.setVelocityY(0);
    	} else if (s == player && currentLevel == 2) {
    		// Not colliding with anything vertically - player is in air
    		onGround = false;
    	}
    }


	public void keyReleased(KeyEvent e) { 

		int key = e.getKeyCode();

		switch (key)
		{
			case KeyEvent.VK_ESCAPE : stop(); break;
			case KeyEvent.VK_UP     : flap = false; break;
			case KeyEvent.VK_W      : flap = false; break;
			case KeyEvent.VK_LEFT   : moveLeft = false; break;
			case KeyEvent.VK_A      : moveLeft = false; break;
			case KeyEvent.VK_RIGHT  : moveRight = false; break;
			case KeyEvent.VK_D      : moveRight = false; break;
			case KeyEvent.VK_SPACE  : flap = false; break;
			default :  break;
		}
	}

	/**
	 * Mouse listener implementation for mouse clicks
	 * Left click = flap (same as UP arrow)
	 */
	@Override
	public void mousePressed(MouseEvent e) {
		if (!inMenu) flap = true;
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (!inMenu) flap = false;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// Check if game is over - restart on click
		if (gameOver) {
			initialiseGame();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// Not used
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// Not used
	}
}

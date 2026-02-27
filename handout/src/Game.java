
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.io.File;
import javax.sound.sampled.*;
import javax.sound.midi.*;

import game2D.*;

// Basic flappy bird game with two levels


@SuppressWarnings("serial")


public class Game extends GameCore implements MouseListener 
{
	static int screenWidth = 640;
	static int screenHeight = 480;

    float lift = 0.005f;
    float gravity = 0.0002f;
    float gravityLevel2 = 0.0008f;
    float fly = -0.06f;
    float flyLevel2 = -0.12f;
    float moveSpeed = 0.06f;
    float moveSpeedLevel2 = 0.12f;
    
    boolean flap = false;
    boolean debug = false;
    boolean gameOver = false;
    boolean inMenu = true;
    int currentLevel = 1;

    Animation landing, walk, penguin;
    Image level2Background;
    
    Sprite player = null;
    ArrayList<Sprite> clouds = new ArrayList<Sprite>();
    ArrayList<Sprite> walkers = new ArrayList<Sprite>();
    ArrayList<Float> walkerLeftBounds = new ArrayList<Float>();
    ArrayList<Float> walkerRightBounds = new ArrayList<Float>();
    ArrayList<Sprite> fallers = new ArrayList<Sprite>();
    ArrayList<Tile> collidedTiles = new ArrayList<Tile>();

    TileMap tmap = new TileMap();
    
    long total;
    int lives = 3;
    long fallerTimer = 0;
    long fallerInterval = 4000;
    int lastPenguinSpawnCol = -99;

    Clip slowLoopClip = null;
    Sequencer midiSequencer = null;

    String basePath = "";

    int generatedUpToColumn = 0;
    int nextPipeColumn = 0;
    int lastGapTop = -1;
    static final int PIPE_SPACING = 7;
    static final int GENERATE_AHEAD = 30;


    public static void main(String[] args) {
        Game gct = new Game();
        gct.init();
        gct.run(false, screenWidth, screenHeight);
    }

    public void init() {
        Sprite s;

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

        landing = new Animation();
        landing.loadAnimationFromSheet(basePath + "images/landbird.png", 4, 1, 60);

        walk = new Animation();
        walk.loadAnimationSeries(basePath + "images/walk.png", 3, 4, 100, 6, 5);
        
        penguin = new Animation();
        penguin.loadAnimationFromSheet(basePath + "images/penguin.png", 4, 1, 1000);

        player = new Sprite(landing);
        
        level2Background = loadImage(basePath + "images/beautiful-sky-sunset-sun-clouds-landscape-nature-background.jpg");
        
        // load different cloud images
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
        
        int numClouds = 50;
        for (int c = 0; c < numClouds; c++) {
        	s = new Sprite(cloudAnims[(int)(Math.random() * cloudAnims.length)]);
        	s.setX((int)(Math.random() * screenWidth * 3));
        	s.setY((int)(Math.random() * (screenHeight / 2)));
        	float cloudSpeed = -(0.008f + (float)(Math.random() * 0.06f));
        	s.setVelocityX(cloudSpeed);
        	s.show();
        	clouds.add(s);
        }
        
        slowLoopClip = loadClip(basePath + "sounds/slow.wav");
        midiSequencer = loadMidi(basePath + "sounds/Wii Channels - Mii Channel.mid");

        initialiseGame();
        System.out.println(tmap);
    }

    public void spawnWalkersBetweenPillars() {
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
    				
    				// Use penguin.png for level 2, walk.png for level 1
    				Sprite walker;
    				if (currentLevel == 2) {
    					walker = new Sprite(penguin);
    				} else {
    					int walkerSpeed = 150;
    					Animation walkerAnim = new Animation();
    					walkerAnim.loadAnimationSeries(basePath + "images/walk.png", 3, 4, walkerSpeed, 6, 5);
    					walker = new Sprite(walkerAnim);
    				}
    				
    				// Store the left and right pixel bounds for the walker's patrol area
    				float leftBound = (gapStart + 1) * tw;
    				float rightBound = (gapEnd - 1) * tw;
    				float px = (gapStart + gapWidth / 2.0f) * tw;
    				float py = mapH * th - walker.getHeight(); // Position so walker stands on bottom border
    				walker.setPosition(px, py);
    				float walkerVelocity = (currentLevel == 2) ? 0.025f : 0.01f;
    				walker.setVelocityX(walkerVelocity); // Movement speed
    				walker.show();
    				walkers.add(walker);
    				walkerLeftBounds.add(leftBound);
    				walkerRightBounds.add(rightBound);
    			}
    			gapStart = -1;
    		}
    	}
    }
    
    public void spawnFallingPenguin() {
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
    	
		// Look for pipe columns ahead of the player
    	ArrayList<Integer> pipeColumns = new ArrayList<Integer>();
    	for (int col = playerCol + 15; col < playerCol + 25; col++)
    	{
    		if (col >= tmap.getMapWidth()) break;
    		
			// Check if this column contains any pipe tiles
			boolean hasPipe = false;
			for (int row = 0; row < mapH; row++)
			{
				char tile = tmap.getTileChar(col, row);
				if (tile != '.')
				{
					hasPipe = true;
					break;
				}
			}
		
			if (hasPipe)
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
     * Procedurally generates pipe columns ahead of the player so the game
     * never runs out of pipes. Expands the tile map as needed.
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
    
    public void generatePipeColumn(int col, int gapTop, int gapSize) {
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
    
    public void spawnWalkerInGap(int leftPipeCol, int rightPipeCol) {
    	int tw = tmap.getTileWidth();
    	int th = tmap.getTileHeight();
    	int mapH = tmap.getMapHeight();
    	
    	int gapCols = rightPipeCol - leftPipeCol - 1;
    	if (gapCols < 3) return;
    	
    	// Use penguin.png for level 2, walk.png for level 1
    	Sprite walker;
    	if (currentLevel == 2) {
    		walker = new Sprite(penguin);
    	} else {
    		int walkerSpeed = 150;
    		Animation walkerAnim = new Animation();
    		walkerAnim.loadAnimationSeries(basePath + "images/walk.png", 3, 4, walkerSpeed, 6, 5);
    		walker = new Sprite(walkerAnim);
    	}
    	
    	float leftBound = (leftPipeCol + 1) * tw;
    	float rightBound = rightPipeCol * tw;
    	float px = ((leftPipeCol + rightPipeCol) / 2.0f) * tw;
    	float py = mapH * th - walker.getHeight(); // Position so walker stands on bottom border
    	
    	walker.setPosition(px, py);
    	float walkerVelocity = (currentLevel == 2) ? 0.025f : 0.01f;
    	walker.setVelocityX(walkerVelocity);
    	walker.show();
    	walkers.add(walker);
    	walkerLeftBounds.add(leftBound);
    	walkerRightBounds.add(rightBound);
    }
    
    public void cleanUpBehindPlayer() {
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
    		System.err.println("Failed to load clip: " + filename + " - " + e);
    		return null;
    	}
    }
    
    public Sequencer loadMidi(String filename) {
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

    public void startSlowLoop() {
    	if (slowLoopClip != null) {
    		slowLoopClip.setFramePosition(0);
    		slowLoopClip.loop(Clip.LOOP_CONTINUOUSLY);
    	}
    }

    public void stopSlowLoop() {
    	if (slowLoopClip != null) {
    		slowLoopClip.stop();
    	}
    }

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

    public void initialiseGame() {
    	total = 0;
    	lives = 3;
    	gameOver = false;
    	fallerTimer = 0;
    	
    	// Load the map and set up infinite mode
    	tmap.loadMap(basePath + "maps", "map.txt");
    	generatedUpToColumn = tmap.getMapWidth() - 1;
		nextPipeColumn = generatedUpToColumn + PIPE_SPACING;
    	lastGapTop = -1;
    	// Spawn walkers for infinite map
    	spawnWalkersBetweenPillars();
		// Pre-generate pipes beyond the initial map so the run feels continuous
		generatePipesAhead();
    	      
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

        // Draw background based on level
        if (currentLevel == 2 && level2Background != null) {
        	// Draw Level 2 background image (scaled to fit)
        	g.drawImage(level2Background, 0, 0, getWidth(), getHeight(), null);
        } else {
        	// Draw Level 1 sky blue background
        	g.setColor(new Color(207, 236, 247));
        	g.fillRect(0, 0, getWidth(), getHeight());
        }
        

        
        // Draw clouds independently on screen (no world offset, pure background)
        for (Sprite s: clouds)
        {
        	s.setOffsets(0, 0);  // Clouds are in screen space, not world space
        	s.draw(g);
        }
        
        // Apply orange/reddish tint to clouds in Level 2 for sunset vibe
        if (currentLevel == 2) {
        	Composite originalComposite = g.getComposite();
        	g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.4f));
        	g.setColor(new Color(255, 120, 60)); // Orange/reddish color
        	g.fillRect(0, 0, getWidth(), getHeight()); // Tint entire window for sunset vibe
        	g.setComposite(originalComposite);
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
        
        String msg = String.format("Score: %d", total/100);
        g.setColor(Color.darkGray);
        g.drawString(msg, getWidth() - 150, 50);
        
        String livesMsg = String.format("Lives: %d", lives);
        g.setColor(Color.red);
        g.drawString(livesMsg, getWidth() - 150, 70);
        
        if (gameOver) {
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
        	
        	g.setFont(new Font("Dialog", Font.PLAIN, 12));
        }
        
if (debug) {
        	tmap.drawBorder(g, xo, yo, Color.black);
        	g.setColor(Color.red);
        	player.drawBoundingBox(g);
        	g.drawString(String.format("Player: %.0f,%.0f", player.getX(),player.getY()),
        						getWidth() - 100, 90);
        	drawCollidedTiles(g, tmap, xo, yo);
        }
        
        int borderThick = 3;
        g.setColor(Color.darkGray);
        g.fillRect(0, 0, screenWidth, borderThick);
        int bottomLineY = yo + tmap.getPixelHeight();
        g.fillRect(0, bottomLineY - borderThick, screenWidth, borderThick);

    }
    
    public void drawTileMap(Graphics2D g, int xoff, int yoff) {
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
    			
    			// Apply dark orange tint for pipes in Level 2
    			if (currentLevel == 2) {
    				char tileChar = tmap.getTileChar(c, r);
    				if (tileChar == 'p' || tileChar == 't' || tileChar == 'b') {
    					g.setColor(new Color(204, 85, 0, 120)); // Dark orange with transparency
    					g.fillRect(xc, yc, tmap.getTileWidth(), tmap.getTileHeight());
    				}
    			}
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
	
    public void drawMenu(Graphics2D g) {
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
    	String info1 = "Level 1 Controls: UP/SPACE/CLICK to flap";
    	int info1Width = g.getFontMetrics().stringWidth(info1);
    	g.drawString(info1, (getWidth() - info1Width) / 2, 330);
    	
    	String info2 = "Level 2 Controls: A/D or LEFT/RIGHT to move, W/UP/SPACE to jump";
    	int info2Width = g.getFontMetrics().stringWidth(info2);
    	g.drawString(info2, (getWidth() - info2Width) / 2, 350);
    	
    	String info3 = "B = Debug Mode | ESC = Quit";
    	int info3Width = g.getFontMetrics().stringWidth(info3);
    	g.drawString(info3, (getWidth() - info3Width) / 2, 370);
    	
    	// Reset font
    	g.setFont(new Font("Dialog", Font.PLAIN, 12));
    }
    
    
    public void update(long elapsed) {
    	if (inMenu || gameOver) return;
    	
    	total += elapsed;
    	
        float currentGravity = (currentLevel == 2) ? gravityLevel2 : gravity;
        player.setVelocityY(player.getVelocityY()+(currentGravity*elapsed));
    	    	
       	player.setAnimationSpeed(1.0f);
       	
   		if (flap) {
   			player.setAnimationSpeed(1.8f);
   			if (currentLevel == 2) {
   				player.setVelocityY(flyLevel2);
   			} else {
   				player.setVelocityY(fly);
   			}
   		}
   		
   		if (currentLevel == 2) {
   			player.setVelocityX(moveSpeedLevel2);
   		} else {
   			player.setVelocityX(moveSpeed);
   		}
       		
       	
       	for (Sprite s: clouds) {
       		s.update(elapsed);
       		if (s.getX() + s.getWidth() < 0) {
       			s.setX(screenWidth + (int)(Math.random() * 200));
       			s.setY((int)(Math.random() * (screenHeight / 2)));
       			s.setVelocityX(-(0.008f + (float)(Math.random() * 0.06f)));
       		}
       	}
       	
       	for (int i = 0; i < walkers.size(); i++) {
       		Sprite w = walkers.get(i);
       		float leftBound = walkerLeftBounds.get(i);
       		float rightBound = walkerRightBounds.get(i);
       		
       		w.update(elapsed);
       		
       		float walkerSpeed = 0.01f;
       		if (w.getX() <= leftBound) {
       			w.setX(leftBound);
       			w.setVelocityX(walkerSpeed);
       		} else if (w.getX() + w.getWidth() >= rightBound) {
       			w.setX(rightBound - w.getWidth());
       			w.setVelocityX(-walkerSpeed);
       		}
       		
       		if (boundingBoxCollision(player, w)) {
       			handleCrash();
       			return;
       		}
       	}
       	
       	for (int i = fallers.size() - 1; i >= 0; i--) {
       		Sprite f = fallers.get(i);
    		f.update(elapsed);
    		
    		if (f.getY() > tmap.getPixelHeight()) {
    			fallers.remove(i);
    			continue;
    		}
    		
    		if (boundingBoxCollision(player, f)) {
    			float playerBottom = player.getY() + player.getHeight();
    			float penguinTop = f.getY();
    			float penguinMiddle = f.getY() + f.getHeight() / 2;
    			
    			if (player.getVelocityY() > 0 && playerBottom < penguinMiddle) {
    				fallers.remove(i);
    				player.setVelocityY(-0.1f);
    				continue;
    			} else {
    				handleCrash();
    				return;
    			}
    		}
    	}
    	
float oldX = player.getX();
    	float oldY = player.getY();
    	player.update(elapsed);
		generatePipesAhead();
		cleanUpBehindPlayer();
		handleScreenEdge(player, tmap, elapsed);
		checkTileCollision(player, tmap, oldX, oldY);
    }

public void handleScreenEdge(Sprite s, TileMap tmap, long elapsed) {
	if (s.getY() + s.getHeight() - tmap.getPixelHeight() > 0) {
		if (s == player && currentLevel == 2) {
			handleCrash();
			return;
		}
		s.setY(tmap.getPixelHeight() - s.getHeight());
		s.setVelocityY(0);
	}
	
	if (s.getY() < 0 && s == player) {
		handleCrash();
	}
}
    
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
				if (gameOver) { inMenu = true; gameOver = false; stopSlowLoop(); stopTheme(); } 
				break;
			case KeyEvent.VK_UP: 
				if (!inMenu) flap = true; 
				break;
			case KeyEvent.VK_W: 
				if (!inMenu) flap = true; 
				break;
			case KeyEvent.VK_SPACE: 
				if (gameOver) { initialiseGame(); } else if (!inMenu) { flap = true; } 
				break;
			case KeyEvent.VK_S: 
				if (!inMenu) { Sound s = new Sound(basePath + "sounds/caw.wav"); s.start(); } 
				break;
			case KeyEvent.VK_ESCAPE: 
				stop(); 
				break;
			case KeyEvent.VK_B: 
				debug = !debug; 
				break;
		}
    }

    public boolean boundingBoxCollision(Sprite s1, Sprite s2) {
    	return ((s1.getX() + s1.getWidth()) > s2.getX() &&
    			(s1.getX() < (s2.getX() + s2.getWidth())) &&
    			(s1.getY() + s1.getHeight()) > s2.getY() &&
    			(s1.getY() < (s2.getY() + s2.getHeight())));
    }
    
    public void checkTileCollision(Sprite s, TileMap tmap, float oldX, float oldY) {
    	collidedTiles.clear();
    	
    	float sx = s.getX();
    	float sy = s.getY();
    	float sw = s.getWidth();
    	float sh = s.getHeight();
    	
    	float tileWidth = tmap.getTileWidth();
    	float tileHeight = tmap.getTileHeight();
    	
    	s.setY(oldY);
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
    		if (s == player) {
    			handleCrash();
    			return;
    		} else {
    			if (s.getVelocityX() > 0) {
    				s.setX(rightTile * tileWidth - sw);
    			} else if (s.getVelocityX() < 0) {
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
    		if (s == player) {
    			handleCrash();
    			return;
    		}
    		
    		if (s.getVelocityY() > 0) {
    			s.setY(bottomTile * tileHeight - sh);
    		} else if (s.getVelocityY() < 0) {
    			s.setY((topTile + 1) * tileHeight);
    		}
    		s.setVelocityY(0);
    	}
    }


	public void keyReleased(KeyEvent e) {
		int key = e.getKeyCode();
		switch (key) {
			case KeyEvent.VK_ESCAPE: stop(); break;
			case KeyEvent.VK_UP: flap = false; break;
			case KeyEvent.VK_W: flap = false; break;
			case KeyEvent.VK_SPACE: flap = false; break;
		}
	}

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
		if (gameOver) {
			initialiseGame();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}
}

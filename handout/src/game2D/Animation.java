package game2D;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class Animation {

    private ArrayList<AnimFrame> frames;
    private int currFrameIndex;
    private long animTime;
    private long totalDuration;
    private float animSpeed = 1.0f;
    
    private boolean loop = true;
    private boolean looped = false;
    private boolean play = true;
    private int stopFrame = -1;

    public Animation() {
        frames = new ArrayList<AnimFrame>();
        totalDuration = 0;
        looped = false;
        start();
    }
    
    public synchronized void addFrame(Image image, long duration) {
        totalDuration += duration;
        frames.add(new AnimFrame(image, totalDuration));
    }


    public synchronized void start() {
        animTime = 0;
        currFrameIndex = 0;
        looped = false;
    }

    public synchronized void update(long elapsedTime) {
    	if (!play) return;
    	
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
            
            if (currFrameIndex == stopFrame) {
            	play = false;
            	stopFrame = -1;
            }
        }
    }


    public synchronized Image getImage() {
        if (frames.size() == 0) {
            return null;
        } else {
            return getFrame(currFrameIndex).image;
        }
    }

    private AnimFrame getFrame(int i) {
        return frames.get(i);
    }

    public Image getFrameImage(int i) {
    	if (i < 0 || i >= frames.size()) return null;
    	AnimFrame frame = frames.get(i);
    	return frame.image;
    }
    
    public void setLoop(boolean shouldLoop) {
    	loop = shouldLoop;
    }
    
    public boolean hasLooped() { 
    	return looped; 
    }
    
    public void loadAnimationFromSheet(String fileName, int columns, int rows, int frameDuration) {
    	Image sheet = new ImageIcon(fileName).getImage();
    	Image[] images = getImagesFromSheet(sheet, columns, rows);
    	
    	for (int i=0; i<images.length; i++)
    	{
    		// addFrame(images[i], frameDuration); This causes flashing on load.
    		addFrame(new ImageIcon(images[i]).getImage(), frameDuration);
    	}
    }
    
    private Image[] getImagesFromSheet(Image sheet, int columns, int rows) {

        Image[] split = new Image[rows*columns];
        int count = 0;
        int width = sheet.getWidth(null)/columns;
        int height = sheet.getHeight(null)/rows;

        for(int i = 0; i < rows; i++) {
            for(int j = 0; j < columns; j++) {
            	ImageFilter cropper = new CropImageFilter(j*width,i*height, width, height);
                FilteredImageSource cropped = new FilteredImageSource(sheet.getSource(), cropper);
                split[count] = Toolkit.getDefaultToolkit().createImage(cropped);
                count++;
            }
        }
        return split;
    }

    public void loadAnimationSeries(String fileName, int columns, int rows, long frameDuration, int from, int numFrames) {
    	ArrayList<Integer> frameIndex = new ArrayList<>();
    	for (int i = from; i<from+numFrames; i++) frameIndex.add(i);
    	ArrayList<BufferedImage> imagesList = loadSubsetFromSpriteSheet(fileName, columns, rows, frameIndex);
    	
    	for (BufferedImage img : imagesList) addFrame(img,frameDuration);
    }
    
    private ArrayList<BufferedImage> loadSubsetFromSpriteSheet(
        String spritesheetPath, 
        int framesPerRow, 
        int framesPerColumn, 
        ArrayList<Integer> frames) {

        ArrayList<BufferedImage> imagesList = new ArrayList<>();
    	try {
	        BufferedImage spritesheet = ImageIO.read(new File(spritesheetPath));
	        int frameWidth = spritesheet.getWidth() / framesPerRow;
	        int frameHeight = spritesheet.getHeight() / framesPerColumn;
	        
	        for (Integer frameIndex : frames) {
	            int row = (frameIndex % framesPerRow);
	            int col = (int)(frameIndex / framesPerRow);
	            int x = row * frameWidth;
	            int y = col * frameHeight;
	            
	            if (x + frameWidth > spritesheet.getWidth() || y + frameHeight > spritesheet.getHeight()) continue;
	            BufferedImage image = spritesheet.getSubimage(x, y, frameWidth, frameHeight);
	            imagesList.add(image);
	        }
    	} catch (IOException e) {
    		System.err.printf("Failed to read from sprite sheet file: %s",e);
    	}
        return imagesList;
    }
    
    public void pause() {
    	play = false;
    }
    
    public void pauseAt(int frame) {
    	if ((frame < 0) || (frame >= frames.size())) 
    		stopFrame = 0;
    	else
    		stopFrame = frame; 
    }
    
    public void play() {
    	play = true;
    }
    
    public void setAnimationSpeed(float rate) {
    	animSpeed = rate;
    }
    
    public void setAnimationFrame(int f) {
    	if (f < 0 || f >= frames.size()) return;
    	currFrameIndex = f;
    }
    
    private class AnimFrame {
        Image image;
        long endTime;

        public AnimFrame(Image image, long endTime) {
            this.image = image;
            this.endTime = endTime;
        }
    }
}

package game2D;

public class Tile {

	private char character=' ';
	private int xc=0;
	private int yc=0;
 	
	public Tile(char c, int x, int y) {
		character = c;
		xc = x;
		yc = y;
	}

	public char getCharacter() {
		return character;
	}

	public void setCharacter(char character) {
		this.character = character;
	}

	public int getXC() {
		return xc;
	}

	public int getYC() {
		return yc;
	}
}

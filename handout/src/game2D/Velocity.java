package game2D;

public class Velocity {

	private	double angle;
	private double dangle;
	private double speed;
	private double dx;
	private double dy;

	public Velocity() {
		dx = 0.0f;
		dy = 0.0f;
		speed = 0.0f;
		angle = 0.0f;
		dangle = 0.0f;
	}

	public Velocity(double s, double a) {
		dx = 0.0f;
		dy = 0.0f;
		speed = s;
		dangle = a;
		reCalc();
	}

	private void reCalc() {
		angle = Math.toRadians(dangle);
		dx = speed * Math.cos(angle);
		dy = speed * Math.sin(angle);
	}

	public void setVelocity(double s, double a) {
		speed = s;
		dangle = a;
		angle = Math.toRadians(a);
		reCalc();
	}

	public void setAngle(double a) {
		dangle = a;
		reCalc();
	}

	public void setSpeed(double s) {
		speed = s;
		reCalc();
	}

	public double getAngle() { return dangle; }
	public double getSpeed() { return speed; }

	public void add(Velocity v) {
		dx += v.dx;
		dy += v.dy;

		speed = Math.sqrt((dx*dx) + (dy*dy));
		angle = Math.acos(dx/speed);
	}

	public void subtract(Velocity v) {
		dx -= v.dx;
		dy -= v.dy;

		speed = Math.sqrt((dx*dx) + (dy*dy));
		angle = Math.acos(dx/speed);
	}

	public double getdx() { return dx; }
	public double getdy() { return dy; }
}

package debugging;

/**
 * Event
 *
 * @author Michael Mrozek
 *         Created Dec 19, 2010.
 */
public class Event {
	private Level level;
	private String className;
	private String methodName;
	private int line;
	private String text;
	
	public Event(Level level, String className, String methodName, int line, String text) {
		this.level = level;
		this.className = className;
		this.methodName = methodName;
		this.line = line;
		this.text = text;
	}
	
	public Event(Level level, String text) {this(level, null, null, 0, text);}
	
	public Level getLevel() {return this.level;}
	public String getClassName() {return this.className;}
	public String getMethodName() {return this.methodName;}
	public int getLine() {return this.line;}
	public String getText() {return this.text;}
	
	@Override public String toString() {
		return "[" + Character.toUpperCase(this.getLevel().toString().charAt(0)) + "] " + this.getClassName() + "(" + this.getMethodName() + ":" + this.getLine() + "): " + this.getText();
	}
}

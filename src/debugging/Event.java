package debugging;

import java.time.LocalDateTime;

/**
 * Event
 *
 * @author Michael Mrozek
 *         Created Dec 19, 2010.
 */
public class Event {
	private Level level;
	private LocalDateTime timestamp;
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
		
		this.timestamp = LocalDateTime.now();
	}
	
	public Event(Level level, String text) {this(level, null, null, 0, text);}
	
	public Level getLevel() {return this.level;}
	public LocalDateTime getTimestamp() {return this.timestamp;}
	public String getClassName() {return this.className;}
	public String getMethodName() {return this.methodName;}
	public int getLine() {return this.line;}
	public String getText() {return this.text;}
	
	@Override public String toString() {
		final String ts = String.format("%02d:%02d:%02d", this.getTimestamp().getHour(), this.getTimestamp().getMinute(), this.getTimestamp().getSecond());
		return "[" + Character.toUpperCase(this.getLevel().toString().charAt(0)) + "] " + ts + " " + this.getClassName() + "(" + this.getMethodName() + ":" + this.getLine() + "): " + this.getText();
	}
}

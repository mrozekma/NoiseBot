package main;

/**
 * Message
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Message {
	private String message;
	private String sender;
	private boolean pm;

	public Message(String message, String sender, boolean pm) {
		this.message = message;
		this.sender = sender;
		this.pm = pm;
	}

	public String getMessage() {return this.message;}
	public String getSender() {return this.sender;}
	public boolean isPM() {return this.pm;}

	@Override public String toString() {return "<" + this.sender + "> " + this.message;}
}

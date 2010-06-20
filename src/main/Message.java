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
	
	public Message(String message, String sender) {
		this.message = message;
		this.sender = sender;
	}
	
	public String getMessage() {return this.message;}
	public String getSender() {return this.sender;}
	
	@Override public String toString() {return "<" + this.sender + "> " + this.message;}
}

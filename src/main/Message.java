package main;

/**
 * Message
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Message {
	private transient NoiseBot bot = null; // a deserialized Message will have a null 'bot'
	private String message;
	private String sender;
	private boolean pm;

	public Message(NoiseBot bot, String message, String sender, boolean pm) {
		this.bot = bot;
		this.message = message;
		this.sender = sender;
		this.pm = pm;
	}

	public String getMessage() {return this.message;}
	public String getSender() {return this.sender;}
	public boolean isPM() {return this.pm;}

	public void respond(String message) {
		if(this.bot == null) {
			throw new RuntimeException("Unable to respond to deserialized messages");
		}
		if(this.pm) {
			this.bot.sendMessage(this.sender, message);
		} else {
			this.bot.sendMessage(message);
		}
	}

	public void respondParts(String separator, String... parts) {
		if(this.bot == null) {
			throw new RuntimeException("Unable to respond to deserialized messages");
		}
		if(this.pm) {
			this.bot.sendTargetedMessageParts(this.sender, separator, parts);
		} else {
			this.bot.sendMessageParts(separator, parts);
		}
	}

	@Override public String toString() {return "<" + this.sender + "> " + this.message;}
}

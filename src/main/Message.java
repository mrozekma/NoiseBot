package main;

/**
 * Message
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Message {
	private final NoiseBot bot;
	private final String message;
	private final String sender;
	private final boolean pm;

	public Message(NoiseBot bot, String message, String sender, boolean pm) {
		this.bot = bot;
		this.message = message;
		this.sender = sender;
		this.pm = pm;
	}

	public String getMessage() {return this.message;}
	public String getSender() {return this.sender;}
	public boolean isPM() {return this.pm;}

	public void respond(String fmt, String... args) {
		this.bot.sendMessageTo(this.pm ? this.sender : this.bot.channel, fmt, args);
	}

	public void respond(Style style, String fmt, String... args) {
		this.bot.sendMessageTo(this.pm ? this.sender : this.bot.channel, style, fmt, args);
	}

	public MessageBuilder buildResponse() {
		return this.bot.buildMessageTo(this.pm ? this.sender : this.bot.channel);
	}

	@Override public String toString() {return "<" + this.sender + "> " + this.message;}
}

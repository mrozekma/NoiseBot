package main;

/**
 * @author Michael Mrozek
 *         Created Feb 3, 2016.
 */
public class CommandContext {
	private final Message message;

	CommandContext(Message message) {
		this.message = message;
	}

	public Message getMessage() {
		return this.message;
	}

	// Shortcuts for getMessage().xxx()
	public String getResponseTarget() {
		return this.message.getResponseTarget();
	}
	public void respond(String fmt, Object... args) {
		this.message.respond(fmt, args);
	}
	public MessageBuilder buildResponse() {
		return this.message.buildResponse();
	}
	public void respondAction(String fmt, Object... args) {
		this.message.respondAction(fmt, args);
	}
	public MessageBuilder buildActionResponse() {
		return this.message.buildActionResponse();
	}
	public void respondReaction(String reaction) {
		this.message.respondReaction(reaction);
	}

	public String getMessageSender() {
		return this.message.getSender();
	}
	public Message deriveMessage(String text) {
		return this.message.deriveNew(text);
	}
}

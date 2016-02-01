package main;

/**
 * @author Michael Mrozek
 *         Created Jan 16, 2016.
 */
public class SentMessage {
	protected final NoiseBot bot;
	protected final String target;
	protected final MessageBuilder.Type type;

	SentMessage(NoiseBot bot, String target, MessageBuilder.Type type) {
		this.bot = bot;
		this.target = target;
		this.type = type;
	}

	@Override public boolean equals(Object o) {
		if(!(o instanceof SentMessage)) {
			return false;
		}
		final SentMessage other = (SentMessage)o;
		return this.bot == other.bot && this.target.equals(other.target) && this.type == other.type;
	}

	public SentMessage[] edit(String fmt, Object... args) {
		return this.buildEdit().add(fmt, args).send();
	}

	public MessageBuilder buildEdit() {
		return new MessageBuilder(this.bot, this.target, this.type, this);
	}
}

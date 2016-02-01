package main;

/**
 * @author Michael Mrozek
 *         Created Jan 19, 2016.
 */
public class SlackSentMessage extends SentMessage {
	private final String timestamp;

	SlackSentMessage(NoiseBot bot, String target, MessageBuilder.Type type, String timestamp) {
		super(bot, target, type);
		this.timestamp = timestamp;
	}

	String getTimestamp() {
		return this.timestamp;
	}

	@Override public boolean equals(Object o) {
		if(!(o instanceof SlackSentMessage)) {
			return false;
		}
		final SlackSentMessage other= (SlackSentMessage)o;
		return super.equals(o) && this.timestamp.equals(other.timestamp);
	}
}

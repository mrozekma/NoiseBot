package main;

import com.mrozekma.taut.TautMessage;

/**
 * @author Michael Mrozek
 *         Created Jan 19, 2016.
 */
public class SlackSentMessage extends SentMessage {
	private final TautMessage tautMessage;

	SlackSentMessage(NoiseBot bot, String target, MessageBuilder.Type type, TautMessage tautMessage) {
		super(bot, target, type);
		this.tautMessage = tautMessage;
	}

	public TautMessage getTautMessage() {
		return this.tautMessage;
	}

	@Override public boolean equals(Object o) {
		if(!(o instanceof SlackSentMessage)) {
			return false;
		}
		final SlackSentMessage other= (SlackSentMessage)o;
		return super.equals(o) && this.tautMessage.equals(other.tautMessage);
	}
}

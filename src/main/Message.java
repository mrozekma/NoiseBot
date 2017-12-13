package main;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

/**
 * Message
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Message {
	private class BufferedBuilder extends MessageBuilder {
		public BufferedBuilder(NoiseBot bot, String target, Type type) {
			super(bot, target, type);
		}

		@Override public SentMessage[] send() {
			if(Message.this.mergeResponses.isPresent()) {
				Message.this.mergeResponses.get().add(this);
				return new SentMessage[0];
			} else {
				return super.send();
			}
		}
	}

	private transient final NoiseBot bot;
	private final String message;
	private final String sender;
	private final String responseTarget;

	private transient Optional<Queue<MessageBuilder>> mergeResponses = Optional.empty();

	public Message(NoiseBot bot, String message, String sender, String responseTarget) {
		this.bot = bot;
		this.message = message;
		this.sender = sender;
		this.responseTarget = responseTarget;
	}

	public String getMessage() {return this.message;}
	public String getSender() {return this.sender;}
	public String getResponseTarget() {return this.responseTarget;}
	public boolean isPM() {return this.responseTarget.charAt(0) != '#';}

	public Message deriveNew(String message) {
		return new Message(this.bot, message, this.sender, this.responseTarget);
	}

	////////////////////////////////////////////////////////////////////////////////

	public void mergeResponses() {
		this.mergeResponses = Optional.of(new LinkedList<>());
	}

	SentMessage[] flushResponses() {
		final Queue<MessageBuilder> q = this.mergeResponses.orElseThrow(() -> new IllegalStateException("Message is not buffering responses"));
		try {
			return this.bot.sendMessageBuilders(q.toArray(new MessageBuilder[0]));
		} finally {
			q.clear();
		}
	}

	public void respond(String fmt, Object... args) {
		this.buildResponse().add(fmt, args).send();
	}

	public MessageBuilder buildResponse() {
		return this.new BufferedBuilder(this.bot, this.responseTarget, MessageBuilder.Type.MESSAGE);
	}

	public void respondAction(String fmt, Object... args) {
		this.buildActionResponse().add(fmt, args).send();
	}

	public MessageBuilder buildActionResponse() {
		return this.new BufferedBuilder(this.bot, this.responseTarget, MessageBuilder.Type.ACTION);
	}

	public void respondReaction(String reaction) {
		throw new UnsupportedOperationException("This message cannot be reacted to");
	}

	@Override public String toString() {return "<" + this.sender + "> " + this.message;}
}

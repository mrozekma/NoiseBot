package main;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * @author Michael Mrozek
 *         Created Feb 3, 2016.
 */
public class ViewContext {
	private final Message message;
	private final Optional<Method> commandMethod;

	ViewContext(Message message) {
		this.message = message;
		this.commandMethod = Optional.empty();
	}

	ViewContext(Message message, Method commandMethod) {
		this.message = message;
		this.commandMethod = Optional.of(commandMethod);
	}

	public Message getMessage() {
		return this.message;
	}

	public boolean wasCommandTriggered() {
		return this.commandMethod.isPresent();
	}

	public Method getCommandMethod() {
		return this.commandMethod.get();
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

	public String getMessageSender() {
		return this.message.getSender();
	}
	public Message deriveMessage(String text) {
		return this.message.deriveNew(text);
	}
}

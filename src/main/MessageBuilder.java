package main;

import debugging.Log;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Michael Mrozek
 *         Created Jan 2, 2016.
 */
public class MessageBuilder {
	enum Type {MESSAGE, ACTION, NOTICE}

	private interface Message {}

	private static class SingleMessage implements Message {
		final String message;
		SingleMessage(String message) {
			this.message = message;
		}
	}

	private static class MultipartMessage implements Message {
		final String separator;
		final String[] parts;
		MultipartMessage(String separator, String[] parts) {
			this.separator = separator;
			this.parts = parts;
		}
	}

	private static class BadFormatException extends RuntimeException {
		final int pos;
		final String baseMessage;
		BadFormatException(int pos, String message) {
			super(String.format("Bad format at offset %d: %s", pos, message));
			this.pos = pos;
			this.baseMessage = message;
		}
		// Only used at outermost scope (add(), not addHelper())
		BadFormatException(RuntimeException cause) {
			super(cause);
			this.pos = -1;
			this.baseMessage = null;
		}
	}

	private final List<Message> messages;
	private final NoiseBot bot;
	public final String target;
	public final Type type;

	public MessageBuilder(NoiseBot bot, String target, Type type) {
		this.messages = new LinkedList<>();
		this.bot = bot;
		this.target = target;
		this.type = type;
	}


	public MessageBuilder add(String fmt, Object[] args) {
		Log.d("Formatting: %s", fmt);
		final Queue<Object> argQ = new LinkedList<>(Arrays.asList(args));
		try {
			this.addHelper(fmt, argQ, Style.PLAIN, false);
		} catch(BadFormatException e) {
			Log.e(e);
			throw e;
		} catch(RuntimeException e) {
			Log.e(e);
			throw new BadFormatException(e);
		}
		return this;
	}

	private String addHelper(String fmt, Queue<Object> args, Style style, boolean isMultipart) {
		final StringBuilder pending = new StringBuilder(), rtn = (isMultipart ? new StringBuilder() : null);
		final Consumer<Style> flush = (thisStyle) -> {
			if(pending.length() > 0) {
				final String s = bot.format(thisStyle, pending.toString());
				if(isMultipart) {
					rtn.append(s);
				} else {
					messages.add(new SingleMessage(s));
				}
				pending.setLength(0);
			}
		};

		int end = fmt.length();
		for(int i = 0; i < end;) {
			switch(fmt.charAt(i)) {
			case '#':
				if(i + 1 == end) {
					throw new BadFormatException(i, "Dangling `#'");
				}
				switch(fmt.charAt(i + 1)) {
				case '#': // Escaped '#'
					pending.append('#');
					i += 2;
					break;
				case '(': // Multipart group
					if(isMultipart) {
						throw new BadFormatException(i, "Can't nest multipart groups");
					}
					flush.accept(style);
					final int groupStart;
					final String separator;
					if(fmt.charAt(i + 2) == '[') { // Custom separator
						int sepEnd = fmt.indexOf("]", i + 3);
						if(sepEnd == -1) {
							throw new BadFormatException(i + 2, "Unclosed multipart group separator");
						}
						separator = fmt.substring(i + 3, sepEnd);
						while(fmt.charAt(++sepEnd) == ' ');
						groupStart = sepEnd;
					} else {
						separator = " ";
						groupStart = i + 2;
					}
					final int groupEnd = fmt.indexOf(")", groupStart);
					if(groupEnd == -1) {
						throw new BadFormatException(groupStart, "Unclosed multipart group");
					}
					final String partFmt = fmt.substring(groupStart, groupEnd);
					final Queue<Object> partArgs = new LinkedList<>(Arrays.asList((Object[])args.remove()));
					final List<String> parts = new LinkedList<>();
					while(!partArgs.isEmpty()) {
						try {
							parts.add(this.addHelper(partFmt, partArgs, style, true));
						} catch(BadFormatException e) {
							throw new BadFormatException(groupStart + e.pos, e.baseMessage);
						}
					}
					this.messages.add(new MultipartMessage(this.bot.format(style, separator), parts.toArray(new String[0])));
					for(i = groupEnd + 1; i < end && fmt.charAt(i) == ' '; i++);
					break;
				default:
					int sp = fmt.indexOf(" ", i + 1);
					if(sp == -1) {
						// This style apparently appears at the end of the format specifier, so it's useless
						sp = end;
					}
					flush.accept(style);
					final String modName = fmt.substring(i + 1, sp);
					try {
						style = style.update(modName);
					} catch(NoSuchElementException e) {
						throw new BadFormatException(i + 1, String.format("Unrecognized style: %s", modName));
					}
					for(i = sp + 1; i < end && fmt.charAt(i) == ' '; i++);
					break;
				}
				break;
			case '%': // Format specifier
				// Search for the end of the format specifier (the first alpha character -- this may or may not actually be right)
				int specEnd;
				for(specEnd = i + 1; specEnd < end && !Character.isAlphabetic(fmt.charAt(specEnd)); specEnd++);
				if(specEnd == end) {
					throw new BadFormatException(i, "Dangling format specifier");
				}
				specEnd++;
				pending.append(String.format(fmt.substring(i, specEnd), args.remove()));
				i = specEnd;
				break;
			default:
				pending.append(fmt.charAt(i++));
			}
		}
		flush.accept(style);

		return (rtn == null) ? null : rtn.toString();
	}

	public void send() {
		this.bot.sendMessage(this);
	}

	public String[] getFinalMessages(Optional<Integer> maxMessageLen) {
		final List<String> rtn = new LinkedList<>();
		final StringBuilder str = new StringBuilder();
		for(ListIterator<Message> iter = this.messages.listIterator(); iter.hasNext();) {
			final Message message = iter.next();
			if(message instanceof SingleMessage) {
				final SingleMessage smessage = (SingleMessage)message;
				if(maxMessageLen.isPresent()) {
					final int remaining = maxMessageLen.get() - str.length();
					// For single-part messages, if they're too long we just split wherever
					String text = smessage.message;
					if(text.length() > remaining) {
						String excess = text.substring(remaining);
						text = text.substring(0, remaining);
						str.append(text);

						// Send the string so far
						rtn.add(str.toString());
						str.setLength(0);

						// Queue the excess up to be processed next iteration
						iter.add(new SingleMessage(excess));
						iter.previous();
					} else {
						str.append(text);
					}
				} else {
					str.append(smessage.message);
				}
			} else if(message instanceof MultipartMessage) {
				final MultipartMessage mpmessage = (MultipartMessage)message;
				if(maxMessageLen.isPresent()) {
					if(mpmessage.separator.length() > maxMessageLen.get()) {
						throw new IllegalArgumentException("Separator is too large");
					}
					String separator = "";
					for(int i = 0; i < mpmessage.parts.length; i++) {
						String part = mpmessage.parts[i];
						int remaining = maxMessageLen.get() - str.length();
						// For multi-part messages, try to split between parts if possible
						if(separator.length() + part.length() > remaining) {
							if(str.length() == 0) {
								// This one part is too large for a single message, so we have to split it up
								str.append(separator);
								remaining -= separator.length();

								while(part.length() > remaining) {
									str.append(part.substring(0, remaining));
									part = part.substring(remaining);
									rtn.add(str.toString());
									str.setLength(0);
									remaining = maxMessageLen.get();
								}

								// There might be remaining data in 'part'
								if(part.isEmpty()) {
									continue;
								} else {
									str.append(part);
								}
							} else {
								// Redo this part next pass, after flushing 'str'
								i--;
							}
							rtn.add(str.toString());
							str.setLength(0);
							separator = "";
						} else {
							str.append(separator).append(part);
							separator = mpmessage.separator;
						}
					}
				} else {
					str.append(Arrays.stream(mpmessage.parts).collect(Collectors.joining(mpmessage.separator)));
				}
			} else {
				throw new RuntimeException("Unexpected Message subclass: " + message.getClass());
			}
		}
		if(str.length() > 0) {
			rtn.add(str.toString());
		}

		return rtn.toArray(new String[0]);
	}
}

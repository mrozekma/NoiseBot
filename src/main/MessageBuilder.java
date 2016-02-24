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

	private interface Message {
		String[] finalize(StringBuilder cur, ListIterator<Message> iter, Optional<Integer> maxMessageLen);
	}

	private static class SingleMessage implements Message {
		final String message;
		SingleMessage(String message) {
			this.message = message;
		}

		@Override public String[] finalize(StringBuilder cur, ListIterator<Message> iter, Optional<Integer> maxMessageLen) {
			if(!maxMessageLen.isPresent()) {
				cur.append(this.message);
				return new String[0];
			}

			// For single-part messages, if they're too long we just split wherever
			final int remaining = maxMessageLen.get() - cur.length();
			String text = this.message;
			if(text.length() > remaining) {
				String excess = text.substring(remaining);
				text = text.substring(0, remaining);
				cur.append(text);

				// Send the string so far
				final String[] rtn = {cur.toString()};
				cur.setLength(0);

				// Queue the excess up to be processed next iteration
				iter.add(new SingleMessage(excess));
				iter.previous();

				return rtn;
			} else {
				cur.append(text);
				return new String[0];
			}
		}
	}

	private static class MultipartMessage implements Message {
		final String separator;
		final String[] parts;
		MultipartMessage(String separator, String[] parts) {
			this.separator = separator;
			this.parts = parts;
		}

		@Override public String[] finalize(StringBuilder cur, ListIterator<Message> iter, Optional<Integer> maxMessageLen) {
			if(!maxMessageLen.isPresent()) {
				cur.append(Arrays.stream(this.parts).collect(Collectors.joining(this.separator)));
				return new String[0];
			}

			if(this.separator.length() > maxMessageLen.get()) {
				throw new IllegalArgumentException("Separator is too large");
			}
			String separator = "";
			final List<String> rtn = new LinkedList<>();
			for(int i = 0; i < this.parts.length; i++) {
				String part = this.parts[i];
				int remaining = maxMessageLen.get() - cur.length();
				// For multi-part messages, try to split between parts if possible
				if(separator.length() + part.length() > remaining) {
					if(cur.length() == 0) {
						// This one part is too large for a single message, so we have to split it up
						cur.append(separator);
						remaining -= separator.length();

						while(part.length() > remaining) {
							cur.append(part.substring(0, remaining));
							part = part.substring(remaining);
							rtn.add(cur.toString());
							cur.setLength(0);
							remaining = maxMessageLen.get();
						}

						// There might be remaining data in 'part'
						if(part.isEmpty()) {
							continue;
						} else {
							cur.append(part);
						}
					} else {
						// Redo this part next pass, after flushing 'str'
						i--;
					}
					rtn.add(cur.toString());
					cur.setLength(0);
					separator = "";
				} else {
					cur.append(separator).append(part);
					separator = this.separator;
				}
			}

			return rtn.toArray(new String[0]);
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
		BadFormatException(int pos, String message, RuntimeException cause) {
			super(String.format("Bad format at offset %d: %s", pos, message), cause);
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

	public static char BULLET = '•';

	private final List<Message> messages;
	private Optional<Style> blockStyle;
	private final NoiseBot bot;
	public final String target;
	public final Type type;
	public final Optional<SentMessage> replacing;

	public MessageBuilder(NoiseBot bot, String target, Type type, SentMessage replacing) {
		this.messages = new LinkedList<>();
		this.blockStyle = Optional.empty();
		this.bot = bot;
		this.target = target;
		this.type = type;
		this.replacing = Optional.of(replacing);
	}

	public MessageBuilder(NoiseBot bot, String target, Type type) {
		this.messages = new LinkedList<>();
		this.blockStyle = Optional.empty();
		this.bot = bot;
		this.target = target;
		this.type = type;
		this.replacing = Optional.empty();
	}

	public void reset() {
		this.messages.clear();
		this.blockStyle = Optional.empty();
	}

	public MessageBuilder add(String text) {
		return this.add(text, new Object[0]);
	}

	// This intentionally takes an array instead of varargs to avoid the typical Java confusion around passing a single Object[]
	public MessageBuilder add(String fmt, Object[] args) {
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
			try {
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
					case ' ': // Dynamic style
						flush.accept(style);
						final Object thisStyle = args.remove();
						if(thisStyle instanceof Style) {
							style = (Style)thisStyle;
						} else if(thisStyle instanceof String) {
							style = Style.PLAIN.update((String)thisStyle);
						} else {
							throw new BadFormatException(i, "Expected Style argument, got " + thisStyle.getClass().getSimpleName());
						}
						if(style.isBlock && (!this.messages.isEmpty() || i != 0 || isMultipart)) {
							throw new BadFormatException(i, "Dynamic block style specified after message has begun (must be at the beginning of the first format specifier of the MessageBuilder)");
						}
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
							while(fmt.charAt(++sepEnd) == ' ') ;
							groupStart = sepEnd;
						} else {
							separator = " ";
							groupStart = i + 2;
						}
						// This deals with format strings that include parentheses within groups. Unless the parentheses don't match, in which case...don't do that
						int groupEnd, parens = 0;
						for(groupEnd = groupStart; groupEnd < fmt.length(); groupEnd++) {
							final char c = fmt.charAt(groupEnd);
							if(c == '(') {
								parens++;
							} else if(c == ')') {
								if(parens-- == 0) {
									break;
								}
							}
						}
						if(groupEnd == fmt.length()) {
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
						for(i = groupEnd + 1; i < end && fmt.charAt(i) == ' '; i++) ;
						break;
					default:
						int sp = fmt.indexOf(" ", i + 1);
						if(sp == -1) {
							// This style apparently appears at the end of the format specifier, so it's useless
							sp = end;
						}
						flush.accept(style);
						final String modName = fmt.substring(i + 1, sp);
						final Style newStyle;
						try {
							newStyle = style.update(modName);
						} catch(NoSuchElementException e) {
							throw new BadFormatException(i + 1, String.format("Unrecognized style: %s", modName));
						}
						if(newStyle.isBlock) {
							// This needs to be at the beginning of the first message of the builder
							if(!this.messages.isEmpty() || i != 0 || isMultipart) {
								throw new BadFormatException(i, "Block style specified after message has begun (must be at the beginning of the first format specifier of the MessageBuilder)");
							}
							this.blockStyle = Optional.of(newStyle);
						} else {
							style = newStyle;
						}
						i = sp + 1;
						break;
					}
					break;
				case '%': // Format specifier (maybe)
					if(i + 1 == end) {
						throw new BadFormatException(i, "Dangling `#'");
					}
					if(fmt.charAt(i + 1) == '%') { // Escaped '%'
						pending.append('%');
						i += 2;
						continue;
					}
					Optional<Style> thisArgStyle = Optional.empty();
					if(i + 1 < end && fmt.charAt(i + 1) == '#') { // Dynamic style in the arg queue
						Object thisStyle = args.remove();
						if(thisStyle instanceof Style) {
							thisArgStyle = Optional.of((Style)thisStyle);
						} else if(thisStyle instanceof String) {
							thisArgStyle = Optional.of(Style.PLAIN.update((String)thisStyle));
						} else {
							throw new BadFormatException(i - 1, "Expected Style argument, got " + thisStyle.getClass().getSimpleName());
						}
						if(thisArgStyle.get().isBlock) {
							throw new BadFormatException(i + 1, "Block style passed for format specifier");
						}
						i++;
					}
					if(i + 2 < end && fmt.charAt(i + 1) == '(' && fmt.charAt(i + 2) == '#') { // Static style for just this arg
						final int paren = fmt.indexOf(")", i + 3);
						if(paren == -1) {
							throw new BadFormatException(i + 1, "Unclosed style name");
						}
						final String[] mods = fmt.substring(i + 3, paren).split(" ");
						thisArgStyle = Optional.of(style);
						for(String mod : mods) {
							thisArgStyle = Optional.of(thisArgStyle.get().update(mod));
						}
						if(thisArgStyle.get().isBlock) {
							throw new BadFormatException(i + 3, "Block style specified for format specifier");
						}
						i = paren;
					}

					// Search for the end of the format specifier (the first alpha character -- this may or may not actually be right)
					int specEnd;
					for(specEnd = i + 1; specEnd < end && !Character.isAlphabetic(fmt.charAt(specEnd)); specEnd++) ;
					if(specEnd == end) {
						throw new BadFormatException(i, "Dangling format specifier");
					}
					specEnd++;
					final String formatted = String.format("%" + fmt.substring(i + 1, specEnd), args.remove());
					// If we used a dynamic style, we have to add this as a separate message
					if(thisArgStyle.isPresent()) {
						flush.accept(style);
						pending.append(formatted);
						flush.accept(thisArgStyle.get());
					} else {
						pending.append(formatted);
					}
					i = specEnd;
					break;
				default:
					pending.append(fmt.charAt(i++));
				}
			} catch(NoSuchElementException e) { // arg queue ran out
				throw new BadFormatException(i, "Insufficient arguments to satisfy format", e);
			}
		}
		flush.accept(style);

		return (rtn == null) ? null : rtn.toString();
	}

	public SentMessage[] send() {
		try {
			return this.bot.sendMessageBuilders(this);
		} finally {
			this.reset();
		}
	}

	public Optional<Style> getBlockStyle() {
		return this.blockStyle;
	}

	public String[] getFinalMessages(Optional<Integer> maxMessageLen) {
		final List<String> rtn = new LinkedList<>();
		final StringBuilder str = new StringBuilder();
		for(ListIterator<Message> iter = this.messages.listIterator(); iter.hasNext();) {
			final Message message = iter.next();
			rtn.addAll(Arrays.asList(message.finalize(str, iter, maxMessageLen)));
		}
		if(str.length() > 0) {
			rtn.add(str.toString());
		}
		return rtn.toArray(new String[0]);
	}
}

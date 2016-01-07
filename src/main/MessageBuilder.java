package main;

import debugging.Log;

import java.util.*;
import java.util.List;
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

	private interface Action {}

	private static class PrintStatic implements Action {
		final String text;
		PrintStatic(String text) {
			this.text = text;
		}
	}

	private static class PrintVariable implements Action {
		final String fmt;
		final boolean styled;
		PrintVariable(String fmt, boolean styled) {
			this.fmt = fmt;
			this.styled = styled;
		}
	}

	private static class StartGroup implements Action {
		StartGroup() {}

		// The StartGroup instance is used by the action processor as a record of the current group, so it stashes some data here:
		Optional<String> partSeparator;
		int idx;
		Style style;
		Queue<Object> args;
		Vector<String> output;
	}

	private static class EndGroup implements Action {}

	private static class SetStyle implements Action {
		final String style;
		SetStyle(String style) {
			this.style = style;
		}
	}

	private static class MakeMultipart implements Action {
		final String separator;
		MakeMultipart(String separator) {
			this.separator = separator;
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
		try {
			return this.addWrap(fmt, args);
		} catch(RuntimeException e) {
			Log.e(e);
			throw e;
		}
	}

	public MessageBuilder addWrap(String fmt, Object[] args) {
		Log.d("Formatting: %s", fmt);

		// First, build a list of actions from the format string
		final Vector<Action> actions = new Vector<>();
		for(int i = 0; i < fmt.length();) {
			switch(fmt.charAt(i)) {
			case '%':
				boolean styled = false;
				if(fmt.charAt(i + 1) == '%') {
					actions.add(new PrintStatic("%"));
					i += 2;
					continue;
				} else if(fmt.charAt(i + 1) == '^') {
					styled = true;
					i++;
				}
				final int start = ++i;
				// Search for the end of the format string (the first alpha character -- this may or may not actually be right)
				while(!Character.isAlphabetic(fmt.charAt(i++)));
				actions.add(new PrintVariable("%" + fmt.substring(start, i), styled));
				break;
			case '(':
				// Consume style strings
				final List<String> styles = new LinkedList<>();
				Optional<String> partSeparator = Optional.empty();
				i++;
				while(true) {
					// Skip all spaces. This means no group can start with a literal space, but that's probably ok
					for(; fmt.charAt(i) == ' '; i++);
					if(fmt.charAt(i) == '[') {
						if(partSeparator.isPresent()) {
							throw new IllegalArgumentException(String.format("Second part separator at %d", i));
						}
						final int end = fmt.indexOf(']', i + 1);
						if(end == -1) {
							throw new IllegalArgumentException(String.format("Unclosed part separator at %d", i));
						}
						partSeparator = Optional.of(fmt.substring(i + 1, end));
						i = end + 1;
					} else {
						final int end = fmt.indexOf(" ", i);
						if(end == -1) {
							// The group can't be empty, so the last term can't be a style string
							break;
						}
						final String candidate = fmt.substring(i, end);
						if(Style.getMod(candidate).isPresent()) {
							styles.add(candidate);
						} else {
							break;
						}
						i = end + 1;
					}
				}
				actions.add(new StartGroup(styles.toArray(new String[0]), partSeparator));
				break;
			case ')':
				actions.add(new EndGroup());
				i++;
				break;
			default:
				try {
					actions.set(actions.size() - 1, new PrintStatic(((PrintStatic)actions.lastElement()).text + fmt.charAt(i)));
				} catch(ClassCastException | NoSuchElementException e) { // NoSuchElement if actions is empty, ClassCast if the last action isn't a PrintStatic
					actions.add(new PrintStatic("" + fmt.charAt(i)));
				}
				i++;
				break;
			}
		}

		//TODO Turn style keywords into their own action (SetStyle). This will allow "red %s" to work without an explicit group, as well as stuff like "red %s green %s"
		//TODO Add 'raw text support'
		/*
		 * Preprocess the action list
		 *
		 *   1) Wrap the whole action list in a start/end group so there's no need to special case if we're in a group or not
		 *   2) Remove empty groups
		 *   3) Combine adjacent PrintStack actions, because the parser is comically inefficient
		 *   3) Nested groups are a bit of a problem, since groups don't flush their output (can't, for multipart groups) until
		 *      they end. We split up overlapping groups, while filling in the style information for each group as we go.
		 *      For example, (a (b) c) turns into:
		 *
		 *          StartGroup Print StartGroup Print EndGroup Print EndGroup
		 *
		 *      We rewrite it to:
		 *
		 *          StartGroup Print EndGroup StartGroup Print EndGroup StartGroup Print EndGroup
		 *
		 *      while being sure to base the second group's style off of the first's since it was originally nested.
		 */
		{
			final StartGroup baseGroup = new StartGroup(new String[0], Optional.empty());
			baseGroup.style = Style.PLAIN;
			baseGroup.args = new ArrayDeque<>(Arrays.asList(args));
			baseGroup.idx = 0;
			baseGroup.output = new Vector<>();
			actions.add(0, baseGroup);
			actions.add(new EndGroup());

			final Stack<StartGroup> groups = new Stack<>();
			for(ListIterator<Action> iter = actions.listIterator(); iter.hasNext();) {
				final Action action = iter.next();
				if(action instanceof StartGroup) {
					final StartGroup sg = (StartGroup)action;
					if(iter.next() instanceof EndGroup) {
						continue;
					}
					iter.previous();
					// I'm sure there's a streams way to do this with reduce() or collect(), but I can't quite figure it out
					if(groups.isEmpty()) {
						sg.style = Style.PLAIN;
					} else {
						sg.style = groups.peek().style;
						// End the previous group before starting this one
						// We'll also start another copy of it after this group ends
						iter.previous();
						iter.add(new EndGroup());
						iter.next();
					}
					for(String mod : sg.styles) {
						sg.style = sg.style.update(mod);
					}
					groups.push(sg);
				} else if(action instanceof EndGroup) {
					groups.pop();
					if(!groups.isEmpty()) {
						// Restart the outer group
						iter.add(groups.peek());
					}
				}
			}

			if(!groups.isEmpty()) {
				throw new IllegalStateException("Unclosed group");
			}
		}

		// Process the actions
		final Queue<Object> baseArgsQ = new ArrayDeque<>(Arrays.asList(args));
		StartGroup group = null;
		boolean multipart = false;
		for(int i = 0; i < actions.size(); i++) {
			final Action action = actions.get(i);
			if(action instanceof PrintStatic) {
				final PrintStatic ps = (PrintStatic)action;
				group.output.add(this.bot.format(group.style, ps.text));
			} else if(action instanceof PrintVariable) {
				final PrintVariable pv = (PrintVariable)action;
				Style s;
				if(pv.styled) {
					final Object o = group.args.remove();
					if(o instanceof Style) {
						s = (Style)o;
					} else if(o instanceof String) {
						s = Style.PLAIN;
						for(String mod : ((String)o).split(" ")) {
							s = s.update(mod);
						}
					} else {
						throw new IllegalArgumentException(String.format("Passed bad type for style argument (%s)", o.getClass()));
					}
				} else {
					s = group.style;
				}
				group.output.add(this.bot.format(s, String.format(pv.fmt, group.args.remove())));
			} else if(action instanceof StartGroup) {
				final Queue<Object> oldArgs = (group != null) ? group.args : baseArgsQ;
				group = (StartGroup)action;
				group.idx = i;
				group.output = new Vector<>();
				if(group.partSeparator.isPresent()) {
					if(multipart) {
						throw new IllegalStateException("Part groups cannot nest");
					}
					multipart = true;
					group.args = new ArrayDeque<>(Arrays.asList((Object[])oldArgs.remove()));
					// We use null to indicate the start of a single part. At the end of the part, we merge all the output pieces into a single entry
					group.output.add(null);
				} else {
					group.args = oldArgs; // Single part groups just pull arguments normally, so share the arg queue with the parent one
				}
			} else if(action instanceof EndGroup) {
				if(group.partSeparator.isPresent()) {
					// Merge all the pieces of this part into one entry in the output list
					final StringBuilder part = new StringBuilder();
					final int idx = group.output.lastIndexOf(null);
					for(int j = idx + 1; j < group.output.size(); j++) {
						part.append(group.output.get(j));
					}
					group.output.setSize(idx);
					group.output.add(part.toString());

					// Ending one part of a multipart group. If there are still arguments, do another pass
					if(group.args.isEmpty()) {
						this.messages.add(new MultipartMessage(group.partSeparator.get(), group.output.toArray(new String[0])));
						multipart = false;
					} else {
						group.output.add(null); // Start the next part
						i = group.idx;
						continue;
					}
				} else {
					// Ending a single part group
					this.messages.add(new SingleMessage(group.output.stream().collect(Collectors.joining(""))));
				}
				group = null;
			}
		}

		return this;
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

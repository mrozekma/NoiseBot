package main;

import com.google.gson.internal.StringMap;
import com.ullink.slack.simpleslackapi.*;
import com.ullink.slack.simpleslackapi.SlackTimestamped;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import org.json.JSONException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static main.Utilities.pluralize;
import static main.Utilities.substring;

/**
 * @author Michael Mrozek
 *         Created Dec 31, 2015.
 */
public class SlackNoiseBot extends NoiseBot {
	private static final int RECENT_MESSAGE_MEMORY = 100;

	private final SlackServer server;
	private final TreeMap<String, SlackMessagePosted> recentMessages;

	public SlackNoiseBot(SlackServer server, String channel, boolean quiet, String[] fixedModules) {
		super(channel, quiet, fixedModules);
		this.server = server;
		this.recentMessages = new TreeMap<>();
	}

	static void createBot(String connectionName, StringMap data) throws IOException {
		final String token = (String)data.get("token");
		final boolean quiet = data.containsKey("quiet") ? (Boolean)data.get("quiet") : false;
		final String[] modules = data.containsKey("modules") ? ((List<String>)data.get("modules")).toArray(new String[0]) : null;
		final SlackServer server = new SlackServer(token);

		final Optional<String> general = server.connect();
		if(!general.isPresent()) {
			throw new IOException("Unable to connect to server");
		}

		final SlackNoiseBot bot = new SlackNoiseBot(server, "#" + general.get(), quiet, modules);
		server.setBot(bot);
		NoiseBot.bots.put(connectionName, bot);
		bot.onChannelJoin();
	}

	private SlackChannel slackChannel() {
		return this.server.findChannelByName(this.channel.substring(1));
	}

	@Override public Protocol getProtocol() {
		return Protocol.Slack;
	}

	@Override public String getBotNick() {
		return this.server.sessionPersona().getUserName();
	}

	@Override public String[] getNicks() {
		//TODO This appears to cache old data
		return this.slackChannel().getMembers().stream().map(SlackPersona::getUserName).toArray(String[]::new);
	}

	@Override public boolean clearPendingSends() {
		return false;
	}

	@Override File getStoreDirectory() {
		// Could use SlackPersona.getId(), but I don't want this method to depend on being connected
		// Instead taking the last 10 characters of the API token and hoping to avoid collisions
		final String id = substring(this.server.getToken(), -10);
		return new File(STORE_DIRECTORY, String.format("%s@slack%s", id, this.channel));
	}

	@Override public void whois(String nick, WhoisHandler handler) {
		final SlackUser user = this.server.findUserByUserName(nick);
		handler.setUsername(user.getUserName());
		handler.setHostname("slack");
		handler.setGecos(user.getRealName());
		handler.setServer("slack");
		handler.setAccount(user.getUserName());

		// Synchronous communication -- what an idea
		handler.notifyDone(true);
	}

	@Override protected void outputSyncInfo(Git.Revision oldrev, Git.Revision[] revs, boolean coreChanged, String[] reloadedModules) {
		final String title = "Synced " + pluralize(revs.length, "revision", "revisions");
		final Stream<String> fmtRevs = Arrays.stream(revs).map(rev -> String.format("%c `%s` by _%s_ -- %s", MessageBuilder.BULLET, rev.getHash(), rev.getAuthor(), rev.getDescription()));
		final SlackAttachment attachment = new SlackAttachment(title, title, fmtRevs.collect(Collectors.joining("\n")), null);
		attachment.setColor("#ff6d20");
		attachment.setTitleLink(Git.diffLink(oldrev, this.revision));
		attachment.addMarkdownIn("text");
		this.sendAttachment(attachment);

		if(coreChanged) {
			this.sendMessage("#bold Core files changed; NoiseBot will restart");
		} else if(reloadedModules.length > 0) {
			Style.pushOverrideMap(new HashMap<String, Style>() {{
				Style.addHelpStyles(getProtocol(), this);
			}});
			try {
				this.sendMessage("Reloaded modules: #([, ] #module %s)", (Object)reloadedModules);
			} finally {
				Style.popOverrideMap();
			}
		}
	}

	@Override protected void onIssueEvent(String action, JSONObject issue) throws JSONException {
		final String title = String.format("Issue #%d %s", issue.getInt("number"), action);
		final SlackAttachment attachment = new SlackAttachment(title, title, issue.getString("title"), null);
		attachment.setColor("#ff6d20");
		attachment.setTitleLink(issue.getString("html_url"));
		this.sendAttachment(attachment);
	}

	public String getUserID(String username) {
		return this.server.findUserByUserName(username).getId();
	}

	// We remember the most recent messages so we can find them on events (e.g. reactionAdded)
	void recordIncomingMessage(SlackMessagePosted message) {
		synchronized(this.recentMessages) {
			this.recentMessages.put(message.getTimestamp(), message);
			while(this.recentMessages.size() > RECENT_MESSAGE_MEMORY) {
				this.recentMessages.remove(this.recentMessages.firstKey());
			}
		}
	}

	Optional<SlackMessagePosted> getRecordedMessage(String ts) {
		synchronized(this.recentMessages) {
			return Optional.ofNullable(this.recentMessages.get(ts));
		}
	}

	public String escape(String text) {
		// https://api.slack.com/docs/formatting#urls_and_escaping
		//TODO Strip regular formatting: https://get.slack.help/hc/en-us/articles/202288908-Formatting-your-messages
		//TODO Add regular formatting from 'style'
		text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

		// There isn't a good way to escape Slack formatting characters. The following works for bold but not italics, and is ugly
		// https://github.com/slackhq/slack-api-docs/issues/34
//		text = text.replaceAll("([*_])([^ ])", "$1 $2").replaceAll("([^ ])([*_])", "$1 $2");

		return text;
	}

	private static final Pattern UNESCAPE_PATTERN = Pattern.compile("<([^|>]+)(?:|([^>]+))?>");
	public String unescape(String text) {
		final Matcher matcher = UNESCAPE_PATTERN.matcher(text);
		final StringBuffer rtn = new StringBuffer();
		while(matcher.find()) {
			final String what = matcher.group(1);
			final Optional<String> display = Optional.ofNullable(matcher.group(2));
			if(what.startsWith("@") || what.startsWith("#")) {
				// For users and channels, use the display string if available
				if(display.isPresent()) {
					matcher.appendReplacement(rtn, display.get());
				} else {
					switch(what.charAt(0)) {
					case '@':
						// Strip the '@'
						final SlackUser user = this.server.findUserById(what.substring(1));
						matcher.appendReplacement(rtn, (user == null) ? what.substring(1) : user.getUserName());
						break;
					case '#':
						// Include the '#'
						final SlackChannel channel = this.server.findChannelById(what.substring(1));
						matcher.appendReplacement(rtn, (channel == null) ? what : ("#" + channel.getName()));
						break;
					}
				}
			} else {
				// For URLs, use the canonical version
				matcher.appendReplacement(rtn, what);
			}
		}
		matcher.appendTail(rtn);
		return rtn.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
	}

	private static String emph(String text, String emph) {
		int leading, trailing, len = text.length();
		for(leading = 0; leading < len && text.charAt(leading) == ' '; leading++);
		for(trailing = len; trailing > leading && text.charAt(trailing - 1) == ' '; trailing--);
		return text.substring(0, leading) + emph + text.substring(leading, trailing) + emph + text.substring(trailing);
	}

	public String formatUser(String username) {
		final SlackUser user = this.server.findUserByUserName(username);
		return String.format("<@%s>", user.getId());
	}

	@Override public String format(Style style, String text) {
		return this.format(style, text, true);
	}

	public String format(Style style, String text, boolean escape) {
		if(escape) {
			text = this.escape(text);
		}

		if(style.is(Style.Prop.bold)) {
			text = emph(text, "*");
		}
		if(style.is(Style.Prop.italic)) {
			text = emph(text, "_");
		}
		if(style.is(Style.Prop.mono)) {
			text = emph(text, "`");
		}
		if(style.is(Style.Prop.monoblock)) {
//			text = emph(text, "```");
			text = String.format("```%s```", text);
		}

		return text;
	}

	@Override public SentMessage[] sendMessageBuilders(MessageBuilder... builders) {
		// As long as consecutive builders can logically be merged, we do so and separate with newlines
		final BiPredicate<MessageBuilder, MessageBuilder> compatible = (a, b) -> a.type == b.type && a.target.equals(b.target) && a.getBlockStyle().equals(b.getBlockStyle()) && a.replacing.equals(b.replacing);

		final List<SentMessage> rtn = new LinkedList<>();
		final List<String> finalMessages = new LinkedList<>();
		for(int i = 0; i <= builders.length; i++) {
			// This condition is a bit complicated. It's always false on the first pass (since nothing is buffered yet),
			// and always true on the last pass (which is after the end of the builders list, and happens just so we can output the last messages)
			if(i == builders.length || (i > 0 && !compatible.test(builders[i], builders[i - 1]))) {
				final MessageBuilder last = builders[i - 1];
				// finalMessages will never be empty here
				String text = finalMessages.stream().collect(Collectors.joining("\n"));
				final Optional<Style> blockStyle = last.getBlockStyle();

				// Special handling for [core]error block styles
				boolean handled = false;
				if(blockStyle.isPresent() && (last.type == MessageBuilder.Type.MESSAGE || last.type == MessageBuilder.Type.NOTICE)) {
					final Style style = blockStyle.get();
					if(style == Style.ERROR || style == Style.COREERROR) {
						if(last.replacing.isPresent()) {
							rtn.add(this.editTitled(last.replacing.get(), Style.ERROR.color, "Error", text));
						} else {
							rtn.add(this.sendTitledTo(last.target, Style.ERROR.color, "Error", text));
						}
						handled = true;
					}
				}

				if(!handled) {
					final SlackChannel target = (last.target.charAt(0) == '#') ? this.server.findChannelByName(last.target.substring(1)) : null;
					switch(last.type) {
					case ACTION:
						// There's currently no way to send me_message events through the Slack API
						// Instead we just italicize the whole message. Close enough?
						text = this.format(Style.ITALIC, text, false);
						// Fallthrough
					case MESSAGE:
					case NOTICE:
					default:
						final SlackMessageHandle<? extends SlackTimestamped> handle;
						if(last.replacing.isPresent()) {
							final SlackSentMessage sent = (SlackSentMessage)last.replacing.get();
							if(target != null) {
								handle = this.server.updateMessage(sent.getTimestamp(), target, text);
							} else {
								handle = this.server.updateMessageToUser(sent.getTimestamp(), last.target, text);
							}
						} else {
							if(target != null) {
								handle = this.server.sendMessage(target, text, null, false);
							} else {
								handle = this.server.sendMessageToUser(last.target, text, null);
							}
						}
						rtn.add(new SlackSentMessage(this, last.target, last.type, handle.getReply().getTimestamp()));
						break;
					}
				}

				finalMessages.clear();
				if(i == builders.length) {
					break;
				}
			}

			// getFinalMessages() should only return 1 element, since we don't give a max message len, but it feels dirty to rely on that
			finalMessages.add(Arrays.stream(builders[i].getFinalMessages(Optional.empty())).collect(Collectors.joining("")));
		}

		return rtn.toArray(new SentMessage[0]);
	}

	public SlackSentMessage sendAttachment(SlackAttachment attachment) {
		return this.sendAttachmentTo(this.channel, attachment);
	}

	public SlackSentMessage sendAttachmentTo(String target, SlackAttachment attachment) {
		final SlackMessageHandle<? extends SlackTimestamped> handle;
		if(!target.isEmpty() && target.charAt(0) == '#') {
			handle = this.server.sendMessage(this.server.findChannelByName(target.substring(1)), "\n", attachment);
		} else {
			handle = this.server.sendMessageToUser(target, "\n", attachment);
		}
		return new SlackSentMessage(this, target, MessageBuilder.Type.MESSAGE, handle.getReply().getTimestamp());
	}

	public SentMessage editAttachment(SentMessage replacing, SlackAttachment attachment) {
		final SlackSentMessage sent = (SlackSentMessage)replacing;
		final String target = replacing.target;
		final SlackMessageHandle<? extends SlackTimestamped> handle;
		if(!target.isEmpty() && target.charAt(0) == '#') {
			handle = this.server.updateMessage(sent.getTimestamp(), this.server.findChannelByName(target.substring(1)), "\n", attachment);
		} else {
			handle = this.server.updateMessageToUser(sent.getTimestamp(), target, "\n", attachment);
		}
		return new SlackSentMessage(this, target, MessageBuilder.Type.MESSAGE, handle.getReply().getTimestamp());
	}

	public SentMessage sendTitledTo(String target, Color color, String title, String text) {
		final SlackAttachment attachment = new SlackAttachment(title, text, text, null);
		attachment.setColor(String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()));
		return this.sendAttachmentTo(target, attachment);
	}

	public SentMessage editTitled(SentMessage replacing, Color color, String title, String text) {
		final SlackAttachment attachment = new SlackAttachment(title, text, text, null);
		attachment.setColor(String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()));
		return this.editAttachment(replacing, attachment);
	}

	public void deleteMessage(SlackSentMessage message) {
		if(!message.target.isEmpty() && message.target.charAt(0) == '#') {
			this.server.deleteMessage(message.getTimestamp(), this.server.findChannelByName(message.target.substring(1)));
		} else {
			this.server.deleteMessageToUser(message.getTimestamp(), message.target);
		}
	}

	public void uploadFile(byte[] data, String title) {
		this.uploadFileTo(this.channel, data, title);
	}

	public void uploadFileTo(String channel, byte[] data, String title) {
		this.server.uploadFile(this.server.findChannelByName(channel.substring(1)), data, title);
	}
}

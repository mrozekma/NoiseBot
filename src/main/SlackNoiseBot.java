package main;

import com.google.gson.internal.StringMap;
import com.mrozekma.taut.*;
import debugging.Log;
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

import static main.Utilities.exceptionString;
import static main.Utilities.pluralize;
import static main.Utilities.substring;

/**
 * @author Michael Mrozek
 *         Created Dec 31, 2015.
 */
public class SlackNoiseBot extends NoiseBot {
	private static final int SLACK_SIGNAL_PORT = 41934;

	private final SlackServer server;

	public SlackNoiseBot(SlackServer server, String channel, boolean quiet, String[] fixedModules) {
		super(channel, quiet, fixedModules);
		this.server = server;
	}

	static void createBot(String connectionName, StringMap data) throws IOException, TautException {
		final String token = (String)data.get("token");
		final String botToken = (String)data.get("bot-token");
		final boolean quiet = data.containsKey("quiet") ? (Boolean)data.get("quiet") : false;
		final String[] modules = data.containsKey("modules") ? ((List<String>)data.get("modules")).toArray(new String[0]) : null;
		final SlackServer server = new SlackServer(token, botToken);

		if(data.containsKey("slack-listener")) {
			final StringMap httpsData = (StringMap)data.get("slack-listener");
			final String keyFile = (String)httpsData.get("key");
			final String certFile = (String)httpsData.get("cert");
			final String verificationToken = (String)httpsData.get("verification-token");
			server.setActionHandler(new TautHTTPSServer(new File(certFile), new File(keyFile), SLACK_SIGNAL_PORT, verificationToken));
		}

		final Optional<String> general = server.connect();
		if(!general.isPresent()) {
			throw new IOException("Unable to connect to server");
		}

		final SlackNoiseBot bot = new SlackNoiseBot(server, "#" + general.get(), quiet, modules);
		server.setBot(bot);
		NoiseBot.bots.put(connectionName, bot);
		bot.onChannelJoin();
	}

	private TautChannel slackChannel() throws TautException {
		return this.server.getChannelByName(this.channel.substring(1));
	}

	@Override public Protocol getProtocol() {
		return Protocol.Slack;
	}

	@Override public String getBotNick() {
		try {
			return this.server.getSelf().getName();
		} catch(TautException e) {
			Log.e(e);
			//TODO Add IOException to the NoiseBot methods so these don't need to throw RuntimeException anymore (see other cases below)
			throw new RuntimeException(e);
		}
	}

	@Override public String[] getNicks() {
		try {
			return this.slackChannel().getMembers().stream()
					.filter(user -> {
						try {
							return !user.isDeleted();
						} catch(TautException e) {
							Log.e(e);
							throw new RuntimeException(e);
						}
					})
					.map(user -> {
						try {
							return user.getName();
						} catch(TautException e) {
							Log.e(e);
							throw new RuntimeException(e);
						}
					})
					.toArray(String[]::new);
		} catch(TautException e) {
			Log.e(e);
			throw new RuntimeException(e);
		}
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
		try {
			final TautUser user = this.server.getUserByName(nick);
			handler.setUsername(user.getName());
			handler.setHostname("slack");
			handler.setGecos(user.getFirstName() + " " + user.getLastName());
			handler.setServer("slack");
			handler.setAccount(user.getName());
		} catch(TautException e) {
			Log.e(e);
			throw new RuntimeException(e);
		}

		// Synchronous communication -- what an idea
		handler.notifyDone(true);
	}

	@Override protected void outputSyncInfo(Git.Revision oldrev, Git.Revision[] revs, boolean coreChanged, String[] reloadedModules) {
		final String title = "Synced " + pluralize(revs.length, "revision", "revisions");
		final Stream<String> fmtRevs = Arrays.stream(revs).map(rev -> String.format("%c `%s` by _%s_ -- %s", MessageBuilder.BULLET, rev.getHash(), rev.getAuthor(), rev.getDescription()));
		final TautAttachment attachment = this.makeAttachment();
		attachment.setTitle(title);
		attachment.setFallback(title);
		attachment.setText(fmtRevs.collect(Collectors.joining("\n")), true);
		attachment.setColor("#ff6d20");
		attachment.setTitleLink(Git.diffLink(oldrev, this.revision));
		try {
			this.sendAttachment(attachment);
		} catch(TautException e) {
			Log.e(e);
			throw new RuntimeException(e);
		}

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

	@Override protected void onIssueEvent(String action, JSONObject issue, String url) throws JSONException {
		final String title = String.format("Issue #%d %s", issue.getInt("number"), action);
		final TautAttachment attachment = this.makeAttachment();
		attachment.setTitle(title);
		attachment.setFallback(title);
		attachment.setText(issue.getString("title"), true);
		attachment.setColor("#ff6d20");
		attachment.setTitleLink(url);
		try {
			this.sendAttachment(attachment);
		} catch(TautException e) {
			Log.e(e);
			throw new RuntimeException(e);
		}
	}

	public String getUserID(String username) throws TautException {
		return this.server.getUserByName(username).getId();
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
	public String unescape(String text) throws TautException {
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
						final TautUser user = this.server.getUserById(what.substring(1));
						matcher.appendReplacement(rtn, (user == null) ? what.substring(1) : user.getName());
						break;
					case '#':
						// Include the '#'
						final TautChannel channel = this.server.getChannelById(what.substring(1));
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
		try {
			final TautUser user = this.server.getUserByName(username);
			return String.format("<@%s>", user.getId());
		} catch(TautException e) {
			Log.e(e);
			return username;
		}
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
						try {
							if(last.replacing.isPresent()) {
								rtn.add(this.editTitled(last.replacing.get(), Style.ERROR.color, "Error", text));
							} else {
								rtn.add(this.sendTitledTo(last.target, Style.ERROR.color, "Error", text));
							}
						} catch(TautException e) {
							Log.e(e);
							throw new RuntimeException(e);
						}
						handled = true;
					}
				}

				if(!handled) {
					try {
						// The bot connection doesn't have the scope to send to channels, so we use the regular connection
						// However, we must use the bot connection for direct messages, or the direct message will come from the user who added the bot integration instead of coming from the bot's account
						final TautAbstractChannel target = (last.target.charAt(0) == '#')
								? this.server.getChannelByName(last.target.substring(1))
								: this.server.getBotConnection().getUserByName(last.target).getDirectChannel();
						switch(last.type) {
						case ACTION:
							// There's currently no way to send me_message events through the Slack API
							// Instead we just italicize the whole message. Close enough?
							text = this.format(Style.ITALIC, text, false);
							// Fallthrough
						case MESSAGE:
						case NOTICE:
						default:
							final TautMessage message;
							if(last.replacing.isPresent()) {
								final SlackSentMessage sent = (SlackSentMessage)last.replacing.get();
								message = sent.getTautMessage();
								message.update(text);
							} else {
								message = target.sendMessage(text);
							}
							rtn.add(new SlackSentMessage(this, last.target, last.type, message));
							break;
						}
					} catch(TautException e) {
						Log.e(e);
						throw new RuntimeException(e);
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

	public SentMessage[] sendMessageTo(TautAbstractChannel channel, String fmt, Object... args) throws TautException {
		if(channel instanceof TautChannel) {
			return this.sendMessageTo("#" + ((TautChannel)channel).getName(), fmt, args);
		} else if(channel instanceof TautDirectChannel) {
			return this.sendMessageTo(((TautDirectChannel)channel).getUser().getName(), fmt, args);
		} else {
			throw new TautException("Unsupported TautAbstractChannel class: " + channel.getClass());
		}
	}

	public SentMessage[] trySendMessageTo(TautAbstractChannel channel, String fmt, Object... args) {
		try {
			return this.sendMessageTo(channel, fmt, args);
		} catch(TautException e) {
			Log.e(e);
			return new SentMessage[0];
		}
	}

	public void reportErrorTo(TautAbstractChannel channel, Throwable t) {
		Log.e(t);
		this.trySendMessageTo(channel, "#error %s", exceptionString(t));
	}

	public void reportErrorTo(String target, Throwable t) {
		Log.e(t);
		this.sendMessageTo(target, "#error %s", exceptionString(t));
	}

	public SlackSentMessage sendAttachment(TautAttachment attachment) throws TautException {
		return this.sendAttachmentTo(this.channel, attachment);
	}

	public SlackSentMessage sendAttachmentTo(String target, TautAttachment attachment) throws TautException {
		final TautAbstractChannel channel;
		if(!target.isEmpty() && target.charAt(0) == '#') {
			channel = this.server.getChannelByName(target.substring(1));
		} else {
			channel = this.server.getUserByName(target).getDirectChannel();
		}
		final TautMessage message = channel.sendAttachment(attachment);
		return new SlackSentMessage(this, target, MessageBuilder.Type.MESSAGE, message);
	}

	public SentMessage editAttachment(SentMessage replacing, TautAttachment attachment) throws TautException {
		final SlackSentMessage sent = (SlackSentMessage)replacing;
		final String target = replacing.target;
		final TautAbstractChannel channel;
		if(!target.isEmpty() && target.charAt(0) == '#') {
			channel = this.server.getChannelByName(target.substring(1));
		} else {
			channel = this.server.getUserByName(target).getDirectChannel();
		}
		final TautMessage message = sent.getTautMessage().update(new TautMessageDraft("\n").setAttachments(attachment));
		return new SlackSentMessage(this, target, MessageBuilder.Type.MESSAGE, message);
	}

	public SentMessage sendTitledTo(String target, Color color, String title, String text) throws TautException {
		final TautAttachment attachment = this.makeAttachment();
		attachment.setTitle(title);
		attachment.setText(text);
		attachment.setFallback(text);
		attachment.setColor(color);
		return this.sendAttachmentTo(target, attachment);
	}

	public SentMessage editTitled(SentMessage replacing, Color color, String title, String text) throws TautException {
		final TautAttachment attachment = this.makeAttachment();
		attachment.setTitle(title);
		attachment.setText(text);
		attachment.setFallback(text);
		attachment.setColor(color);
		return this.editAttachment(replacing, attachment);
	}

	public void deleteMessage(SlackSentMessage message) throws TautException {
		message.getTautMessage().delete();
	}

	public void uploadFile(byte[] data, String title) throws TautException {
		this.uploadFileTo(this.channel, data, title);
	}

	public void uploadFileTo(String channel, byte[] data, String title) throws TautException {
		// This uses the bot connection so the file will be uploaded by the bot, not the auth user
		this.server.getBotConnection().getChannelByName(channel.substring(1)).uploadFile(new TautFileUpload(data).setTitle(title));
	}

	public TautAttachment makeAttachment() {
		return new TautAttachment(this.server);
	}
}

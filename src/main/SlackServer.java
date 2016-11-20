package main;

import com.mrozekma.taut.*;
import com.mrozekma.taut.JSONObject;
import debugging.Log;

import java.io.IOException;
import java.util.Optional;

import static main.Utilities.exceptionString;

/**
 * @author Michael Mrozek
 *         Created Dec 31, 2015.
 */
public class SlackServer extends TautConnection implements TautEventListener {
	private static abstract class ModuleCall {
		public abstract void call(NoiseBot bot, NoiseModule module) throws Exception;
		public void onException(NoiseBot bot, Exception e) {
			Log.e(e);
		}
	}

	public static final int MAX_ACTION_BUTTONS_PER_MESSAGE = 5;
	static final String ACTION_CALLBACK_PREFIX = "noisebot.modules.";

	private final String token;
	private final TautConnection botConnection;
	private final TautRTMConnection rtm;
	private SlackNoiseBot bot;
	private Optional<TautHTTPSServer> actionHandler = Optional.empty();

	SlackServer(String token, String botToken) throws TautException {
		super(token);
		this.token = token;

		this.botConnection = new TautConnection(botToken);
		this.botConnection.setHistoryConnection(this);
		this.rtm = this.botConnection.rtmStart();
		this.rtm.addListener(this);
	}

	String getToken() {
		return this.token;
	}

	void setBot(SlackNoiseBot bot) {
		this.bot = bot;
	}

	void setActionHandler(TautHTTPSServer actionHandler) throws IOException {
		this.actionHandler = Optional.of(actionHandler);
		actionHandler.start(new TautHTTPSServer.ActionRequestHandler(this) {
			@Override protected void onSlackRequest(UserAction action) {
				SlackServer.this.onSlackAction(action);
			}
		});
	}

	TautConnection getBotConnection() {
		return this.botConnection;
	}

	public Optional<String> connect() {
		try {
			if(!this.rtm.isConnected()) {
				Log.i("Connecting to slack");
				System.out.println("Connecting to slack");
				this.rtm.connect();
			}

			if(this.bot != null) {
				return Optional.of(this.bot.channel);
			}

			final Optional<TautChannel> general = this.getChannels().stream().filter(channel -> {
				try {
					return channel.isGeneral();
				} catch(TautException e) {
					Log.e(e);
					return false;
				}
			}).findAny();
			if(general.isPresent()) {
				return Optional.of(general.get().getName());
			}

			System.err.println("No general channel found");
		} catch(TautException e) {
			Log.e(e);
			System.err.printf("Network error: %s\n", e.getMessage());
		}

		return Optional.empty();
	}

	@Override public TautUser getSelf() {
		return this.botConnection.getSelf();
	}

	private void moduleDispatch(String channel, ModuleCall call) {
		for(NoiseModule module : this.bot.getModules().values()) {
			try {
				call.call(bot, module);
			} catch(Exception e) {
				call.onException(bot, e);
			}
		}
	}

	// 'conn' here will be 'this.botConnection', but we want channels/messages constructed via the main connection ('this')
	@Override public void fire(TautConnection conn, JSONObject json) throws TautException {
		TautEventListener.super.fire(this, json);
	}

	@Override public void onMessage(TautMessage message) {
		try {
			final TautUser sender = message.getCurrent().getUser().get();
			final String senderName = sender.getName();
			final String responseTarget;
			{
				final TautAbstractChannel channel = message.getChannel();
				if(channel instanceof TautDirectChannel) {
					responseTarget = senderName;
				} else if(channel instanceof TautChannel) {
					responseTarget = "#" + ((TautChannel)channel).getName();
				} else {
					throw new ClassCastException("Unexpected type: " + channel.getClass());
				}
			}
			final String text = message.getText();
			Log.in(String.format("<%s -> %s: %s", senderName, responseTarget, text));

			this.moduleDispatch(responseTarget, new ModuleCall() {
				@Override public void call(NoiseBot bot, NoiseModule module) throws Exception {
					if(!sender.isBot()) {
						module.processMessageAndDisplayResult(new Message(bot, ((SlackNoiseBot)bot).unescape(text), senderName, responseTarget));
					}
				}

				@Override public void onException(NoiseBot bot, Exception e) {
					super.onException(bot, e);
					Log.e(e);
					bot.sendMessage("#coreerror %s", exceptionString(e));
				}
			});
		} catch(TautException | ClassCastException e) {
			Log.e(e);
			bot.sendMessage("#coreerror %s", exceptionString(e));
		}
	}

	//TODO Listen to ReactionRemoved?
	@Override public void onMessageReactionAdded(TautMessage message, TautReaction reaction) {
		try {
			final TautUser sender = message.getCurrent().getUser().get();
			final String senderName = sender.getName();
			final String responseTarget;
			{
				final TautAbstractChannel channel = message.getChannel();
				if(channel instanceof TautDirectChannel) {
					responseTarget = senderName;
				} else if(channel instanceof TautChannel) {
					responseTarget = "#" + ((TautChannel)channel).getName();
				} else {
					throw new ClassCastException("Unexpected type: " + channel.getClass());
				}
			}
			final String ts = message.getCurrent().getTs();
			final String targetSender = message.getCurrent().getUser().get().getName();

			this.moduleDispatch(responseTarget, new ModuleCall() {
				@Override public void call(NoiseBot bot, NoiseModule module) throws Exception {
					if(!sender.isBot()) {
						module.processReaction(new Message(bot, ((SlackNoiseBot)bot).unescape(message.getText()), targetSender, targetSender), senderName, reaction.getName());
					}
				}

				@Override public void onException(NoiseBot bot, Exception e) {
					super.onException(bot, e);
					Log.e(e);
					bot.sendMessage("#coreerror %s", exceptionString(e));
				}
			});
		} catch(TautException e) {
			Log.e(e);
			bot.sendMessage("#coreerror %s", exceptionString(e));
		}
	}

	private void onSlackAction(TautHTTPSServer.ActionRequestHandler.UserAction action) {
		final String callbackId = action.getCallbackId();
		if(!callbackId.startsWith(ACTION_CALLBACK_PREFIX)) {
			Log.e("Bad slack action callback ID: %s", callbackId);
			this.bot.sendMessage("#coreerror Unexpected Slack action");
			return;
		}
		final NoiseModule module = this.bot.getModules().get(callbackId.substring(ACTION_CALLBACK_PREFIX.length()));
		if(module == null) {
			Log.e("Bad slack action callback ID: %s", callbackId);
			this.bot.sendMessage("#coreerror Bad slack action; unknown or unloaded module");
			return;
		}
		if(!(module instanceof SlackActionHandler)) {
			Log.e("Slack action for non-action handler: %s", module.getFriendlyName());
			this.bot.sendMessage("#coreerror Bad slack action; %s is not an action handler", module.getFriendlyName());
			return;
		}
		((SlackActionHandler)module).onSlackAction(action);
	}
}

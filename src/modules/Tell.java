package modules;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import main.CommandContext;
import main.NoiseModule;

/**
 * Tell
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Tell extends NoiseModule implements Serializable {
	private static class CachedMessage implements Serializable {
		private String sender;
		private Date date;
		private String message;
		
		private CachedMessage() {}
		public CachedMessage(String sender, String message) {
			this.sender = sender;
			this.date = new Date();
			this.message = message;
		}
		
		@Override public String toString() {
			return String.format("%s <%s> %s", this.date, this.sender, this.message);
		}
	}
	
	private Map<String, LinkedList<CachedMessage>> messages = new HashMap<>();

	@Command(value = "\\.(?:tell|ask) ([^ ]+) (.+)", allowPM = false)
	public void tell(CommandContext ctx, String nick, String userMessage) {
		if(nick.equals(ctx.getMessageSender())) {
			ctx.respond("#error That's you");
		} else if(this.bot.isOnline(nick)) {
			ctx.respond("#error They're here now");
		} else {
			if(!this.messages.containsKey(nick)) {
				this.messages.put(nick, new LinkedList<>());
			}
			this.messages.get(nick).add(new CachedMessage(ctx.getMessageSender(), userMessage));
			this.save();
			ctx.respond("#success Queued");
		}
	}
	
	@Override protected void joined(String nick) {
		if(this.messages.containsKey(nick)) {
			for(CachedMessage message : this.messages.get(nick)) {
				this.bot.sendMessage("%s: %s", nick, "" + message);
			}
			this.messages.remove(nick);
			this.save();
		}
	}

	@Override public String getFriendlyName() {return "Tell";}
	@Override public String getDescription() {return "Queues messages for offline users (in-channel MemoServ)";}
	@Override public String[] getExamples() {
		return new String[] {
				".tell _nick_ _message_ -- Queue _message_ to be sent when _nick_ joins",
				".ask _nick_ _question_ -- Same as .tell"
		};
	}
}

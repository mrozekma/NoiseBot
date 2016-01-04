package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import main.Message;
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
		
		@Override public String toString() {return this.date + " <" + this.sender + "> " + this.message;}
	}
	
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_SUCCESS = GREEN;
	
	private Map<String, LinkedList<CachedMessage>> messages = new HashMap<String, LinkedList<CachedMessage>>();
	
	@Command("\\.(?:tell|ask) ([^ ]+) (.+)")
	public void tell(Message message, String nick, String userMessage) {
		if(nick.equals(message.getSender())) {
			message.respond(COLOR_ERROR + "That's you");
		} else if(this.bot.isOnline(nick)) {
			message.respond(COLOR_ERROR + "They're here now");
		} else {
			if(!this.messages.containsKey(nick)) {this.messages.put(nick, new LinkedList<CachedMessage>());}
			this.messages.get(nick).add(new CachedMessage(message.getSender(), userMessage));
			this.save();
			message.respond(COLOR_SUCCESS + "Queued");
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

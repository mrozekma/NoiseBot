package modules;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import main.*;

import org.json.JSONException;

/**
 * Seen
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Seen extends NoiseModule implements Serializable {
	private Map<String, Date> seenDates = new HashMap<>();
	private Map<String, Date> talkDates = new HashMap<>();

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("here", Style.GREEN);
			put("last_seen", Style.YELLOW);
			put("never_seen", Style.RED);
		}};
	}

	@Command("\\.seen (.+)")
	public JSONObject seen(CommandContext ctx, String target) throws JSONException {
		target = target.replaceAll(" +$", "");
		final JSONObject rtn = new JSONObject();
		rtn.put("who", target);
		if(this.bot.isOnline(target)) {
			rtn.put("last_online", "now");
		} else if(this.seenDates.containsKey(target)) {
			rtn.put("last_online", this.seenDates.get(target));
		}
		if(this.talkDates.containsKey(target)) {
			rtn.put("last_spoke", this.talkDates.get(target));
		}
		return rtn;
	}

	@View(method = "seen")
	public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
		if(data.has("last_online")) {
			if(data.optString("last_online", "").equals("now")) {
				if(data.has("last_spoke")) {
					ctx.respond("#here %s is here now -- last spoke %s", data.get("who"), data.get("last_spoke"));
				} else {
					ctx.respond("#here %s is here now", data.get("who"));
				}
			} else {
				ctx.respond("#last_seen %s was last seen %s", data.get("who"), data.get("last_online"));
			}
		} else {
			ctx.respond("#never_seen %s hasn't been seen", data.get("who"));
		}
	}

	@Override public MessageResult processMessage(Message message) throws InvocationTargetException {
		this.talkDates.put(message.getSender(), new Date());
		this.save();
		return super.processMessage(message);
	}

	@Override protected void left(String nick) {
		this.seenDates.put(nick, new Date());
		this.save();
	}

	@Override public String getFriendlyName() {return "Seen";}
	@Override public String getDescription() {return "Reports the last time a nick was in-channel";}
	@Override public String[] getExamples() {
		return new String[] {
				".seen _nick_ -- Returns the last time the specified nick was in-channel"
		};
	}
}

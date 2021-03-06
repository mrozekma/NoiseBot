package modules;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.*;

import main.*;
import org.json.JSONException;

import static main.Utilities.*;

/**
 * Spook
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Spook extends NoiseModule implements Serializable {
	private static class Entry {
		public final String entry;

		public Entry(String entry) {
			this.entry = entry;
		}

		public JSONObject pack() throws JSONException {
			return new JSONObject().put("entry", this.entry);
		}

		public static Entry unpack(JSONObject json) throws JSONException {
			if(json.has("who") && json.has("when")) {
				return new CustomEntry(json.getString("who"), (Date)json.get("what"), json.getString("entry"));
			} else {
				return new Entry(json.getString("entry"));
			}
		}

		@Override public boolean equals(Object o) {
			if(!(o instanceof Entry)) {
				return false;
			}
			final Entry other = (Entry)o;
			return this.entry.equalsIgnoreCase(other.entry);
		}

		@Override public String toString() {
			return this.entry;
		}
	}

	private static class CustomEntry extends Entry {
		public final String who;
		public final Date when;

		public CustomEntry(String who, Date when, String entry) {
			super(entry);
			this.who = who;
			this.when = when;
		}

		public CustomEntry(String who, String entry) {
			this(who, new Date(), entry);
		}

		public JSONObject pack() throws JSONException {
			return super.pack().put("who", this.who).put("when", this.when);
		}

		// Intentionally not overriding equals(); entry text should be unique
	}

	private static File SPOOK_FILE = NoiseBot.getDataFile("spook.lines");

	private transient Entry[] emacsLines;
	private List<CustomEntry> customLines = new Vector<>();

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.emacsLines = new Entry[0];
		try {
			this.emacsLines = Files.lines(Paths.get(SPOOK_FILE.toURI())).map(line -> new Entry(substring(line, 0, -1))).toArray(Entry[]::new);
		} catch(NoSuchFileException e) {
			this.bot.sendNotice("No spook lines file found");
		} catch(IOException e) {
			this.bot.sendNotice("Unable to load spook file");
		}
	}

	@Command("(?:\\.spook|:sleuth_or_spy:) ([0-9]+)")
	public JSONObject spook(CommandContext ctx, int num) throws JSONException {
		final int totalLines = this.emacsLines.length + this.customLines.size();
		num = range(num, 1, Math.min(totalLines, 20));
		final Set<Entry> choices = new LinkedHashSet<>();
		while(choices.size() < num) {
			final int i = getRandomInt(0, totalLines - 1);
			choices.add(i < this.emacsLines.length ? this.emacsLines[i] : this.customLines.get(i - this.emacsLines.length));
		}
		final List<JSONObject> packedChoices = new LinkedList<>();
		for(Entry e : choices) {
			packedChoices.add(e.pack());
		}
		return new JSONObject().put("spook", packedChoices);
	}

	@Command("\\.spook|:sleuth_or_spy:")
	public JSONObject spookDefault(CommandContext ctx) throws JSONException {
		return this.spook(ctx, 10);
	}

	@View
	public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
		final JSONArray entries = data.getJSONArray("spook");
		final Object[] args = entries.flatValueStream("entry");
		ctx.respond("#(%s)", (Object)args);
	}

	@Command("\\.spookadd (.+)")
	public void spookadd(CommandContext ctx, String spook_) {
		final String spook = spook_.trim();
		if(!spook.matches("^[a-zA-Z0-9][a-zA-Z0-9 _.-]+")) {
			Slap.slap(this.bot, ctx);
		} else if(Arrays.stream(this.emacsLines).map(entry -> entry.entry).anyMatch(spook::equalsIgnoreCase)) {
			ctx.respond("#error Entry already exists");
		} else if(this.customLines.stream().anyMatch(entry -> entry.entry.equalsIgnoreCase(spook))) {
			final CustomEntry entry = this.customLines.stream().filter(e -> e.entry.equalsIgnoreCase(spook)).findAny().get();
			ctx.respond("#error Entry already exists (added by %s, %s)", entry.who, entry.when);
		} else {
			this.customLines.add(new CustomEntry(ctx.getMessageSender(), spook));
			this.save();
			ctx.respond("Added");
		}
	}

	@Command("\\.spookrm (.+)")
	public void spookrm(CommandContext ctx, String spook_) {
		final String spook = spook_.trim();
		for(ListIterator<CustomEntry> iter = this.customLines.listIterator(); iter.hasNext();) {
			final CustomEntry entry = iter.next();
			if(entry.entry.equalsIgnoreCase(spook)) {
				iter.remove();
				this.save();
				ctx.respond("Removed entry added by %s, %s", entry.who, entry.when);
				return;
			}
		}
		ctx.respond("#error Entry does not exist");
	}

	@Override public String getFriendlyName() {return "Spook";}
	@Override public String getDescription() {return "Displays a random line from the Emacs spook file";}
	@Override public String[] getExamples() {
		return new String[] {
				".spook",
				".spook 15",
				".spookadd _phrase_ -- Add custom spook entry",
				".spookrm _phrase_ -- Remove custom spook entry",
		};
	}
}

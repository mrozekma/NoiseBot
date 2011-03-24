package modules;

import static org.jibble.pircbot.Colors.*;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import panacea.MapFunction;

import main.Message;
import main.NoiseModule;
import static panacea.Panacea.*;

/**
 * Choose
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Choose extends NoiseModule {
	private static final String CHOICE_COLOR = BLUE;
	
	private String lastOpts = null;
	
	@Command("\\.(?:choose|choice) (.*)")
	public void choose(Message message, String opts) {
		this.lastOpts = opts;
		final Set<String> options = new TreeSet<String>();
		options.addAll(Arrays.asList(map(opts.split(","), new MapFunction<String, String>() {
			@Override public String map(String source) {
				return source.trim();
			}
		})));
		
		if(options.size() > 1)
			this.bot.reply(message, CHOICE_COLOR + getRandom(options.toArray(new String[0])).trim());
		else
			this.bot.reply(message, "You're having me choose from a set of one...fine, " + CHOICE_COLOR + options.iterator().next());
	}
	
	@Command("\\.rechoose")
	public void rechoose(Message message) {
		if(this.lastOpts == null) {
			this.bot.sendMessage("Perhaps you should have me make a choice first");
		} else {
			choose(message, this.lastOpts);
		}
	}
	
	@Override public String getFriendlyName() {return "Choose";}
	@Override public String getDescription() {return "Returns a randomly selected entry from the specified list";}
	@Override public String[] getExamples() {
		return new String[] {
				".choose _opt1_,_opt2_,... -- Returns a random entry from the comma-separated list",
				".choice _opt1_,_opt2_,... -- Same as above",
				".rechoose -- Run the last .choose again"
		};
	}
}

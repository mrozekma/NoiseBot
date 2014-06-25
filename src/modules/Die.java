package modules;

import static org.jibble.pircbot.Colors.*;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.Queue;

import java.lang.reflect.Field;
import java.util.Map;

import main.Message;
import main.ModuleLoadException;
import main.NoiseBot;
import main.NoiseModule;

public class Die extends NoiseModule {
	public Queue queue = null;

	@Override public void init(NoiseBot bot, Map<String, String> config) throws ModuleLoadException {
		super.init(bot, config);

		/* Damn java programmers */
		try {
			Class klass = bot.getClass().getSuperclass();
			Field field = klass.getDeclaredField("_outQueue");
			field.setAccessible(true);
			this.queue = (Queue)field.get(bot);
		} catch (NoSuchFieldException e) {
			bot.sendMessage("No field found");
		} catch (IllegalAccessException e) {
			bot.sendMessage("No access");
		}
	}

	@Command("\\.die(?:diedie)?") public void die(Message message) {
		if (this.queue != null) {
			this.queue.clear();
			this.bot.sendMessage(RED + "Aaaarrrggghhhh..");
		}
	}

	@Override public String getFriendlyName() {return "Die";}
	@Override public String getDescription() {return "Kills the queue";}
	@Override public String[] getExamples() { return new String[] { ".die" }; }
}

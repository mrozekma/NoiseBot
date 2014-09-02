package modules;

import static org.jibble.pircbot.Colors.*;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.Queue;

import debugging.Log;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import main.Message;
import main.ModuleInitException;
import main.NoiseBot;
import main.NoiseModule;

public class Die extends NoiseModule {
	@Command("\\.die(?:diedie)?") public void die(Message message) {
		if(this.bot.clearPendingSends()) {
			this.bot.sendMessage(RED + "Aaaarrrggghhhh..");
		} else {
			this.bot.sendMessage(RED + "Unable to modify outqueue");
		}
	}

	@Override public String getFriendlyName() {return "Die";}
	@Override public String getDescription() {return "Kills the queue";}
	@Override public String[] getExamples() { return new String[] { ".die" }; }
}

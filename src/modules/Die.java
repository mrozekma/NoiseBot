package modules;

import main.*;

public class Die extends NoiseModule {
	@Override public void init(NoiseBot bot) throws ModuleInitException {
		if(bot.getProtocol() != Protocol.IRC) {
			throw new ModuleInitException("This module is IRC-specific");
		}
	}

	@Command("\\.die(?:diedie)?") public void die(Message message) {
		if(this.bot.clearPendingSends()) {
			this.bot.sendMessage("#red Aaaarrrggghhhh..");
		} else {
			this.bot.sendMessage("#red Unable to modify outqueue");
		}
	}

	@Override public String getFriendlyName() {return "Die";}
	@Override public String getDescription() {return "Kills the queue";}
	@Override public String[] getExamples() { return new String[] { ".die" }; }
}

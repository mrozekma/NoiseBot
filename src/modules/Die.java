package modules;

import main.*;

public class Die extends NoiseModule {
	@Command("\\.die(?:diedie)?")
	public void die(CommandContext ctx) {
		if(this.bot.clearPendingSends()) {
			this.bot.sendMessage("#red Aaaarrrggghhhh..");
		} else {
			this.bot.sendMessage("#red Unable to modify outqueue");
		}
	}

	@Override public boolean supportsProtocol(Protocol protocol) {
		return protocol == Protocol.IRC;
	}

	@Override public String getFriendlyName() {return "Die";}
	@Override public String getDescription() {return "Kills the queue";}
	@Override public String[] getExamples() { return new String[] { ".die" }; }
}

package modules;

import static org.jibble.pircbot.Colors.*;

import main.Message;
import main.NoiseModule;
import static panacea.Panacea.*;

public class Foo extends NoiseModule {
	@Command("\\.foo") public void foo(Message message) {this.bot.reply(message, "Updated!");}

	@Override public String getFriendlyName() {return "Foo";}
	@Override public String getDescription() {return "DCC test module";}
	@Override public String[] getExamples() {return null;}
}

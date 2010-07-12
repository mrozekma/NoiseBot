package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

import main.Message;
import main.NoiseModule;

//TODO Fix line splitting

/**
 * QDB
 *
 * @author Michael Mrozek
 *         Created Jul 12, 2010.
 */
public class QDB extends NoiseModule {
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_QUOTE = CYAN;
	
	@Command("\\.(?:qdb|quote) ([0-9]+)")
	public void show(Message message, int id) {
		try {
			String url = "http://mrozekma.com/qdb.php";
			if(id > 0)
				url += "?id=" + id;
			final URLConnection c = new URL(url).openConnection();
			final Scanner s = new Scanner(c.getInputStream());
			while(s.hasNextLine()) {
				final String line = s.nextLine();
				this.bot.sendMessage(COLOR_QUOTE + line);
			}
		} catch(IOException e) {
			this.bot.reply(message, COLOR_ERROR + "Unable to connect to QDB");
			e.printStackTrace();
		}
	}

	@Command("\\.(?:qdb|quote)")
	public void showRandom(Message message) {
		show(message, 0);
	}

	@Override public String getFriendlyName() {return "QDB";}
	@Override public String getDescription() {return "Displays quotes from the RHLUG Quote Database at http://lug.rose-hulman.edu/qdb/";}
	@Override public String[] getExamples() {
		return new String[] {
				".qdb -- Shows a random short quote",
				".qdb _id_ -- Shows quote _id_",
				".quote _id_ -- Same as .qdb"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}

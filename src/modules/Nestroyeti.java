package modules;

import java.io.IOException;

import main.Message;
import main.NoiseModule;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Created by whitelje on 2/7/14.
 */
public class Nestroyeti extends NoiseModule {

    @Override
    public String getFriendlyName() {
        return "Nestroyeti";
    }

    @Override
    public String getDescription() {
        return "Returns the current temperature in the home of necroyeti";
    }

    @Override
    public String[] getExamples() {
        return new String[] {
                ".nestroyeti"
        };
    }

    @Command("\\.nestroyeti")
    public void getTemp(Message message) {
        try{
        	Document page = Jsoup.connect("http://phire.org/nest/").ignoreContentType(true).get();
        	this.bot.sendMessage(page.text());
        } catch (IOException e) {
            e.printStackTrace();
            this.bot.sendMessage("Error retrieving yeti weather...");
        }

    }


}

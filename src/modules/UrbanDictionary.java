package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import main.Message;
import main.NoiseModule;
import static main.Utilities.*;

/**
 * Urban Dictionary module
 *
 * @author Will Fuqua
 *         Created Nov 5, 2011
 */
public class UrbanDictionary extends NoiseModule {
	private static final int MAXIMUM_MESSAGE_LENGTH = 400; // Approximately (512 bytes including IRC data)
	private static final String COLOR_WARNING = RED;
	private static final String COLOR_ERROR = RED + REVERSE;
	private static final String URBAN_URL = "http://www.urbandictionary.com/iphone/search/define?term=";
	
	@Command("\\.(?:urbandictionary|urban) (.+)")
	public void urban(Message message, String term) {
		if(term.isEmpty()) { // Should be impossible
			this.bot.sendMessage(COLOR_ERROR + "Missing term");
			return;
		}
		
		sendDefinition(term);
	}
	
	private void sendDefinition(String term) {
		String definitionText = "";
		String definitionUrl = "";
		
		try {
			// actually make the api call
			JSONObject result = getJSON(URBAN_URL + urlEncode(term));
			
			// disregard results that are not exact (like search results)
			String matchType = result.getString("result_type");
			if(!"exact".equals(matchType)) {
				this.bot.sendMessage(COLOR_ERROR + "No results");
				return;
			}
			
			// dive into the json object and extract the highest rated definition
			JSONObject bestDefinition = null;
			int bestScore = Integer.MIN_VALUE;
			JSONArray definitions = result.getJSONArray("list");
			JSONObject temp;
			for(int i = 0; i < definitions.length(); i++) {
				temp = definitions.getJSONObject(i);
				int thisScore = temp.getInt("thumbs_up");
				if(thisScore > bestScore) {
					bestDefinition = temp;
					bestScore = thisScore;
				}
			}
			
			if(bestDefinition == null) {
				this.bot.sendMessage(COLOR_ERROR + "No results");
				return;
			}
			
			
			definitionText = bestDefinition.getString("definition").replaceAll("\\r\\n|\\r|\\n", " ");
			definitionUrl = bestDefinition.getString("permalink");
		} catch (IOException | JSONException e) {
			e.printStackTrace();
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing urban dictionary JSON data");
		}
		
		// truncate the definition if it's too long. We need to append the URL. 
		if(definitionText.length() + definitionUrl.length() > MAXIMUM_MESSAGE_LENGTH)
			definitionText = definitionText.substring(0, MAXIMUM_MESSAGE_LENGTH - definitionUrl.length() - 1);
		
		this.bot.sendMessage(definitionText + " " + definitionUrl);
		
	}
	
	@Override public String getFriendlyName() {return "UrbanDictionary";}
	@Override public String getDescription() {return "Looks up a term in urbandictionary.com";}
	@Override public String[] getExamples() {
		return new String[] {
				".urban _term_ -- Look up a term",
				".urbandictionary _term_ -- Look up a term"
		};
	}
}

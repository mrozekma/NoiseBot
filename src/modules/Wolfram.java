package modules;

import debugging.Log;
import main.JSONObject;
import main.Message;
import main.NoiseModule;

import com.wolfram.alpha.WAEngine;
import com.wolfram.alpha.WAException;
import com.wolfram.alpha.WAPlainText;
import com.wolfram.alpha.WAPod;
import com.wolfram.alpha.WAQuery;
import com.wolfram.alpha.WAQueryResult;
import main.Style;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.*;
import java.util.stream.Stream;

/**
 * Wolfram Alpha integration
 * 
 * @author Will Fuqua 
 * 		   Created Oct 31, 2011. Halloween, bitches
 */
public class Wolfram extends NoiseModule {
	private static final String WOLFRAM_API_ID = "G755Y4-JH24VP3RV7";
	private static final WAEngine wolfram = new WAEngine();

	public Wolfram() {
		wolfram.setAppID(WOLFRAM_API_ID);
		wolfram.addFormat("plaintext");
	}

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("warning", Style.RED);
			put("bracket", Style.MAGENTA);
			put("text", Style.PLAIN);
		}};
	}

	@Command("\\.(?:wolfram|wolf) (.+)")
	public JSONObject wolfram(Message message, String term) throws JSONException {
		// this will be our returned object
		String resultText = null;
		
		// Create the query.
		final WAQuery query = wolfram.createQuery();
		query.setInput(term);
		
		try {
			WAQueryResult queryResult = wolfram.performQuery(query);

			final JSONObject rtn = new JSONObject().put("term", term);
			if(queryResult.isError()) {
				rtn.put("error", queryResult.getErrorMessage());
			} else if(!queryResult.isSuccess()) {
				rtn.put("warning", "No results available");
			} else {
				this.formatResult(queryResult, rtn);
			}
			return rtn;
		} catch (WAException e) {
			Log.e(e);
			return new JSONObject().put("error", e.getMessage());
		}
	}

	private void formatResult(WAQueryResult queryResult, JSONObject dst) throws JSONException {
		// work with Wolfram Alpha's weird API. The website's user interface pretty much mirrors the queryResult structure
		// we have Pods, which have SubPods, which have Content Objects. We care about the Content Objects that are WAPlainText objects
		
		WAPod[] answerPods = queryResult.getPods();
		final JSONObject rtn= new JSONObject();
		for(WAPod pod : answerPods) {
			// ignore the input interpretation
			if(pod.getTitle().startsWith("Input")) {
				continue;
			}

			// grab all the content from the subpods
			Stream<Object> contentsStream = Arrays.stream(pod.getSubpods()).map(subpod -> subpod.getContents()[0]);
			
			// filter the content down to the WAPlainText objects
			Stream<WAPlainText> textContentStream = contentsStream.filter(content -> content instanceof WAPlainText).map(content -> (WAPlainText)content);

			final List<String> arr = new LinkedList<>();
			textContentStream
					.map(source -> source.getText().replace(" | ", ": "))
					.filter(source -> !source.isEmpty())
					.map(source -> Arrays.asList(source.split("\n")))
					.forEach(arr::addAll);

			if(!arr.isEmpty()) {
				dst.put("title", pod.getTitle());
				dst.put("results", new JSONArray(arr.toArray(new String[0])));
				return;
			}
		}
		
		dst.put("warning", "No results available");
	}

	@View
	public void plainView(Message message, JSONObject data) throws JSONException {
		if(data.has("warning")) {
			message.respond("#warning %s", data.get("warning"));
			return;
		}

		message.respond("%s: #([ ] #bracket [#text %s#bracket ])", data.get("title"), data.getStringArray("results"));
	}

	@Override
	public String getFriendlyName() {
		return "Wolfram";
	}

	@Override
	public String getDescription() {
		return "Query Wolfram Alpha - see http://www.wolframalpha.com/examples/";
	}

	@Override
	public String[] getExamples() {
		return new String[] { ".wolfram duck", ".wolf integrate x^3" };
	}
}

package modules;

import panacea.Condition;
import panacea.MapFunction;
import panacea.Panacea;
import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import com.wolfram.alpha.WAEngine;
import com.wolfram.alpha.WAException;
import com.wolfram.alpha.WAPlainText;
import com.wolfram.alpha.WAPod;
import com.wolfram.alpha.WAQuery;
import com.wolfram.alpha.WAQueryResult;
import com.wolfram.alpha.WASubpod;
import com.wolfram.alpha.visitor.Visitable;

import static panacea.Panacea.*;
import static org.jibble.pircbot.Colors.*;

/**
 * Wolfram Alpha integration
 * 
 * @author Will Fuqua 
 * 		   Created Oct 31, 2011. Halloween, bitches
 */
public class Wolfram extends NoiseModule {
	private static final String COLOR_ERROR = RED + REVERSE;
	private static final String COLOR_WARNING = RED;
	private static final String COLOR_BRACKET = PURPLE;
	private static final String COLOR_TEXT = NORMAL;
	private static final String WOLFRAM_API_ID = "G755Y4-JH24VP3RV7";
	private static final WAEngine wolfram = new WAEngine();

	public Wolfram() {
		wolfram.setAppID(WOLFRAM_API_ID);
		wolfram.addFormat("plaintext");
	}
	
	@Command("\\.(?:wolfram|wolf) (.+)")
	public void wolfram(Message message, String term) {
		if (term.isEmpty()) { // Should be impossible
			this.bot.sendMessage(COLOR_ERROR + "Missing term");
			return;
		}

		String response = queryWolfram(term);

		this.bot.sendMessage(response);
	}

	private String queryWolfram(String term) {

		// this will be our returned object
		String resultText = null;
		
		// Create the query.
		final WAQuery query = wolfram.createQuery();
		query.setInput(term);
		
		try {

			WAQueryResult queryResult = wolfram.performQuery(query);
			
			resultText = queryResult.isError() ? COLOR_ERROR + encoded(queryResult.getErrorMessage()) :
						 !queryResult.isSuccess() ? COLOR_WARNING + "No results available" :
						 formatResult(queryResult);

		} catch (WAException e) { 
			e.printStackTrace();
		}
		
		return resultText;
	}

	private String formatResult(WAQueryResult queryResult) {
		
		// work with Wolfram Alpha's weird API. The website's user interface pretty much mirrors the queryResult structure
		// we have Pods, which have SubPods, which have Content Objects. We care about the Content Objects that are WAPlainText objects
		
		WAPod[] answerPods = queryResult.getPods();
		
		for(WAPod pod : answerPods) {
			
			// ignore the input interpretation
			if(pod.getTitle().startsWith("Input"))
				continue;

			// grab all the content from the subpods
			Object[] contents = map(pod.getSubpods(), new MapFunction<WASubpod, Object>() {
				@Override public Object map(WASubpod subpod) { return subpod.getContents()[0]; }
			});
			
			// filter the content down to the WAPlainText objects
			WAPlainText[] textContent = (WAPlainText[])filter(contents, new Condition<Object>() {
				@Override public boolean satisfies(Object content) { return content instanceof WAPlainText; }
			});
			
			// extract the text from the PlainText objects and format it
			String[] formattedContent = map(textContent, new MapFunction<WAPlainText, String>() {
				@Override
				public String map(WAPlainText source) {
					// wolfram api uses newlines and pipes to separate data
					String[] answerParts = source.getText().replace(" | ", ": ").split("\n");
					return implode(Panacea.map(answerParts, new MapFunction<String, String>() {
						@Override
						public java.lang.String map(String source) {
							return source == "" ? "" : COLOR_BRACKET + "[" + COLOR_TEXT + encoded(source.trim()) + COLOR_BRACKET + "]";
						}
					}), " ");
				}
			});
			
			String answer = implode(formattedContent, " ");
			
			// if we have a non-empty answer, return it
			if(!answer.trim().isEmpty())
				return encoded(pod.getTitle()) + ": " + answer;
			
		}
		
		return COLOR_WARNING + "No results available";
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

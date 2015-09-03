package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

/**
 * Bitcoin
 *
 * @author Michael Mrozek
 *         Created Mar 12, 2014.
 */
public class Bitcoin extends NoiseModule {
	private static class Price {
		public final double bid, ask, avg;
		public Price(double bid, double ask, double avg) {
			this.bid = bid;
			this.ask = ask;
			this.avg = avg;
		}
	}

	private static final String COLOR_ERROR = RED + REVERSE;

	private final Map<String, Price> exchanges = new HashMap<String, Price>();

	@Command("\\.bitcoin ([a-zA-Z]+)")
	public void bitcoin(Message message, String exchange) {
		exchange = exchange.toLowerCase();
		try {
			this.refresh();
			if(this.exchanges.isEmpty()) {
				this.bot.sendMessage(COLOR_ERROR + "No exchanges found");
				return;
			}

			final Price price;
			if(this.exchanges.containsKey(exchange)) {
				price = this.exchanges.get(exchange);
			} else if(this.exchanges.containsKey(exchange + "usd")) {
				price = this.exchanges.get(exchange + "usd");
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown exchange");
				return;
			}

			this.bot.sendMessage(String.format("$%.2f / $%.2f", price.ask, price.bid));
		} catch(Exception e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing data");
		}
	}

	@Command("\\.bitcoin")
	public void bitcoinAvg(Message message) {
		try {
			this.refresh();
			if(this.exchanges.isEmpty()) {
				this.bot.sendMessage(COLOR_ERROR + "No exchanges found");
				return;
			}

			final double avg = this.exchanges.values().stream().filter(p -> p.avg > 0).mapToDouble(p -> p.avg).average().orElse(0.0);
			this.bot.sendMessage(String.format("$%.2f", avg));
		} catch(Exception e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing data");
		}
	}

	@Override public String getFriendlyName() {return "Bitcoin";}
	@Override public String getDescription() {return "Outputs current Bitcoin exchange rates";}
	@Override public String[] getExamples() {
		return new String[] {
			".bitcoin",
			".bitcoin bitstamp"
		};
	}

	private void refresh() throws IOException, JSONException {
		final URLConnection c = new URL("http://api.bitcoincharts.com/v1/markets.json").openConnection();
		final Scanner s = new Scanner(c.getInputStream());
		final StringBuffer buffer = new StringBuffer();
		while(s.hasNextLine()) {
			buffer.append(s.nextLine());
		}

		final JSONArray json = new JSONArray(buffer.toString());
		this.exchanges.clear();
		for(int i = 0; i < json.length(); i++) {
			final JSONObject obj = json.getJSONObject(i);
			if(obj.getString("currency").equals("USD")) {
				final double bid = obj.isNull("bid") ? 0.0 : obj.getDouble("bid");
				final double ask = obj.isNull("ask") ? 0.0 : obj.getDouble("ask");
				final double avg = obj.isNull("avg") ? 0.0 : obj.getDouble("avg");
				this.exchanges.put(obj.getString("symbol").toLowerCase(), new Price(bid, ask, avg));
			}
		}
	}
}

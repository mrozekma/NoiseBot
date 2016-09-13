package modules;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import main.JSONArray;
import main.JSONObject;
import org.json.JSONException;

import debugging.Log;

import main.CommandContext;
import main.NoiseModule;
import main.ViewContext;

/**
 * Bitcoin
 *
 * @author Michael Mrozek
 *         Created Mar 12, 2014.
 */
public class Bitcoin extends NoiseModule {
	public static class Price {
		public final double bid, ask, avg;
		public Price(double bid, double ask, double avg) {
			this.bid = bid;
			this.ask = ask;
			this.avg = avg;
		}

		public JSONObject pack() throws JSONException {
			return new JSONObject().put("bid", this.bid).put("ask", this.ask).put("avg", this.avg);
		}

		public static Price unpack(org.json.JSONObject data) throws JSONException {
			return new Price(data.getDouble("bid"), data.getDouble("ask"), data.getDouble("avg"));
		}
	}

	private final Map<String, Price> exchanges = new HashMap<>();

	@Command("(?:\\.bitcoin|:bitcoin:) ([a-zA-Z]+)")
	public JSONObject bitcoin(CommandContext ctx, String exchange) throws JSONException {
		exchange = exchange.toLowerCase();
		try {
			this.refresh();
			if(this.exchanges.isEmpty()) {
				return new JSONObject().put("error", "No exchanges found");
			}

			final Price price;
			if(this.exchanges.containsKey(exchange)) {
				price = this.exchanges.get(exchange);
			} else if(this.exchanges.containsKey(exchange + "usd")) {
				price = this.exchanges.get(exchange + "usd");
			} else {
				return new JSONObject().put("error", "Unknown exchange");
			}

			return new JSONObject().put("exchange", exchange).put("price", price.pack());
		} catch(Exception e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing data");
		}
	}

	@Command("(?:\\.bitcoin|:bitcoin:)")
	public JSONObject bitcoinAvg(CommandContext ctx) throws JSONException {
		try {
			this.refresh();
			if(this.exchanges.isEmpty()) {
				return new JSONObject().put("error", "No exchanges found");
			}

			final double avg = this.exchanges.values().stream().filter(p -> p.avg > 0).mapToDouble(p -> p.avg).average().orElse(0.0);
			return new JSONObject().put("average", avg);
		} catch(Exception e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing data");
		}
	}

	@View(method = "bitcoin")
	public void plainBitcoinView(ViewContext ctx, JSONObject data) throws JSONException {
		final Price price = Price.unpack(data.getJSONObject("price"));
		ctx.respond("$%.2f / $%.2f", price.ask, price.bid);
	}

	@View(method = "bitcoinAvg")
	public void plainBitcoinAvgView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("$%.2f", data.getDouble("average"));
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

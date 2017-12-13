package modules;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

import main.*;
import org.json.JSONException;

import debugging.Log;

import static main.Utilities.getJSON;

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

	@Command("(?:\\.bitcoin|\\.btc|:bitcoin:) ([a-zA-Z]+)")
	public JSONObject bitcoinExchange(CommandContext ctx, String exchange) throws JSONException {
		try {
			final URLConnection c = new URL("http://api.bitcoincharts.com/v1/markets.json").openConnection();
			final Scanner s = new Scanner(c.getInputStream());
			final StringBuffer buffer = new StringBuffer();
			while(s.hasNextLine()) {
				buffer.append(s.nextLine());
			}

			final JSONArray json = new JSONArray(buffer.toString());
			for(int i = 0; i < json.length(); i++) {
				final JSONObject obj = json.getJSONObject(i);
				if(obj.getString("currency").equals("USD")) {
					final String symbol = obj.getString("symbol");
					if(symbol.equalsIgnoreCase(exchange) || symbol.equalsIgnoreCase(exchange + "usd")) {
						final double bid = obj.isNull("bid") ? 0.0 : obj.getDouble("bid");
						final double ask = obj.isNull("ask") ? 0.0 : obj.getDouble("ask");
						final double avg = obj.isNull("avg") ? 0.0 : obj.getDouble("avg");
						return new JSONObject().put("exchange", symbol).put("price", new Price(bid, ask, avg).pack());
					}
				}
			}

			return new JSONObject().put("error", "Unknown exchange");
		} catch(Exception e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing data");
		}
	}

	@Command("(?:\\.bitcoin|\\.btc|:bitcoin:)")
	public JSONObject bitcoin(CommandContext ctx) throws JSONException {
		return this.coinbase(ctx, "BTC");
	}

	@Command("(?:\\.ethereum|\\.eth)")
	public JSONObject ethereum(CommandContext ctx) throws JSONException {
		return this.coinbase(ctx, "ETH");
	}

	@Command("(?:\\.litecoin|\\.ltc)")
	public JSONObject litecoin(CommandContext ctx) throws JSONException {
		return this.coinbase(ctx, "LTC");
	}

	private JSONObject coinbase(CommandContext ctx, String currency) throws JSONException {
		final JSONObject json;
		try {
			json = getJSON(String.format("https://api.coinbase.com/v2/prices/%s-USD/spot", currency));
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to communicate with Coinbase");
		}

		try {
			final JSONObject rtn = json.getJSONObject("data");
			// Make sure this keys exists
			rtn.getDouble("amount");
			return rtn;
		} catch(JSONException e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing data");
		}
	}

	@View(method = "bitcoinExchange")
	public void plainBitcoinView(ViewContext ctx, JSONObject data) throws JSONException {
		final Price price = Price.unpack(data.getJSONObject("price"));
		ctx.respond("$%.2f ($%.2f / $%.2f)", price.avg, price.ask, price.bid);
	}

	@View
	public void plainCoinbaseView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("$%.2f", data.getDouble("amount"));
	}

	@Override public String getFriendlyName() {return "Bitcoin";}
	@Override public String getDescription() {return "Outputs current crypto currency exchange rates";}
	@Override public String[] getExamples() {
		return new String[] {
			".bitcoin _exchange_ -- Get the most recent advertised price on _exchange_",
			".bitcoin -- Get the live Bitcoin price listed on Coinbase",
			".ethereum -- Get the live Ethereum price listed on Coinbase",
			".litecoin -- Get the live Litecoin price listed on Coinbase",
		};
	}
}

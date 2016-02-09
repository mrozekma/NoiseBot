package modules;

import java.util.Calendar;
import java.util.GregorianCalendar;

import main.*;
import org.json.JSONException;

/**
 * Discordianism
 *
 * @author Michael Mrozek
 *         Created on the 51st day of Confusion in the YOLD 3178
 */
public class Discordianism extends NoiseModule {
	private static String[] SEASONS = {"Chaos", "Discord", "Confusion", "Bureaucracy", "the Aftermath"};
	private static String[] DOTW = {"Sweetmorn", "Boomtime", "Pungenday", "Prickle-Prickle", "Setting Orange"};
	private static String LEAP_DOTW = "St. Tib's Day";
	private static String[] HOLYDAYS = {"Mungday", "Chaoflux", "Mojoday", "Discoflux", "Syaday", "Confuflux", "Zaraday", "Bureflux", "Maladay", "Afflux"};

	@Command("\\.ddate")
	public JSONObject ddate(CommandContext ctx) throws JSONException {
		final JSONObject rtn = new JSONObject();
		final GregorianCalendar now = new GregorianCalendar();
		rtn.put("gregorian", new JSONObject()
			.put("year", now.get(Calendar.YEAR))
			.put("month", now.get(Calendar.MONTH) + 1)
			.put("day", now.get(Calendar.DAY_OF_MONTH)));

		int dayOfYear = now.get(Calendar.DAY_OF_YEAR);

		String dotw = null;
		if(now.isLeapYear(now.get(Calendar.YEAR))) {
			if(dayOfYear == 60) {
				dotw = LEAP_DOTW;
			} else if(dayOfYear > 59) {
				dayOfYear--;
			}
		}

		if(dotw == null) {
			dotw = DOTW[(dayOfYear - 1) % DOTW.length];
		}

		int seasonIndex = dayOfYear / (365 / SEASONS.length);
		int dayOfSeason = (dayOfYear % (365 / SEASONS.length));
		if(dayOfSeason == 0) {
			dayOfSeason = 365 / SEASONS.length;
			seasonIndex--;
		}

		final String season = SEASONS[seasonIndex];
		final int year = now.get(Calendar.YEAR) + 1166;

		final JSONObject obj = new JSONObject()
			.put("dotw", dotw)
			.put("day", dayOfSeason)
			.put("season", season)
			.put("year", year);

		if(dayOfSeason == 5 || dayOfSeason == 50) {
			obj.put("holyday", HOLYDAYS[seasonIndex * 2 + (dayOfSeason == 50 ? 1 : 0)]);
		}

		rtn.put("discordian", obj);
		return rtn;
	}

	@View
	public void view(ViewContext ctx, JSONObject data) throws JSONException {
		// It's sad that this is the most complicated part
		final JSONObject disco = data.getJSONObject("discordian");
		final int dayOfSeason = disco.getInt("day");
		final String daySuffix;
		if(dayOfSeason >= 10 && dayOfSeason < 20) {
			daySuffix = "th";
		} else {
			switch(dayOfSeason % 10) {
			case 1:
				daySuffix = "st";
				break;
			case 2:
				daySuffix = "nd";
				break;
			case 3:
				daySuffix = "rd";
				break;
			default:
				daySuffix = "th";
				break;
			}
		}

		final MessageBuilder builder = ctx.buildResponse();
		builder.add("Today is ");
		if(disco.has("holyday")) {
			builder.add("%s, ", new Object[] {disco.get("holyday")});
		}
		builder.add("%s, the %d%s day of %s in the YOLD %d", new Object[] {disco.get("dotw"), dayOfSeason, daySuffix, disco.get("season"), disco.getInt("year")});
		builder.send();
	}

	@Override public String getFriendlyName() {return "Discordianism";}
	@Override public String getDescription() {return "Displays the current Discordian day";}
	@Override public String[] getExamples() {
		return new String[] {
				".ddate"
		};
	}
}

package modules;

import com.mrozekma.taut.TautAttachment;
import com.mrozekma.taut.TautException;
import debugging.Log;
import main.*;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static main.Utilities.*;

/**
 * EmojiRace
 *
 * @author Michael Mrozek
 *         Created Feb 29, 2016.
 */
public class EmojiRace extends NoiseModule implements Serializable {
	private static class Race implements Serializable {
		final String name;
		final String[] members;
		final Date when;
		String[] steps;
		final Map<String, Float> finishTimes;

		Race(String name, String[] members, Date when) {
			this.name = name;
			this.members = members;
			this.when = when;
			this.steps = null;
			this.finishTimes = new HashMap<>();
		}
		Race(String name, String[] members) {
			this(name, members, getStartDate());
		}

		String[] getFinishOrder() {
			return this.finishTimes.entrySet().stream().sorted((e1, e2) -> e1.getValue().compareTo(e2.getValue())).map(e -> e.getKey()).toArray(String[]::new);
		}

		private static Date getStartDate() {
			final Calendar calendar = new GregorianCalendar();
			calendar.add(Calendar.SECOND, PRERACE_DELAY);
			return calendar.getTime();
		}
	}

	private static class CurrentRace {
		enum State {PRERACE, ABOUT_TO_START, RACING}

		final Race race;
		final SlackSentMessage message;
		final Map<String, Integer> progress;

		final Timer timer = new Timer();
		State state =  State.PRERACE;
		final String[] topFinishers = new String[MEDALS.length]; // Can't have an array of Optional<String>
		final List<String> steps = new LinkedList<>();
		final List<Bet> bets = new LinkedList<>(); // It's important that bets be processed in order so money totals make sense chronologically
		final Map<String, Set<String>> cachedRacerToBettors = new HashMap<>();

		CurrentRace(Race race, SlackSentMessage message) {
			this.race = race;
			this.message = message;
			this.progress = new HashMap<String, Integer>() {{
				for(String member : race.members) {
					put(member, 0);
				}
			}};
		}
	}

	//TODO quinella, exacta, trifecta, superfecta, boxes
	private static abstract class Bet {
		final String bettor;
		final String[] racers;
		final int bet;
		Optional<SlackSentMessage> message = Optional.empty();

		Bet(String bettor, String[] racers, int bet) {
			this.bettor = bettor;
			this.racers = racers;
			this.bet = bet;
		}

		abstract int cost();
		abstract double probability(String[] finishOrder);
		abstract boolean made(String[] finishOrder);

		double payout(String[] finishOrder) {
			final double rtn = this.bet * (1.0 / this.probability(finishOrder));
			return Math.round(rtn * 100) / 100.0;
		}

		@Override public String toString() {
			return String.format("@%s bet $%d on %s", this.bettor, this.bet, Arrays.stream(this.racers).map(racer -> ":" + racer + ":").collect(Collectors.joining(" ")));
		}
	}

	private static class SingleBetTopN extends Bet {
		static String[] KEYWORDS = {"win", "place", "show"};

		final int n;

		SingleBetTopN(String bettor, String racer, int bet, int n) {
			super(bettor, new String[] {racer}, bet);
			this.n = n;
		}

		SingleBetTopN(String bettor, String racer, int bet, String type) {
			this(bettor, racer, bet, keywordToN(type));
		}

		@Override int cost() {
			return this.bet;
		}

		@Override double probability(String[] finishOrder) {
			return (float)this.n / finishOrder.length;
		}

		@Override boolean made(String[] finishOrder) {
			for(int i = 0; i < this.n; i++) {
				if(this.racers[0].equals(finishOrder[i])) {
					return true;
				}
			}
			return false;
		}

		@Override public String toString() {
			return String.format("%s to %s", super.toString(), KEYWORDS[this.n - 1]);
		}

		static int keywordToN(String keyword) {
			for(int i = 0; i < KEYWORDS.length; i++) {
				if(KEYWORDS[i].equals(keyword)) {
					return i + 1;
				}
			}
			throw new IllegalArgumentException("Bad keyword: " + keyword);
		}
	}

	private static final int PRERACE_DELAY = 20; // s
	private static final int LAST_DELAY = 10; // s
	private static final int RACE_DISTANCE = 10;
	private static final int PERIOD = 500; // ms
	private static final int MAX_BETS_INDIVIDUAL_MESSAGES = 5;
	private static final Pattern EMOJI_RE = Pattern.compile("(:[^:]+:|#[0-9])");
	private static final String[] MEDALS = {"gold_medal_first_place", "silver_medal_second_place", "bronze_medal_third_place"};

	private transient SlackNoiseBot bot;
	private List<Race> races = new LinkedList<>();
	private Map<String, Integer> money = new HashMap<>(); // Values are cents
	private transient Optional<CurrentRace> currentRace = Optional.empty();

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.bot = (SlackNoiseBot)bot;
	}

	@Override public void unload() {
		if(this.currentRace.isPresent()) {
			// The race isn't even added to the list until it completes, so we don't resolve any bets until then either.
			// Users can get out of bad bets by unloading the module before the race ends, but not sure what can be done.
			try {
				this.bot.deleteMessage(this.currentRace.get().message);
			} catch(TautException e) {
				Log.e(e);
			}
			this.currentRace.get().timer.cancel();
		}
	}

	private static String[] parseEmoji(String list) {
		return parseEmoji(list, new String[0]);
	}

	private static String[] parseEmoji(String list, String[] numLookup) {
		final List<String> rtn = new LinkedList<>();
		final Matcher matcher = EMOJI_RE.matcher(list);
		while(matcher.find()) {
			final String str = matcher.group(1);
			if(str.charAt(0) == ':') {
				rtn.add(str.substring(1, str.length() - 1));
			} else if(str.charAt(0) == '#') {
				final int n = Integer.parseInt(str.substring(1)) - 1;
				if(n < numLookup.length) {
					rtn.add(numLookup[n]);
				}
			}
		}
		return rtn.toArray(new String[0]);
	}

	@Command(value = "\\.race", allowPM = false)
	public synchronized void raceDefault(CommandContext ctx) {
		this.race(ctx, ":grinning::neutral_face::frowning:");
	}

	@Command(value = "\\.race (.+)", allowPM = false)
	public synchronized void race(CommandContext ctx, String membersStr) {
		if(this.currentRace.isPresent()) {
			ctx.respond("#error One race at a time, fellas");
			return;
		}

		final String[] members = parseEmoji(membersStr);
		if(members.length == 0) {
			ctx.respond("#error No racers");
			return;
		} else if(members.length > 9) {
			ctx.respond("#error The track only holds 9 emoji");
			return;
		}

		final Race race = new Race(String.format("Race #%d", this.races.size() + 1), members);
		this.startRace(ctx, race);
	}

	@Command("\\.bet (?:\\$([0-9]+) )?(?:on )?((?:(?::[^:]+:|#[0-9]) ?)+)(?: ?([a-zA-Z ]+))?")
	public synchronized void bet(CommandContext ctx, String _bet, String membersStr, String type) {
		if(!this.currentRace.isPresent()) {
			ctx.respond("#error No race is organized");
			return;
		}
		final CurrentRace race = this.currentRace.get();
		if(race.state == CurrentRace.State.RACING) {
			ctx.respond("#error Betting is closed");
			return;
		}
		final String[] targets = parseEmoji(membersStr, race.race.members);
		{
			final Set<String> racerSet = new TreeSet<>(Arrays.asList(race.race.members));
			final Set<String> targetSet = new TreeSet<>(Arrays.asList(targets));
			targetSet.removeAll(racerSet);
			if(!targetSet.isEmpty()) {
				// I wonder if real horse betting allows this
				ctx.respond("#error You can't bet on emoji that aren't in the race");
				return;
			}
		}

		final int bet = (_bet == null) ? 1 : Integer.parseInt(_bet);
		if(type == null) {
			type = "win";
		} else {
			type = type.toLowerCase();
			if(type.startsWith("to ")) {
				type = type.substring(3);
			}
		}

		final String bettor = ctx.getMessageSender();
		final List<Bet> bets = new LinkedList<>();
		if(type.equals("win") || type.equals("place") || type.equals("show")) {
			for(String target : targets) {
				bets.add(new SingleBetTopN(bettor, target, bet, type));
			}
		} else if(type.equals("win-place")) {
			for(String target : targets) {
				bets.add(new SingleBetTopN(bettor, target, bet, "win"));
				bets.add(new SingleBetTopN(bettor, target, bet, "place"));
			}
		} else if(type.equals("across the board")) {
			for(String target : targets) {
				bets.add(new SingleBetTopN(bettor, target, bet, "win"));
				bets.add(new SingleBetTopN(bettor, target, bet, "place"));
				bets.add(new SingleBetTopN(bettor, target, bet, "show"));
			}
		} else {
			ctx.respond("#error Unrecognized bet type");
			return;
		}

		race.bets.addAll(bets);
		if(bets.size() >= MAX_BETS_INDIVIDUAL_MESSAGES) {
			this.bot.sendMessage("Placed %d bets (too many to report individual results)");
		}
		for(Bet b : bets) {
			if(bets.size() < MAX_BETS_INDIVIDUAL_MESSAGES) {
				b.message = Optional.of((SlackSentMessage)this.bot.sendMessage(":question: %s", b)[0]);
			}
			for(String emoji : b.racers) {
				race.cachedRacerToBettors.computeIfAbsent(emoji, b2 -> new TreeSet<>()).add(bettor);
			}
		}
	}

	private synchronized void startRace(CommandContext ctx, Race race) {
		final TautAttachment attachment = this.bot.makeAttachment();
		attachment.setTitle(race.name);
		attachment.setText("A new race is about to begin\n" + Arrays.stream(race.members).map(member -> String.format(":%s:   ", member)).collect(Collectors.joining()), true);
		try {
			this.currentRace = Optional.of(new CurrentRace(race, this.bot.sendAttachment(attachment)));
		} catch(TautException e) {
			Log.e(e);
			ctx.respond("#error %s", exceptionString(e));
			return;
		}
		this.updateMessage(this.currentRace.get());
		this.currentRace.get().timer.scheduleAtFixedRate(new TimerTask() {
			@Override public void run() {
				EmojiRace.this.tick();
			}
		}, 1000, PERIOD);
	}

	private synchronized void endRace() {
		final CurrentRace race = this.currentRace.get();

		// Resolve bets
		// If people manage to over/underflow their money, I've decided I'm ok with that happening
		final String[] finishOrder = race.race.getFinishOrder();
		for(Bet bet : race.bets) {
			if(bet.made(finishOrder)) {
				final double profit = bet.payout(finishOrder) - bet.bet;
				this.money.put(bet.bettor, this.money.getOrDefault(bet.bettor, 0) + (int)(profit * 100));
				if(bet.message.isPresent()) {
					bet.message.get().edit(":white_check_mark: %s and won $%.2f. Up to $%.2f", bet, profit, this.money.get(bet.bettor) / 100.0);
				}
			} else {
				this.money.put(bet.bettor, this.money.getOrDefault(bet.bettor, 0) - bet.bet * 100);
				if(bet.message.isPresent()) {
					bet.message.get().edit(":x: %s. Down to $%.2f", bet, this.money.get(bet.bettor) / 100.0);
				}
			}
		}

		// Finish the Race instance and save it to the race list
		race.race.steps = race.steps.toArray(new String[0]);
		this.races.add(race.race);

		this.save();
		this.currentRace = Optional.empty();
	}

	private synchronized void updateMessage(CurrentRace race) {
		final Date now = new Date();
		final float ms = now.getTime() - race.race.when.getTime();

		// Move to the next state if it's time; otherwise bail out now if it's pre-race to avoid spamming slack with duplicate messages
		switch(race.state) {
		case PRERACE:
			if(ms / 1000 < -LAST_DELAY) {
				return;
			} else {
				race.state = CurrentRace.State.ABOUT_TO_START;
			}
			break;
		case ABOUT_TO_START:
			if(ms < 0) {
				return;
			} else {
				race.state = CurrentRace.State.RACING;
			}
			break;
		}

		final List<String> text = new LinkedList<>();
		switch(race.state) {
		case PRERACE:
			// Never happens (message sent by startRace())
			break;
		case ABOUT_TO_START:
			text.add(String.format("Race begins in %s", pluralize(-Math.round(ms / 1000), "second", "seconds")));
			break;
		case RACING:
			if(ms < 3000) {
				text.add("*GO!*");
			}
			break;
		}

		for(String member : race.race.members) {
			final StringBuilder track = new StringBuilder("| ");
			int i;
			for(i = 0; i < race.progress.get(member); i++) {
				track.append("   ");
			}
			track.append(":" + member + ":");
			if(i == RACE_DISTANCE) {
				final float finished = race.race.finishTimes.computeIfAbsent(member, m -> {
					for(int j = 0; j < race.topFinishers.length; j++) {
						if(race.topFinishers[j] == null) {
							race.topFinishers[j] = m;
							break;
						}
					}
					return ms;
				});
				int j;
				for(j = 0; j < race.topFinishers.length; j++) {
					if(member.equals(race.topFinishers[j])) {
						track.append(String.format(" :%s:", MEDALS[j]));
						break;
					}
				}
				if(j == race.topFinishers.length) {
					track.append("         ");
				}
				if(member.equals(race.topFinishers[0])) {
					track.append(String.format("  (%.2fs)", finished / 1000));
				} else {
					track.append(String.format("  (+%.2fs)", (finished - race.race.finishTimes.get(race.topFinishers[0])) / 1000));
				}
			} else {
				for(; i < RACE_DISTANCE; i++) {
					track.append("   ");
				}
				track.append("|");
			}
			if(race.cachedRacerToBettors.containsKey(member)) {
				track.append(String.format(" (%s)", race.cachedRacerToBettors.get(member).stream().map(nick -> this.bot.formatUser(nick)).collect(Collectors.joining(" "))));
			}
			text.add(track.toString());
		}

		if(race.topFinishers[0] != null) {
			text.add(0, String.format("*:%s: WINS!*", race.topFinishers[0]));
		}

		final TautAttachment attachment = this.bot.makeAttachment();
		attachment.setTitle(race.race.name);
		attachment.setText(text.stream().collect(Collectors.joining("\n")), true);
		try {
			this.bot.editAttachment(race.message, attachment);
		} catch(TautException e) {
			this.bot.reportErrorTo(race.message.getTautMessage().getChannel(), e);
		}
	}

	private synchronized void tick() {
		final CurrentRace race = this.currentRace.get();
		final Date now = new Date();

		if(now.getTime() >= race.race.when.getTime()) {
			final Vector<String> candidates = new Vector<>(Arrays.asList(race.race.members));
			candidates.removeAll(race.race.finishTimes.keySet());
			if(candidates.isEmpty()) {
				race.timer.cancel();
				this.endRace();
			} else {
				// 1 in 3n chance that nobody moves, for number of racers 'n'
				if(getRandomInt(1, 3 * race.race.members.length) == 1) {
					return;
				}
				final String choice = getRandom(candidates.toArray(new String[0]));
				race.steps.add(choice);
				final int distance = race.progress.get(choice) + 1;
				race.progress.put(choice, distance);
			}
		}

		this.updateMessage(race);
	}

	@Override public boolean supportsProtocol(Protocol protocol) {
		return protocol == Protocol.Slack;
	}

	@Override public String getFriendlyName() {return "EmojiRace";}
	@Override public String getDescription() {return "Force the emoji to race. Like dogs";}
	@Override public String[] getExamples() {
		return new String[] {
			".race",
			".race :fearful: :cold_sweat: :scream: :rage:",
			".bet :scream:",
			".bet $10 on :rage: to show",
		};
	}
}

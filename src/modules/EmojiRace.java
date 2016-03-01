package modules;

import com.ullink.slack.simpleslackapi.SlackAttachment;
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
		Optional<String> winner;

		Race(String name, String[] members, Date when) {
			this.name = name;
			this.members = members;
			this.when = when;
			this.winner = Optional.empty();
		}
		Race(String name, String[] members) {
			this(name, members, getStartDate());
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
		final Map<String, Integer> finalTime;
		final Timer timer;
		State state;

		CurrentRace(Race race, SlackSentMessage message) {
			this.race = race;
			this.message = message;
			this.progress = new HashMap<String, Integer>() {{
				for(String member : race.members) {
					put(member, 0);
				}
			}};
			this.finalTime = new HashMap<>();
			this.timer = new Timer();
			this.state = State.PRERACE;
		}
	}

	private static final int PRERACE_DELAY = 20; // s
	private static final int LAST_DELAY = 5; // s
	private static final int RACE_DISTANCE = 10;
	private static final int PERIOD = 500; // ms
	private static final Pattern EMOJI_RE = Pattern.compile(":([^:]+):");

	private transient SlackNoiseBot bot;
	private List<Race> races = new LinkedList<>();
	private transient Optional<CurrentRace> currentRace = Optional.empty();

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		if(bot.getProtocol() != Protocol.Slack) {
			throw new ModuleInitException("This module is Slack-specific");
		}
		this.bot = (SlackNoiseBot)bot;
	}

	@Override public void unload() {
		if(this.currentRace.isPresent()) {
			this.bot.deleteMessage(this.currentRace.get().message);
			this.currentRace.get().timer.cancel();
		}
	}

	@Command(value = "\\.race", allowPM = false)
	public void raceDefault(CommandContext ctx) {
		this.race(ctx, ":grinning::neutral_face::frowning:");
	}

	@Command(value = "\\.race (.+)")
	public void race(CommandContext ctx, String membersStr) {
		if(this.currentRace.isPresent()) {
			ctx.respond("#error One race at a time, fellas");
			return;
		}

		final List<String> members = new LinkedList<>();
		final Matcher matcher = EMOJI_RE.matcher(membersStr);
		while(matcher.find()) {
			members.add(matcher.group(1));
		}
		if(members.isEmpty()) {
			ctx.respond("#error No racers");
			return;
		}

		final Race race = new Race(String.format("Race #%d", this.races.size() + 1), members.toArray(new String[0]));
		this.races.add(race);
		this.save();
		this.startRace(race);
	}

	private void startRace(Race race) {
		final SlackAttachment attachment = new SlackAttachment(race.name, "", "A new race is about to begin\n" + Arrays.stream(race.members).map(member -> String.format(":%s:   ", member)).collect(Collectors.joining()), "");
		attachment.addMarkdownIn("text");
		this.currentRace = Optional.of(new CurrentRace(race, this.bot.sendAttachment(attachment)));
		this.updateMessage();
		this.currentRace.get().timer.scheduleAtFixedRate(new TimerTask() {
			@Override public void run() {
				EmojiRace.this.tick();
			}
		}, 1000, PERIOD);
	}

	private void updateMessage() {
		final CurrentRace race = this.currentRace.get();

		final Date now = new Date();
		final int seconds = (int)((now.getTime() - race.race.when.getTime()) / 1000);

		// Move to the next state if it's time; otherwise bail out now if it's pre-race to avoid spamming slack with duplicate messages
		switch(race.state) {
		case PRERACE:
			if(seconds < -LAST_DELAY) {
				return;
			} else {
				race.state = CurrentRace.State.ABOUT_TO_START;
			}
			break;
		case ABOUT_TO_START:
			if(seconds < 0) {
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
			text.add(String.format("Race begins in %s", pluralize(-seconds, "second", "seconds")));
			break;
		case RACING:
			if(seconds < 3) {
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
				final int finished = race.finalTime.computeIfAbsent(member, m -> {
					if(!race.race.winner.isPresent()) {
						race.race.winner = Optional.of(m);
						this.save();
					}
					return seconds;
				});
				track.append(String.format("  (%s)", pluralize(finished, "second", "seconds")));
				if(!race.race.winner.get().equals(member)) {
					track.append(String.format(" (+%d)", finished - race.finalTime.get(race.race.winner.get())));
				}
			} else {
				for(; i < RACE_DISTANCE; i++) {
					track.append("   ");
				}
				track.append("|");
			}
			//TODO Display sponsors
			text.add(track.toString());
		}

		if(race.race.winner.isPresent()) {
			text.add(0, String.format("*:%s: WINS!*", race.race.winner.get()));
		}

		final SlackAttachment attachment = new SlackAttachment(race.race.name, "", text.stream().collect(Collectors.joining("\n")), "");
		attachment.addMarkdownIn("text");
		this.bot.editAttachment(race.message, attachment);
	}

	private void tick() {
		final CurrentRace race = this.currentRace.get();
		final Date now = new Date();

		if(now.getTime() >= race.race.when.getTime()) {
			final Vector<String> candidates = new Vector<>(Arrays.asList(race.race.members));
			candidates.removeAll(race.finalTime.keySet());
			if(candidates.isEmpty()) {
				race.timer.cancel();
				this.currentRace = Optional.empty();
			} else {
				candidates.add(null); // No movement if selected
				final String choice = getRandom(candidates.toArray(new String[0]));
				if(choice == null) {
					return;
				}
				final int distance = race.progress.get(choice) + 1;
				race.progress.put(choice, distance);
			}
		}

		this.updateMessage();
	}

	@Override public String getFriendlyName() {return "EmojiRace";}
	@Override public String getDescription() {return "Force the emoji to race. Like dogs";}
	@Override public String[] getExamples() {
		return new String[] {
			".race",
			".race :fearful: :cold_sweat: :scream: :rage:",
		};
	}
}

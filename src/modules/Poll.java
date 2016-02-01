package modules;

import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import com.ullink.slack.simpleslackapi.SlackAttachment;
import debugging.Log;

import au.com.bytecode.opencsv.CSVParser;

import main.*;

/**
 * Poll
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Poll extends NoiseModule {
	private static final int WAIT_TIME = 3; // minutes

	private final CSVParser parser = new CSVParser(' ');
	
	private Timer pollTimer = null;
	private String pollText = "";
	private String pollOwner = "";
	private SentMessage pollStartMessage = null;
	private long startTime;
	private Map<String, String> votes;
	private List<String> validVotes;

	@Override protected Map<String, Style> styles() {
		final Map<String, Style> rtn = new HashMap<String, Style>() {{
			put("vote", Style.MAGENTA);
		}};
		Help.addHelpStyles(this.bot.getProtocol(), rtn); // Needed for 'command' and 'argument'
		return rtn;
	}

	//@Command("\\.poll \\[((?:" + VOTE_CLASS_REGEX + "+,?)+)\\] ?(.*)")
	//@Command("\\.poll \\[(" + VOTE_CLASS_REGEX + "+)\\] ?(.*)")
	@Command(value = "\\.poll (.*)", allowPM = false)
	public void poll(Message message, String argLine) {
		if(this.pollTimer != null) {
			message.respond("#error A poll is in progress");
			return;
		}

		this.pollOwner = message.getSender();
		this.votes = new HashMap<>();

		try {
			final String[] args = this.parser.parseLine(argLine);
			this.pollText = args[0];

			this.validVotes = new LinkedList<>();
			if(args.length > 1) {
				for(int i = 1; i < args.length; i++) {
					String option = args[i].trim();
					if(!option.isEmpty() && !this.validVotes.contains(option)) {
						this.validVotes.add(option);
					}
				}
			} else {
				this.validVotes.addAll(Arrays.asList(new String[] {"yes", "no"}));
			}
		} catch(IOException e) {
			message.respond("#error Exception attempting to parse vote options");
			Log.e(e);
			return;
		}
		
		if(this.validVotes.size() < 2) {
			message.respond("#error Polls need at least two options");
			return;
		}
		
		this.pollTimer = new Timer();
		this.pollTimer.scheduleAtFixedRate(new TimerTask() {
			private int minutesLeft = WAIT_TIME;
			@Override public void run() {
				if(minutesLeft-- == 0) {
					Poll.this.finished();
				} else {
					Poll.this.updateInPlace();
				}
			}
		}, 60 * 1000, 60 * 1000);
		this.startTime = System.currentTimeMillis();

		if(!this.updateInPlace()) {
			this.bot.sendMessage(
					"%s has started a poll (vote with .%(#command)s {#([|] #argument %s)} in the next %d minutes): %s",
					message.getSender(),
					"vote",
					this.validVotes.stream().toArray(String[]::new),
					WAIT_TIME,
					this.pollText);
		}
	}

	@Command("\\.vote  *\\$([1-9][0-9]*) *")
	public void vote(Message message, int vote) {
		this.vote(message, this.validVotes != null ? this.validVotes.get(vote-1) : null);
	}

	@Command("\\.vote  *(.+) *")
	public void vote(Message message, String vote) {
		// This checks pollText instead of pollTimer because we want to allow votes after the poll ended
		if(this.pollText.isEmpty()) {
			message.respond("#error There is no poll to vote on");
			return;
		}

		vote = vote.replaceAll("\\\\\\$", "\\$");
		if(!this.validVotes.contains(vote)) {
			message.respond("#error Invalid vote");
			return;
		}
		
		this.votes.put(message.getSender(), vote);

		if(!this.updateInPlace()) {
			if(this.pollTimer == null) {
				Slap.slap(this.bot, message);
			}
			MessageBuilder builder = message.buildResponse();
			builder.add("#success Vote recorded#plain . Current standing: ");
			this.tabulate(builder);
			builder.send();

			if(message.isPM()) {
				builder = this.bot.buildMessage();
				builder.add("%s voted %#(vote)s. Current standing: ", new Object[] {message.getSender(), vote});
				this.tabulate(builder);
				builder.send();
			}
		}
	}
	
	@Command("\\.pollstats")
	public void stats(Message message) {
		if(this.pollTimer == null) {
			message.respond("#error There is no poll in progress to check");
		} else {
			final int timeLeft = WAIT_TIME * 60 + (int)(this.startTime - System.currentTimeMillis()) / 1000;
			final MessageBuilder builder = message.buildResponse();
			builder.add("(%ds %s): ", new Object[] {timeLeft, (timeLeft == 1 ? "remains" : "remain")});
			this.tabulate(builder);
			builder.send();
		}
	}
	
	@Command(value = "\\.cancelpoll", allowPM = false)
	public void cancel(Message message) {
		if(this.pollTimer == null) {
			message.respond("#error There is no poll in progress to cancel");
		} else if(!this.pollOwner.equals(message.getSender())) {
			message.respond("#error Only %s can cancel the poll", this.pollOwner);
		} else if(!this.votes.isEmpty()) {
			message.respond("#error You can't cancel a poll once votes are in");
		} else {
			this.pollTimer.cancel();
			this.pollTimer = null;
			this.pollText = "";
			if(!this.updateInPlace()) {
				message.respond("#success Poll canceled");
			}
		}
	}
	
	private void finished() {
		this.pollTimer.cancel();
		this.pollTimer = null;
		if(!this.updateInPlace()) {
			// This method isn't called from the normal event processing framework, so our styles aren't attached
			Style.pushOverrideMap(this.styles());
			try {
				final MessageBuilder builder = this.bot.buildMessage();
				builder.add("#success Poll finished#plain : %s", new Object[] {this.pollText});
				builder.send();

				builder.add("Results: ");
				if(this.votes.isEmpty()) {
					builder.add("#vote No votes. You all suck.");
				} else {
					this.tabulate(builder);
				}
				builder.send();
			} finally{
				Style.popOverrideMap();
			}
		}
	}

	private Map<String, LinkedList<String>> nicksPerVote() {
		final Map<String, LinkedList<String>> nicksPerVote = new HashMap<>();
		for(String vote : this.validVotes) {
			nicksPerVote.put(vote, new LinkedList<>());
		}
		for(String nick : this.votes.keySet()) {
			nicksPerVote.get(this.votes.get(nick)).add(nick);
		}
		return nicksPerVote;
	}
	
	private void tabulate(MessageBuilder builder) {
		final Map<String, LinkedList<String>> nicksPerVote = this.nicksPerVote();
		final List<Object> args = new LinkedList<>();
		for(String vote : this.validVotes) {
			final LinkedList<String> nicks = nicksPerVote.get(vote);
			args.add(nicks.size());
			args.add(vote);
			if(nicks.isEmpty()) {
				args.add("");
			} else {
				args.add(String.format(" (%s)", nicks.stream().collect(Collectors.joining(", "))));
			}
		}
		builder.add("#([, ] #vote %d %s#plain %s)", new Object[] {args.toArray()});
	}

	private boolean updateInPlace() {
		if(this.bot.getProtocol() != Protocol.Slack) {
			return false;
		}
		final SlackNoiseBot bot = (SlackNoiseBot)this.bot;

		// Poll canceled
		if(this.pollStartMessage != null && this.pollText.isEmpty()) {
			bot.deleteMessage((SlackSentMessage)this.pollStartMessage);
			this.pollStartMessage = null;
			return true;
		}

		final StringBuilder pretext = new StringBuilder();
		if(this.pollTimer != null) {
			pretext.append(String.format("<@%s> started a poll (", bot.getUserID(this.pollOwner)));
			final int minutesLeft = WAIT_TIME + (int)(this.startTime - System.currentTimeMillis()) / 1000 / 60;
			switch(minutesLeft) {
			case 0:
				pretext.append("<1 minute remains");
				break;
			case 1:
				pretext.append("1 minute remains");
				break;
			default:
				pretext.append(String.format("%d minutes remain", minutesLeft));
				break;
			}
			pretext.append(')');
		}

		final StringBuilder text = new StringBuilder();
		text.append(this.pollText).append('\n');
		final Map<String, LinkedList<String>> nicksPerVote = this.nicksPerVote();
		for(String vote : this.validVotes) {
			final LinkedList<String> nicks = nicksPerVote.get(vote);
			text.append('\n').append(MessageBuilder.BULLET).append(' ').append(vote);
			if(!nicks.isEmpty()) {
				text.append(" (")
				    .append(nicks.stream().map(nick -> String.format("<@%s>", bot.getUserID(nick))).collect(Collectors.joining(" ")))
				    .append(')');
			}
		}

		final SlackAttachment attachment = new SlackAttachment("Poll", "Poll: " + this.pollText, text.toString(), pretext.toString());
		attachment.setColor(Color.BLUE);

		// Slack attachment fields look so bad, I can't bring myself to use them
		/*
		for(String vote : this.validVotes) {
			final LinkedList<String> nicks = nicksPerVote.get(vote);
			attachment.addField(vote, nicks.stream().collect(Collectors.joining("\n")), true);
		}
		*/

		if(this.pollStartMessage == null) {
			// Starting the poll
			this.pollStartMessage = bot.sendAttachment(attachment);
		} else {
			// Edit the poll message to show current information
			bot.editAttachment(this.pollStartMessage, attachment);
		}
		return true;
	}

	@Override public String getFriendlyName() {return "Poll";}
	@Override public String getDescription() {return "Polls users about a given question";}
	@Override public String[] getExamples() {
		return new String[] {
//				".poll _question_ -- Allow users to vote yes or no on _question_",
//				".poll [_vote1_,_vote2_,...] _question_ -- Allow users to vote _vote1_ or _vote2_ or ... on _question_",
				".poll _question_ -- Allow users to vote yes or no on _question_. Double-quote _question_ if it has spaces",
				".poll _question_ _vote1_ _vote2_ ... -- Allow users to vote _vote1_ or _vote2_ or ... on _question_. Double-quote any arguments if they have spaces",
				".vote _vote_ -- Cast your vote as _vote_ (must be one of the votes specified in the poll)",
				".pollstats -- Display time remaining in the poll and the current votes",
				".cancelpoll -- Cancel the poll if no votes have been cast yet"
		};
	}

	@Override public void unload() {
		if(this.pollTimer != null) {
			this.pollTimer.cancel();
			this.pollTimer = null;
		}
	}
}

package main;

import com.mrozekma.taut.TautException;
import com.mrozekma.taut.TautMessage;

import java.io.IOException;

/**
 * @author Michael Mrozek
 *         Created Dec 12, 2017.
 */
public class SlackMessage extends Message {
    private final TautMessage tautMessage;

    SlackMessage(NoiseBot bot, String message, String sender, String responseTarget, TautMessage tautMessage) {
        super(bot, message, sender, responseTarget);
        this.tautMessage = tautMessage;
    }

    public TautMessage getTautMessage() {
        return this.tautMessage;
    }

    @Override public void respondReaction(String reaction) {
        try {
            this.tautMessage.addReaction(reaction);
        } catch(TautException e) {
            // This matches the exception type thrown by MessageBuilder in similar circumstances
            throw new RuntimeException(e);
        }
    }

    @Override public boolean equals(Object o) {
        if(!(o instanceof SlackMessage)) {
            return false;
        }
        final SlackMessage other = (SlackMessage)o;
        return super.equals(o) && this.tautMessage.equals(other.tautMessage);
    }
}

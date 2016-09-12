package main;

import com.mrozekma.taut.TautHTTPSServer;

/**
 * @author Michael Mrozek
 *         Created Sep 11, 2016.
 */
public interface SlackActionHandler {
	void onSlackAction(TautHTTPSServer.ActionRequestHandler.UserAction action);

	default String getCallbackId() {
		// This is a bit warped, but oh well
		if(!(this instanceof NoiseModule)) {
			throw new ClassCastException("SlackActionHandlers should be NoiseModules");
		}
		return SlackServer.ACTION_CALLBACK_PREFIX + this.getClass().getSimpleName();
	}
}

package debugging;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Log
 *
 * @author Michael Mrozek
 *         Created Dec 19, 2010.
 */
public class Log {
	// 	DEBUG, VERBOSE, INFO, WARN, ERROR;

	public static void d(String msg) {Debugger.me.log(Level.DEBUG, msg);}
	public static void v(String msg) {Debugger.me.log(Level.VERBOSE, msg);}
	public static void i(String msg) {Debugger.me.log(Level.INFO, msg);}
	public static void w(String msg) {Debugger.me.log(Level.WARN, msg);}
	public static void e(String msg) {Debugger.me.log(Level.ERROR, msg);}
	
	public static void e(Throwable e) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		pw.flush();
		sw.flush();
		
		e(sw.toString());
	}
}

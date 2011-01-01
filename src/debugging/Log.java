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
	
	public static void d(Throwable e) {d(parseThrowable(e));}
	public static void v(Throwable e) {v(parseThrowable(e));}
	public static void i(Throwable e) {i(parseThrowable(e));}
	public static void w(Throwable e) {w(parseThrowable(e));}
	public static void e(Throwable e) {e(parseThrowable(e));}
	
	public static void in(String text) {i(text); Debugger.me.in.add(text);}
	public static void out(String text) {i(text); Debugger.me.out.add(text);}

	private static String parseThrowable(Throwable e) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		pw.flush();
		sw.flush();
		
		return sw.toString();
	}
}

package main;

/**
 * ModuleUnloadException
 *
 * @author Michael Mrozek
 *         Created Jun 15, 2009.
 */
public class ModuleUnloadException extends Exception {
	public ModuleUnloadException(String message) {super(message);}
	public ModuleUnloadException(String message, Throwable cause) {super(message, cause);}
	public ModuleUnloadException(Throwable cause) {super(cause);}
}

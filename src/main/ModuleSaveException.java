package main;

/**
 * ModuleSaveException
 *
 * @author Michael Mrozek
 *         Created Jul 18, 2009.
 */
public class ModuleSaveException extends Exception {
	public ModuleSaveException(String message) {super(message);}
	public ModuleSaveException(String message, Throwable cause) {super(message, cause);}
	public ModuleSaveException(Throwable cause) {super(cause);}
}

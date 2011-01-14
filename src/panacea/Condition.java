package panacea;

/**
 * Condition
 *
 * @author Michael Mrozek
 *         Created Feb 24, 2007.
 */
public interface Condition<T> {
	public boolean satisfies(T object);
}
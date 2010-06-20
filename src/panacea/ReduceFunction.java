package panacea;

/**
 * ReduceFunction
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public interface ReduceFunction <T, U> {
	public U reduce(T source, U accum);
}

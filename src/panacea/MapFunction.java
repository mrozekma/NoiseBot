package panacea;

/**
 * MapFunction
 *
 * @author Michael Mrozek
 *         Created Dec 1, 2007.
 */
public interface MapFunction<T,U> {
		public U map(T source);
}
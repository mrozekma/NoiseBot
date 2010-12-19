package panacea;

import java.util.Iterator;

/**
 * CircularQueue
 *
 * @author Michael Mrozek
 *         Created Dec 12, 2010.
 */
public class CircularQueue<T> implements Iterable<T> {
	private static class CircularQueueIterator<T> implements Iterator<T> {
		private final CircularQueue<T> queue;
		private int pos;
		private int remaining;
		
		public CircularQueueIterator(CircularQueue<T> queue) {
			this.queue = queue;
			this.pos = this.queue.filled ? (this.queue.tail + 1) % this.queue.size : 0;
			this.remaining = this.queue.filled ? this.queue.size : this.queue.tail + 1;
		}

		@Override public boolean hasNext() {
			return this.remaining > 0;
		}

		@Override public T next() {
			final T rtn = this.queue.elems[this.pos];
			this.pos = (this.pos + 1) % this.queue.size;
			this.remaining--;
			return rtn;
		}

		@Override public void remove() {
			throw new UnsupportedOperationException("remove() unsupported");
		}
	}
	
	private int size;
	private T[] elems;
	int tail;
	boolean filled;
	
	public CircularQueue(int size) {
		this.size = size;
		this.elems = (T[])new Object[this.size];
		this.tail = -1;
		this.filled = false;
	}
	
	public void add(T elem) {
		if(this.filled) {
			this.tail = (this.tail+1) % this.size;
		} else {
			this.tail++;
			if(this.tail+1 == this.size) {
				this.filled = true;
			}
		}
		
		this.elems[this.tail] = elem;
	}
	
	public void dbg() {
		System.out.println("size = "  + this.size);
		System.out.println("tail = "  + this.tail);
		System.out.println("filled = "  + (this.filled ? "true" : "false"));
		System.out.print("[");
		for(int i = 0; i < this.size; i++) {
			if(i>0) {System.out.print(", ");}
			System.out.print(this.elems[i]);
		}
		System.out.println("]");
	}
	
	@Override public Iterator iterator() {
		return new CircularQueueIterator<T>(this);
	}
}

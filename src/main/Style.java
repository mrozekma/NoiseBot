package main;

import java.awt.Color;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * @author Michael Mrozek
 *         Created Jan 2, 2016.
 */
public class Style {
	public static final Style BLACK = new Style(Color.BLACK);
	public static final Style RED = new Style(Color.RED);
	public static final Style YELLOW = new Style(Color.YELLOW);
	public static final Style GREEN = new Style(Color.GREEN);
	public static final Style BLUE = new Style(Color.BLUE);
	public static final Style MAGENTA = new Style(Color.MAGENTA);
	public static final Style CYAN = new Style(Color.CYAN);
	public static final Style WHITE = new Style(Color.WHITE);

	public static final Style PLAIN = new Style(null);
	public static final Style ERROR = RED;
	public static final Style FATAL = RED.reverse();

	public final Color color;
	public final boolean bold, reverse;

	public Style(Color color) {
		this(color, false, false);
	}

	public Style(Color color, boolean bold, boolean reverse) {
		this.color = color;
		this.bold = bold;
		this.reverse = reverse;
	}

	public Style color(Color color) {
		return (this.color == color) ? this : new Style(color, this.bold, this.reverse);
	}

	public Style bold() {
		return this.bold ? this : new Style(this.color, true, this.reverse);
	}

	public Style reverse() {
		return this.reverse ? this : new Style(this.color, this.bold, true);
	}

	// Styles are immutable, so "update" is kind of a misnomer; this returns the modified Style
	public Style update(String mod) {
		return getMod(mod).get().apply(this);
	}

	public static Optional<UnaryOperator<Style>> getMod(String name) {
		if(name.equals("black")) {
			return Optional.of(style -> style.color(Color.BLACK));
		}
		if(name.equals("red")) {
			return Optional.of(style -> style.color(Color.RED));
		}
		if(name.equals("yellow")) {
			return Optional.of(style -> style.color(Color.YELLOW));
		}
		if(name.equals("green")) {
			return Optional.of(style -> style.color(Color.GREEN));
		}
		if(name.equals("blue")) {
			return Optional.of(style -> style.color(Color.BLUE));
		}
		if(name.equals("magenta")) {
			return Optional.of(style -> style.color(Color.MAGENTA));
		}
		if(name.equals("cyan")) {
			return Optional.of(style -> style.color(Color.CYAN));
		}
		if(name.equals("white")) {
			return Optional.of(style -> style.color(Color.WHITE));
		}
		if(name.equals("bold")) {
			return Optional.of(style ->style.bold());
		}
		if(name.equals("reverse")) {
			return Optional.of(style ->style.reverse());
		}
		return Optional.empty();
	}
}

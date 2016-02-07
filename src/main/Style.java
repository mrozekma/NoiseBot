package main;

import java.awt.Color;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * @author Michael Mrozek
 *         Created Jan 2, 2016.
 */
public class Style {
	public enum Prop {bold, italic, underline, reverse, mono, monoblock}

	// Add new styles to getMod() below so they can be resolved in format specifiers
	public static final Style BLACK = new Style(Color.BLACK);
	public static final Style RED = new Style(Color.RED);
	public static final Style YELLOW = new Style(Color.YELLOW);
	public static final Style GREEN = new Style(Color.GREEN);
	public static final Style BLUE = new Style(Color.BLUE);
	public static final Style MAGENTA = new Style(Color.MAGENTA);
	public static final Style CYAN = new Style(Color.CYAN);
	public static final Style WHITE = new Style(Color.WHITE);

	public static final Style PLAIN = new Style(null);
	public static final Style BOLD = PLAIN.updateProp(Prop.bold);
	public static final Style ITALIC = PLAIN.updateProp(Prop.italic);
	public static final Style UNDERLINE = PLAIN.updateProp(Prop.underline);
	public static final Style REVERSE = PLAIN.updateProp(Prop.reverse);

	// It's important that these two be unique instances, since Slack checks for them specially
	public static final Style ERROR = RED.isBlock(true);
	public static final Style COREERROR = RED.updateProp(Prop.reverse).isBlock(true);

	public static final Style WARNING = YELLOW;
	public static final Style SUCCESS = GREEN;

	public final Color color;
	public final Set<Prop> props;
	public final boolean isBlock;

	private static Stack<Map<String, Style>> overrideMap = new Stack<>();

	public Style(Color color) {
		this.color = color;
		this.props = new HashSet<>();
		this.isBlock = false;
	}

	public Style(Color color, Set<Prop> props, boolean isBlock) {
		this.color = color;
		this.props = new HashSet<>(props);
		this.isBlock = isBlock;
	}

	@Override public boolean equals(Object o) {
		if(!(o instanceof Style)) {
			return false;
		}
		final Style other = (Style)o;
		return this.color.equals(other.color) && this.props.equals(other.props) && this.isBlock == other.isBlock;
	}

	public boolean is(Prop prop) {
		return this.props.contains(prop);
	}

	// Styles are immutable, so "update" is kind of a misnomer; this returns the modified Style
	public Style update(String mod) {
		return getMod(mod).orElseThrow(() -> new IllegalArgumentException(String.format("Unrecognized style modifier: %s", mod))).apply(this);
	}

	private Style updateColor(Color color) {
		return new Style(color, this.props, this.isBlock);
	}

	private Style updateProp(Prop prop) {
		final Style rtn = new Style(this.color, this.props, this.isBlock);
		rtn.props.add(prop);
		return rtn;
	}

	public Style isBlock(boolean b) {
		return new Style(this.color, this.props, b);
	}

	public static void pushOverrideMap(Map<String, Style> m) {
		synchronized(overrideMap) {
			overrideMap.push(m);
		}
	}

	public static void popOverrideMap() {
		synchronized(overrideMap) {
			overrideMap.pop();
		}
	}

	public static Optional<UnaryOperator<Style>> getMod(String name) {
		synchronized(overrideMap) {
			if(!overrideMap.isEmpty() && overrideMap.peek().containsKey(name)) {
				final Style rtn = overrideMap.peek().get(name);
				return Optional.of(style -> rtn);
			}
		}

		try {
			final Prop prop = Prop.valueOf(name);
			return Optional.of(style -> style.updateProp(prop));
		} catch(IllegalArgumentException e) {} // 'name' isn't a prop

		if(name.equals("black")) {
			return Optional.of(style -> style.updateColor(Color.BLACK));
		}
		if(name.equals("red")) {
			return Optional.of(style -> style.updateColor(Color.RED));
		}
		if(name.equals("yellow")) {
			return Optional.of(style -> style.updateColor(Color.YELLOW));
		}
		if(name.equals("green")) {
			return Optional.of(style -> style.updateColor(Color.GREEN));
		}
		if(name.equals("blue")) {
			return Optional.of(style -> style.updateColor(Color.BLUE));
		}
		if(name.equals("magenta")) {
			return Optional.of(style -> style.updateColor(Color.MAGENTA));
		}
		if(name.equals("cyan")) {
			return Optional.of(style -> style.updateColor(Color.CYAN));
		}
		if(name.equals("white")) {
			return Optional.of(style -> style.updateColor(Color.WHITE));
		}

		if(name.equals("plain")) {
			return Optional.of(style -> PLAIN);
		}
		if(name.equals("error")) {
			return Optional.of(style -> ERROR);
		}
		if(name.equals("coreerror")) {
			return Optional.of(style -> COREERROR);
		}
		if(name.equals("warning")) {
			return Optional.of(style -> WARNING);
		}
		if(name.equals("success")) {
			return Optional.of(style -> SUCCESS);
		}

		return Optional.empty();
	}

	// These are called "help styles" because it was the first module to use them, but they come up in other places, so they're defined here
	public static void addHelpStyles(Protocol protocol, Map<String, Style> map) {
		switch(protocol) {
		case IRC:
			map.put("command", Style.BLUE);
			map.put("argument", Style.GREEN);
			map.put("module", Style.RED);
			break;
		case Slack:
			map.put("command", Style.BOLD);
			map.put("argument", Style.ITALIC);
			map.put("module", Style.BOLD);
			break;
		}
	}
}

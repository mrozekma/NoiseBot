package modules;

import main.*;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * LaTeXMath
 *
 * @author Michael Mrozek
 *         Created Mar 14, 2016.
 */
public class LaTeXMath extends NoiseModule {
	private static final int SIZE = 20;

	private SlackNoiseBot bot;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.bot = (SlackNoiseBot)bot;
	}

	@Command("\\$\\$(.+)\\$\\$")
	public void math(CommandContext ctx, String expression) {
		final TeXFormula formula = new TeXFormula(expression);
		final BufferedImage image = (BufferedImage)formula.createBufferedImage(TeXConstants.STYLE_DISPLAY, SIZE, Color.BLACK, null);
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", baos);
		} catch(IOException e) {
			ctx.respond("#error Unable to generate image: " + e.getMessage());
		}

		this.bot.uploadFile(baos.toByteArray(), expression);
	}

	@Override public boolean supportsProtocol(Protocol protocol) {
		return protocol == Protocol.Slack;
	}

	@Override public String getFriendlyName() {return "LaTeXMath";}
	@Override public String getDescription() {return "Renders LaTeX math commands to images";}
	@Override public String[] getExamples() {
		return new String[] {
				"$$2+2$$",
				"$$\\binom{n}{k} = \\frac{n!}{k!(n-k)!}$$",
		};
	}
}

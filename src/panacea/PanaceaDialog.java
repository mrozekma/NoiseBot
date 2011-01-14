package panacea;
import java.awt.Window;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 * PanaceaDialog
 *
 * @author Michael Mrozek
 *         Created Jan 7, 2006.
 */
public class PanaceaDialog {
	public static class StatusString implements CharSequence {
		String string;
		
		public StatusString(String string) {
			this.string = string;
		}
		
		public char charAt(int i) {return this.string.charAt(i);}
		public int length() {return this.string.length();}
		public CharSequence subSequence(int a, int b) {return this.string.subSequence(a,b);}
		@Override public String toString() {return this.string;}
	}
	public static class FatalString extends StatusString {public FatalString(String title, String content) {super("<html><div style=\"color:#FF0000;font-weight:bold;font-size:14pt\">" + title + "</div><div style=\"padding-left:10px\">" + content);}}
	//public static class ErrorString extends StatusString {public ErrorString(String title, String content) {super("<html><div style=\"color:#FFFF00;font-weight:bold;font-size:14pt\">" + title + "</div><div style=\"padding-left:10px\">" + content + "</div></html>");}}

	public static final int CONFIRMATION_MESSAGE = JOptionPane.PLAIN_MESSAGE;
	public static final int ERROR_MESSAGE = JOptionPane.ERROR_MESSAGE;
	public static final int INFORMATION_MESSAGE = JOptionPane.INFORMATION_MESSAGE;
	public static final int QUESTION_MESSAGE = JOptionPane.QUESTION_MESSAGE;
	public static final int WARNING_MESSAGE = JOptionPane.WARNING_MESSAGE;
	
	public static final int CLOSD_OPTION = JOptionPane.CLOSED_OPTION;
	
	private static final String[] DEFAULT_OPTIONS = {"Acknowledge"};
	private static final Window DEFAULT_PARENT = null;
	
	private Window parent;
	private String title;
	private CharSequence message;
	private int messageType;
	private ImageIcon icon;
	private String[] options;
	private int defaultOption;
	
	private int buttonClicked;
	
	public PanaceaDialog(CharSequence message) {this("Dialog",message);}
	
	public PanaceaDialog(String title, CharSequence message) {this(null, title, message);}
	public PanaceaDialog(Window parent, String title, CharSequence message) {this(parent, title, message, PanaceaDialog.CONFIRMATION_MESSAGE);}
	public PanaceaDialog(String title, CharSequence message, ImageIcon icon) {this(null, title, message, icon);}
	public PanaceaDialog(Window parent, String title, CharSequence message, ImageIcon icon) {this(parent, title, message, PanaceaDialog.INFORMATION_MESSAGE, icon);}
	public PanaceaDialog(String title, CharSequence message, int messageType) {this(null, title, message, messageType);}
	public PanaceaDialog(Window parent, String title, CharSequence message, int messageType) {this(parent, title, message, messageType, PanaceaDialog.typeToIcon(messageType));}
	public PanaceaDialog(Window parent, String title, CharSequence message, int messageType, ImageIcon icon) {this(parent, title, message, messageType, icon, PanaceaDialog.DEFAULT_OPTIONS);}
	public PanaceaDialog(Window parent, String title, CharSequence message, int messageType, String[] options) {this(parent, title, message, messageType, PanaceaDialog.typeToIcon(messageType), options);}
	public PanaceaDialog(Window parent, String title, CharSequence message, int messageType, String[] options, int defaultOption) {this(parent, title, message, messageType, PanaceaDialog.typeToIcon(messageType), options, defaultOption);}
	public PanaceaDialog(Window parent, String title, CharSequence message, int messageType, ImageIcon icon, String[] options) {this(parent, title, message, messageType, icon, options, 0);}
	public PanaceaDialog(Window parent, String title, CharSequence message, int messageType, ImageIcon icon, String[] options, int defaultOption) {
		this.parent = parent == null ? DEFAULT_PARENT : this.parent;
		this.title = title;
		this.message = message;
		this.messageType = messageType;
		this.icon = icon;
		this.options = options;
		this.defaultOption = defaultOption;
	}
	
	public void show() {this.buttonClicked = JOptionPane.showOptionDialog(this.parent, this.message, this.title, JOptionPane.OK_OPTION, this.messageType, this.icon, this.options, this.options[this.defaultOption]);}
	
	public int getClickedButton() {return this.buttonClicked;}
	
	public int showAndGet() {
		this.show();
		return this.getClickedButton();
	}
	
	private static ImageIcon typeToIcon(int messageType) {
		switch(messageType) {
			case PanaceaDialog.ERROR_MESSAGE:
				return getImage("stop.png");
			case PanaceaDialog.INFORMATION_MESSAGE:
				return getImage("info.png");
			case PanaceaDialog.QUESTION_MESSAGE:
				return getImage("stop.png");
			case PanaceaDialog.WARNING_MESSAGE:
				return getImage("exclaim.png");
			case PanaceaDialog.CONFIRMATION_MESSAGE:
				return getImage("confirm.png");
			default:
				return null;
		}
	}
	
	private static ImageIcon getImage(String filename) {return Panacea.getImage(filename);}
	
	public static void error(String windowTitle, String messageTitle, String content) {
		new PanaceaDialog(null, windowTitle, new FatalString(messageTitle, content), ERROR_MESSAGE, new String[] {"Acknowledge"}, 0).show();
	}

	public static void fatalError(String windowTitle, String messageTitle, String content) {
		new PanaceaDialog(null, windowTitle, new FatalString(messageTitle, content), ERROR_MESSAGE, new String[] {"Exit"}, 0).show();
		System.exit(1);
	}
}
package panacea;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;

//import com.sun.java.swing.plaf.gtk.GTKLookAndFeel;

/**
 * Panacea
 *
 * @author Michael Mrozek
 *         Created Feb 21, 2007.
 */
public class Panacea {
	public enum LookAndFeel {Java, OS, Motif, Aqua, BottleGreen, Brown, LightAqua, LimeGreen, Orange, Purple, Raspberry, SunGlare, Sunset, Olive, Sepia, SteelBlue, Terracotta, Ebony, DarkViolet, Charcol}
	
	public static String NAME = "Panacea";
	public static boolean JAR = Panacea.class.getResource("Panacea.class").toString().startsWith("jar:") || Panacea.class.getResource("Panacea.class").toString().startsWith("onejar:");
	
	private static String ASSET_DIRECTORY = "assets";
	
	private static Preferences myPreferences;
	private static HashMap<String, Semaphore> semaphores = new HashMap<String, Semaphore>();
	
	private static Timer timer = new Timer("PanaceaTimer");
	private static HashMap<String, TimerTask> timers = new HashMap<String, TimerTask>();
	
	public static void main(String[] args) {about(); exit();}
	
	public static ImageIcon getImage(String filename) {return JAR ? new ImageIcon(Panacea.class.getResource(ASSET_DIRECTORY + "/" + filename)) : new ImageIcon(ASSET_DIRECTORY + "/" + filename);}
	public static ImageIcon getImage(String filename, int width, int height) {return new ImageIcon(getImage(filename).getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));}
	
	public static void exit() {System.exit(0);}
	
	public static void sleep(double seconds) {try {Thread.sleep((long)(seconds * 1000));} catch(InterruptedException e) {}}
	
	public static String tint(String source, Color color) {return "<font color=\"#" + addPrefixPadding(Integer.toHexString(color.getRed()), 2, '0') + addPrefixPadding(Integer.toHexString(color.getGreen()), 2, '0') + addPrefixPadding(Integer.toHexString(color.getBlue()), 2, '0')+"\">" + source + "</font>";}
	public static String tint(String source, Color[] colors) {
		String rtn = "";
		for(int i = 0; i < source.length(); i++) {
			rtn += tint("" + source.charAt(i), colors[i % colors.length]);
		}
		return rtn;
	}
	
	public static String addPrefixPadding(String source, int requiredLength, char padding) {if(source.length()<requiredLength) {return addPrefixPadding(padding + source, requiredLength, padding);} else {return source;}}
	public static String addSuffixPadding(String source, int requiredLength, char padding) {if(source.length()<requiredLength) {return addSuffixPadding(source + padding, requiredLength, padding);} else {return source;}}
	public static String padNumber(double source, int requiredLength) {
		String number = "" + source;
		int prefix = number.indexOf(".") + 1;
		while(number.length() < prefix + requiredLength) {number += "0";}
		return number;
	}
	
	public static String makeImgTag(String filename) {return JAR ? "<img src=\"" + Panacea.class.getResource(ASSET_DIRECTORY + "/" + filename) + "\">" : "<img src=\"file:///" + System.getProperty("user.dir").replace("\\","/") + "/" + ASSET_DIRECTORY + "/" + filename+"\">";}
	
	public static String makeHTMLTable(String... cells) {
		String rtn = "<table border=0 cellspacing=0 cellpadding=0><tr>";
		for(String cell : cells) {rtn += "<td>"+cell+"</td>";}
		rtn += "</tr></table>";
		return rtn;
	}
	
	public static void smartPack(Window frame) {
		int width = frame.getSize().width;
		frame.pack();
		frame.setSize(width, frame.getSize().height);
		frame.setLocationRelativeTo(frame.getParent());
	}
	
	public static void addGlobalKeyListener(Window w, KeyListener l) {addGlobalKeyListener(w, l, new Object[] {});}
	public static void addGlobalKeyListener(final Window w, final KeyListener keyL, final Object[] exceptions) {
		ContainerListener l = new ContainerListener() {
			public void componentAdded(ContainerEvent e) {addListenerRecursively(e.getChild());}
			public void componentRemoved(ContainerEvent e) {removeListenerRecursively(e.getChild());}
			
			public void addListenerRecursively(Component c) {
				if(Panacea.arraySearch(exceptions, c) >= 0) {return;}
				c.addKeyListener(keyL);
				if(c instanceof Container) {
					Container cont = (Container)c;
					cont.addContainerListener(this);
					for(Component i : cont.getComponents()) {
						this.addListenerRecursively(i);
					}
				}
			}

			public void removeListenerRecursively(Component c) {
				if(Panacea.arraySearch(exceptions, c)>=0) {return;}
				c.removeKeyListener(keyL);
				if(c instanceof Container) {
					Container cont = (Container)c;
					cont.removeContainerListener(this);
					for(Component i : cont.getComponents()) {
						this.removeListenerRecursively(i);
					}
				}
			}
		};
		
		// Have to use reflection since addListenerRecursively isn't actually a ComponentListener method
		try {l.getClass().getMethod("addListenerRecursively", Component.class).invoke(l, w);} catch(Exception e) {e.printStackTrace();}
	}
	
	public static String implode(String[] strings) {return implode(strings, ",");}
	public static String implode(String[] strings, String delimiter) {
		if(strings.length == 0) {return "";}
		StringBuffer rtn = new StringBuffer();
		for(String s : strings) {rtn.append(s).append(delimiter);}
		return rtn.substring(0, rtn.length() - delimiter.length());
	}
	
	public static <T> int arraySearch(T[] array, T search) {
		for(int i = 0; i < array.length; i++) {
			if(array[i].equals(search)) {return i;}
		}
		return -1;
	}
	
	public static <T extends Comparable> T[] sorted(T[] array) {
		Arrays.sort(array);
		return array;
	}
	public static <T extends Comparable> T[] sortedCopy(T[] array) {return sorted(Arrays.copyOf(array, array.length));}

	public static void initPreferences(Class<?> c) {myPreferences = Preferences.userNodeForPackage(c);}
	public static void initPreferences(Class<?> c, String name) {myPreferences = Preferences.userNodeForPackage(c).node(name);}
	public static String getSetting(String name, String defaultValue) {return myPreferences.get(name, defaultValue);}
	public static boolean getSetting(String name, boolean defaultValue) {return myPreferences.getBoolean(name, defaultValue);}
	public static int getSetting(String name, int defaultValue) {return myPreferences.getInt(name, defaultValue);}
	public static void saveSetting(String name, String value) {myPreferences.put(name, value);}
	public static void saveSetting(String name, boolean value) {myPreferences.putBoolean(name, value);}
	public static void saveSetting(String name, int value) {myPreferences.putInt(name, value);}
	public static void dropSetting(String name) {myPreferences.remove(name);}
	public static void clearPreferences() {try {myPreferences.clear();} catch(BackingStoreException e) {}}

	public static void loadLF(String lf) {
		try {UIManager.setLookAndFeel(lf);} catch(Exception e) {e.printStackTrace();}
    	SwingUtilities.updateComponentTreeUI(new JOptionPane());
    	SwingUtilities.updateComponentTreeUI(new JList());
    	SwingUtilities.updateComponentTreeUI(new JButton());
	}
	
	public static void loadLF(LookAndFeel lf) {
		try {
			   switch(lf) {
				    case Java:
				    	UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
				    	break;
				    case OS:
				    	UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				    	
				    	// Fix GTK theming at some point? (Font size is too big)
//				    	if(!UIManager.getLookAndFeel().isNativeLookAndFeel()) { //Linux doesn't have a native l&f
//				    		try {
//				    			UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
//				    		} catch(Exception e) {} //No GTK; oh well
//				    	}
				    	break;
				    case Motif:
				    	UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
				    	break;
				    default:
//				    	UIManager.setLookAndFeel(new SubstanceLookAndFeel());
//				    	SubstanceLookAndFeel.setCurrentTheme("org.jvnet.substance.theme.Substance" + lf.toString() + "Theme");
				    	break;
				    }
			    
		    	SwingUtilities.updateComponentTreeUI(new javax.swing.JOptionPane());
		    	SwingUtilities.updateComponentTreeUI(new javax.swing.JTabbedPane());
		    	SwingUtilities.updateComponentTreeUI(new javax.swing.JComboBox());
			} catch(Exception e) {PanaceaDialog.error("Loading Problem", "Missing Look and Feel", "Your look and feel is missing. The default look and feel will be used instead");}
	}
	
	public static void loadLF(javax.swing.LookAndFeel lf) {
		try {UIManager.setLookAndFeel(lf);} catch(Exception e) {}
    	SwingUtilities.updateComponentTreeUI(new JOptionPane());
    	SwingUtilities.updateComponentTreeUI(new JList());
    	SwingUtilities.updateComponentTreeUI(new JButton());
	}
	
	public static JLabel makeRightLabel(String text) {return makeRightLabel(text, true);}
	public static JLabel makeRightLabel(String text, boolean alignTop) {
		JLabel label = new JLabel(text);
		label.setHorizontalAlignment(JLabel.RIGHT);
		if(alignTop) {label.setVerticalAlignment(JLabel.TOP);}
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		return label;
	}
	
	public static FlowLayout makeFlowSpacer() {return makeFlowSpacer(5);}
	public static FlowLayout makeFlowSpacer(int gap) {
		FlowLayout layout = new FlowLayout();
		layout.setHgap(gap);
		return layout;
	}
	
	// Include a & in text before a letter to make it the mnemonic
	public static JMenuItem makeMenuItem(String text) {
		final JMenuItem rtn = new JMenuItem();
		
		if(text.contains("&")) {
			rtn.setMnemonic(text.charAt(text.indexOf("&") + 1));
			text = text.replaceFirst("&", "");
		}
		
		rtn.setText(text);
		
		return rtn;
	}
	public static JMenuItem makeMenuItem(String text, ImageIcon icon) {
		final JMenuItem rtn = makeMenuItem(text);
		if(icon != null) {rtn.setIcon(icon);}
		return rtn;
	}
	public static JMenuItem makeMenuItem(String text, ImageIcon icon, JMenu parentMenu) {
		final JMenuItem rtn = makeMenuItem(text, icon);
		parentMenu.add(rtn);
		return rtn;
	}
	public static JMenuItem makeMenuItem(String text, ImageIcon icon, JMenu parentMenu, int accelerator) {
		return makeMenuItem(text, icon, parentMenu, accelerator, 0);
	}
	public static JMenuItem makeMenuItem(String text, ImageIcon icon, JMenu parentMenu, int accelerator, int acceleratorModifiers) {
		final JMenuItem rtn = makeMenuItem(text, icon, parentMenu);
		rtn.setAccelerator(KeyStroke.getKeyStroke(accelerator, acceleratorModifiers));
		return rtn;
	}
	public static JMenuItem makeMenuItem(String text, ImageIcon icon, JMenu parentMenu, int accelerator, int acceleratorModifiers, final Runnable onClick) {
		final JMenuItem rtn = makeMenuItem(text, icon, parentMenu, accelerator, acceleratorModifiers);
		
		rtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onClick.run();
			}
		});
		
		return rtn;
	}
	
	public static JButton makeToolBarItem(String text, ImageIcon icon, JToolBar toolBar, final Runnable onClick) {
		final JButton rtn = new JButton(icon);
		rtn.setToolTipText(text);
		rtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onClick.run();
			}
		});
		
		toolBar.add(rtn);
		return rtn;
	}
	
	public static Border makeBorder(int padding) {return BorderFactory.createEmptyBorder(padding, padding, padding, padding);}
	
	public static int range(int value, int min, int max) {return Math.min(max, Math.max(min, value));}
	
	public static InetAddress getLocalHost() {try {return InetAddress.getLocalHost();} catch(UnknownHostException e) {return null;}}
	
	public static void addSizeDebugger(final Window window) {
		if(window instanceof JDialog) {((JDialog)window).setResizable(true); ((JDialog)window).setModal(false);}
		if(window instanceof JFrame) {((JFrame)window).setResizable(true);}
		
		final JFrame frame = new JFrame("Size");
		frame.setSize(120, 60);
		frame.setLocation(10, 10);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new FlowLayout());

		window.addWindowListener(new WindowAdapter() {
			@Override public void windowClosed(WindowEvent e) {
				frame.dispose();
			}
		});
		
		final JLabel label = new JLabel(window.getSize().width + " x " + window.getSize().height);
		frame.add(label);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 18));
		label.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) {
				window.setSize(Integer.parseInt(JOptionPane.showInputDialog(frame, "Width:", window.getSize().width)), Integer.parseInt(JOptionPane.showInputDialog(frame, "Height:", window.getSize().height)));
			}
		});
		frame.setVisible(true);
		
		window.addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent e) {
				super.componentResized(e);
				label.setText(window.getSize().width + " x " + window.getSize().height);			}
		});
	}
	
	public static void makeStdErrDebugger() {
		final PrintStream stderr = System.err;
		final String start = "<html>" + tint("Panacea Standard Error (stderr) Window", Color.RED) + "<br><br>";
		final StringBuffer buffer = new StringBuffer(start);
		
		final JFrame frame = new JFrame("Standard Error (stderr)");
		frame.setSize(400, 300);
		frame.setLocation(10, 10);
		//frame.setIconImage(Panacea.getImage("toolbar/stop.png").getImage());
		frame.setLayout(new BorderLayout());
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.setAlwaysOnTop(true);

		final JLabel label = new JLabel();
		label.setVerticalAlignment(JLabel.TOP);
		label.setBorder(makeBorder(5));
		final JScrollPane scrollPane = new JScrollPane(label, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		frame.add(scrollPane, BorderLayout.CENTER);
		JPanel buttonPanel = new JPanel();
		frame.add(buttonPanel, BorderLayout.SOUTH);
		buttonPanel.setLayout(makeFlowSpacer());
		JButton flushButton = new JButton("Flush");
		buttonPanel.add(flushButton);
		flushButton.setMnemonic(KeyEvent.VK_F);
		flushButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String out = buffer.substring(start.length()).replace("<br>","\n");
				for(int i = 0; i < out.length(); i++) {stderr.write(out.charAt(i));}
			}
		});
		JButton exitButton = new JButton("Exit JVM");
		buttonPanel.add(exitButton);
		exitButton.setMnemonic(KeyEvent.VK_X);
		exitButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				exit();
			}
		});
		
		System.setErr(new PrintStream(new OutputStream() {
			@Override public void write(int b) {
				buffer.append((char)b == '\n' ? "<br>" : (char)b);
				if(!frame.isVisible()) {
						frame.setVisible(true);
						frame.setLocationRelativeTo(null);
						new Timer().scheduleAtFixedRate(new TimerTask() {
							@Override public void run() {
								if(label.getText().length() < buffer.toString().length()) {
									label.setText(buffer.toString());
									scrollPane.validate();
									frame.validate();
									scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
								}
							}
						}, 0, 1000);
				}
			}
		}));
	}
	
	public static String[] getStrings(Object[] objects) {
		String[] strings = new String[objects.length];
		for(int i = 0; i < strings.length; i++) {strings[i]=objects[i].toString();}
		return strings;
	}
	
	@SuppressWarnings("unchecked") public static <T> T[] filter(T[] objects,Condition<T> condition) {
		Vector<T> rtn = new Vector<T>();
		for(T o : objects) {
			if(condition.satisfies(o)) {rtn.add(o);}
		}
		return rtn.toArray((T[])Array.newInstance(objects.getClass().getComponentType(),0));
	}
	
	@SuppressWarnings("unchecked") public static <T> T[] filter(T[] objects,boolean[] conditions) {
		if(objects.length != conditions.length) {throw new IllegalArgumentException("Need exactly 1 condition for each object");}
		Vector<T> rtn = new Vector<T>();
		for(int i = 0; i < objects.length; i++) {
			if(conditions[i]) {rtn.add(objects[i]);}
		}
		return rtn.toArray((T[])Array.newInstance(objects.getClass().getComponentType(), 0));
	}
	
	public static <T, U> U[] map(T[] objects, MapFunction<T, U> f) {                                                                                                                                                                              
        if(objects.length == 0) {throw new IllegalArgumentException("Can't map onto an empty array");}                                                                                                                                           
        @SuppressWarnings("unchecked") U[] rtn = (U[])Array.newInstance(f.map(objects[0]).getClass(), objects.length);                                                                                                                            
        for(int i = 0; i < objects.length; i++) {                                                                                                                                                                                                    
            rtn[i] = f.map(objects[i]);                                                                                                                                                                                                          
        }                                                                                                                                                                                                                                      
        return rtn;                                                                                                                                                                                                                            
    }
	
	public static <T, U> U[] map(T[] objects, MapFunction<T, U> f, U[] empty) {
		return objects.length == 0 ? empty : map(objects, f);
	}
	
	public static <T, U> U reduce(T[] objects, ReduceFunction<T, U> f, U start) {
		U accum = start;
		for(T object : objects) {
			accum = f.reduce(object, accum);
		}
		return accum;
	}
	
	public static double round(double number, int places) {return places > 0 ? (Math.round(number * Math.pow(10, places)) / Math.pow(10, places)) : number;}
	
	public static String bytesToFriendly(double bytes, int places) {
		for(String size : new String[] {"bytes", "KB", "MB", "GB"}) {
			if(bytes < 1024) {return Panacea.round(bytes, places) + " " + size;}
			bytes /= 1024;
		}
		return Panacea.round(bytes, places) + " PB";
	}
	
	public static String pluralize(int number, String singular, String plural) {return pluralize(number, singular, plural, true);}
	public static String pluralize(int number, String singular, String plural, boolean includeNumber) {return (includeNumber ? number + " " : "") + (number == 1 ? singular : plural);}
	
	public static String toSentenceCase(String string) {return Character.toUpperCase(string.charAt(0)) + string.substring(1).toLowerCase();}
	
	public static Color decodeColor(String name, Color defaultColor) {
		if(name.equalsIgnoreCase("white")) {return Color.WHITE;}
		if(name.equalsIgnoreCase("gray")) {return Color.GRAY;}
		if(name.equalsIgnoreCase("black")) {return Color.BLACK;}
		if(name.equalsIgnoreCase("red")) {return Color.RED;}
		if(name.equalsIgnoreCase("pink")) {return Color.PINK;}
		if(name.equalsIgnoreCase("orange")) {return Color.ORANGE;}
		if(name.equalsIgnoreCase("yellow")) {return Color.YELLOW;}
		if(name.equalsIgnoreCase("green")) {return Color.GREEN;}
		if(name.equalsIgnoreCase("magenta")) {return Color.MAGENTA;}
		if(name.equalsIgnoreCase("cyan")) {return Color.CYAN;}
		if(name.equalsIgnoreCase("blue")) {return Color.BLUE;}
		return defaultColor;
	}
	
	@SuppressWarnings("unchecked") public static <T> T[] combine(T[] one, T[] two) {
		Vector<T> rtn = new Vector<T>();
		for(T i : one) {rtn.add(i);}
		for(T i : two) {rtn.add(i);}
		return (T[])rtn.toArray();
	}
	
	public static String[] toString(Object[] objects) {
		String[] rtn = new String[objects.length];
		for(int i = 0; i < rtn.length; i++) {rtn[i] = objects[i].toString();}
		return rtn;
	}
	
	public static <T> T[] reverse(T[] array) {
		List<T> list = Arrays.asList(array);
		Collections.reverse(list);
		return list.toArray(array);
	}
	
	public static String substring(String s, int start) {return start < 0 ? substring(s, s.length() + start) : substring(s, start, s.length() - start);}
	public static String substring(String s, int start, int length) {
		if(length + start > s.length()) {throw new IndexOutOfBoundsException("Length extends past the end of the string");}
		if(start < 0) {return substring(s, s.length() + start,length);}
		if(length <= start - s.length()) {throw new IndexOutOfBoundsException("Negative length extends past the beginning of the string");}
		if(length < 0) {return substring(s, start, s.length() + length - start);}
		return s.substring(start, start + length);
	}
	
	public static void lock(String name) {
		System.out.println("Acquiring semaphore: " + name);
		if(!semaphores.keySet().contains(name)) {semaphores.put(name, new Semaphore(1));}
		try {
			semaphores.get(name).acquire();
		} catch(InterruptedException e) {}
		System.out.println("Locked semaphore: " + name);
	}
	
	public static void unlock(String name) {
		if(!semaphores.keySet().contains(name)) {return;}
		semaphores.get(name).release();
		System.out.println("Released semaphore: " + name);
	}
	
	public static void startTimer(String name, TimerTask task, double seconds) {startTimer(name, task, seconds, false);}
	public static void startTimer(String name, TimerTask task, double seconds, boolean recurring) {
		timers.put(name, task);
		if(recurring) {
			timer.scheduleAtFixedRate(task, 0, (long)(seconds*1000));
		} else {
			timer.schedule(task, (long)(seconds*1000));
		}
	}
	
	public static void startUniqueTimer(String name, TimerTask task, double seconds) {
		stopTimer(name);
		startTimer(name, task, seconds);
	}
	
	public static void startUniqueTimer(String name, TimerTask task, double seconds, boolean recurring) {
		stopTimer(name);
		startTimer(name, task, seconds, recurring);
	}
	
	public static void stopTimer(String name) {
		if(timers.containsKey(name)) {
			timers.get(name).cancel();
			timers.remove(name);
		}
	}
	
	public static void stopAllTimers() {
		for(String n : timers.keySet()) {stopTimer(n);}
	}
	
	public static boolean timerIsRunning(String name) {return timers.get(name) != null;}
	
	public static int getRandomInt(int min, int max) {return ((int)(Math.random() * (max - min + 1))) + min;}
	
	public static <T> T getRandom(T[] arr) {return arr.length > 0 ? arr[getRandomInt(0, arr.length - 1)] : null;}

	public static String[] getMatches(String[] arr, String key) {
		Vector<String> matches = new Vector<String>();
		Pattern pattern = Pattern.compile(key);
		for(int i = 0; i < arr.length; i++) {
			if (pattern.matcher(arr[i]).matches())
				matches.add(arr[i]);
		}
		return matches.toArray(new String[0]);
	}

	public static String getRandomMatch(String[] arr, String key) {return getRandom(getMatches(arr, key));}
	
	public static void beep() {Toolkit.getDefaultToolkit().beep();}
	
	public static Dimension getScreenSize() {return Toolkit.getDefaultToolkit().getScreenSize();}
	
	public static void deleteDirectory(File dir) {
		if(!dir.isDirectory()) {throw new IllegalArgumentException("dir must be a directory");}
		if(!dir.exists()) {return;}
		
		for(File f : dir.listFiles()) {
			if(f.isDirectory()) {
				deleteDirectory(f);
			} else {
				f.delete();
			}
		}
		
		dir.delete();
	}
	
	public static JMenuItem makeHelpMenu() {
		JMenuItem item = new JMenuItem("About Panacea", getImage("panacea.png", 16, 16));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				about();
			}
		});
		return item;
	}
	
	public static void about() {
		String[] stuffses = new String[] {
				"frame management",
				"string parsing",
				"mathematics",
				"L&F operations",
				"system preferences",
				"array manipulation",
				"dialog creation",
				"database operations",
				"network transmissions",
				"tray icons",
				"automatic status bars",
				"reflected splash screens",
				"semaphores",
				"debuggers"
		};
		
		String[] buttons = new String[] {"Close", "System Properties", "Runtime", "Semaphores"};
		
		switch(JOptionPane.showOptionDialog(null, "<html><div style=\"color:#0000FF;font-weight:bold;font-size:14pt\">About Panacea</div><div style=\"padding-left:10px;width:300px\"><b>Panacea</b> is a package of Java utility classes, written by Michael Mrozek.<br><br>Panacea facilitates " + implode(stuffses, ", ") + " and more.</div></html>", "About Panacea", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE, Panacea.getImage("panacea.png"), buttons, null)) {
			case 0: return; //Close
			case 1: { //System Properties
				JFrame frame = new JFrame("System Properties");
				frame.setSize(600, 600);
				frame.setLocationRelativeTo(null);
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				FlexTable table = new FlexTable("Key", "Value");
				frame.add(new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
				for(Enumeration<?> e = System.getProperties().propertyNames(); e.hasMoreElements();) {
					String key = e.nextElement().toString();
					String value = System.getProperty(key);
					table.addRow(key, value);
				}
				frame.setVisible(true);
				table.setColumnWidths(150, 0);
				return;
			}
			case 2: { //Runtime
				JFrame frame = new JFrame("Runtime");
				frame.setSize(225, 150);
				frame.setLocationRelativeTo(null);
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				FlexTable table = new FlexTable("Parameter", "Value");
				frame.add(new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
				
				table.addRow("Processors", Runtime.getRuntime().availableProcessors());
				table.addRow("Free Memory", bytesToFriendly(Runtime.getRuntime().freeMemory(), 4));
				table.addRow("Maximum Memory", bytesToFriendly(Runtime.getRuntime().maxMemory(), 4));
				table.addRow("Total Memory", bytesToFriendly(Runtime.getRuntime().totalMemory(), 4));
				table.addRow("Current Time","" + System.currentTimeMillis());
//				table.addRow("Prepared Statements",(((Database.getConnects() - 1) * Database.MAX_STATEMENTS) + Database.getCount()));
				
				frame.setVisible(true);
				return;
			}
			case 3: { //Semaphores
				JFrame frame = new JFrame("Semaphores");
				frame.setSize(300, 300);
				frame.setLocationRelativeTo(null);
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				FlexTable table = new FlexTable("Name", "Status");
				frame.add(new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
				for(String name : semaphores.keySet()) {
					table.addRow(name, semaphores.get(name).availablePermits() == 0 ? "Locked" : "Free");
				}
				frame.setVisible(true);
				table.setColumnWidths(0, 50);
				return;
			}
		}
	}
}

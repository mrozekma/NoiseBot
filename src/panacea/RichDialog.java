package panacea;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * RichDialog
 *
 * @author Michael Mrozek
 *         Created Jul 16, 2007.
 */
@SuppressWarnings("serial") public class RichDialog extends JDialog {
	private static final Font FONT = new Font("Arial", Font.BOLD, 12);
	
	private final JLabel titleLabel;
	private final JLabel descriptionLabel;
	
	private JPanel contentPanel;
	private JPanel buttonPanel = null;
	
	public RichDialog(Window parent, String title, String description, boolean modal) {
		super(parent, title, modal ? Dialog.ModalityType.DOCUMENT_MODAL : Dialog.ModalityType.MODELESS);
		this.setSize(400, 200);
		
		JPanel panel = new JPanel(new BorderLayout());
		this.add(panel);
		
		JPanel titlePanel = new JPanel(new BorderLayout());
		panel.add(titlePanel, BorderLayout.NORTH);
		titlePanel.setBackground(Color.WHITE);
		titlePanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
		
		this.titleLabel = new JLabel(title);
		titlePanel.add(this.titleLabel, BorderLayout.NORTH);
		this.titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 0));
		this.titleLabel.setFont(FONT);
		
		this.descriptionLabel = new JLabel(description);
		titlePanel.add(this.descriptionLabel, BorderLayout.SOUTH);
		this.descriptionLabel.setBorder(BorderFactory.createEmptyBorder(5, 20, 10, 0));
//		this.descriptionLabel.setFont(FONT.deriveFont(Font.PLAIN, 10));
		
		panel.add(this.contentPanel = new JPanel());
	}
	
	public void setTitle(String title) {this.titleLabel.setText(title);}
	public void setDescription(String description) {this.descriptionLabel.setText(description);}
	
	@Override public void setVisible(boolean b) {
		if(this.contentPanel.getComponents().length == 0 && this.buttonPanel != null) {this.buttonPanel.setBorder(null);}
		super.setVisible(b);
	}

	public JPanel getContentPanel() {return this.contentPanel;}
	
	public void addButton(JButton button) {
		if(this.buttonPanel == null) {
			this.add(this.buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 10)),BorderLayout.SOUTH);
			this.buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
		}
		
		this.buttonPanel.add(button);
	}
	
	public void addEnterEscapeListener(JButton enterButton, JButton escapeButton) {addEnterEscapeListener(enterButton, escapeButton,new Object[] {});}
	public void addEnterEscapeListener(final JButton enterButton, final JButton escapeButton, Object[] exceptions) {
		Panacea.addGlobalKeyListener(this, new KeyAdapter() {
			private boolean enterPress = false;
			
			@Override public void keyPressed(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
					escapeButton.doClick();
				} else if(e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
					this.enterPress = true;
				} else {
					super.keyPressed(e);
				}
			}
			
			@Override public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0 && this.enterPress) {
					enterButton.doClick();
				} else {
					super.keyReleased(e);
				}
				this.enterPress = false;
			}
		}, exceptions);
		
		this.addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent e) {
				escapeButton.doClick();
			}
		});
	}
	
	public static String showInputDialog(Window parent, String title, String message) {return showInputDialog(parent, title, message, "");}
	public static String showInputDialog(Window parent, String title, String message, String defaultValue) {return showInputDialog(parent, title, message, defaultValue, "OK", "Cancel");}
	public static String showInputDialog(Window parent, String title, String message, String defaultValue, String okText, String cancelText) {
		final String[] rtn = {null};
		
		final RichDialog d = new RichDialog(parent, title, message, true);
		final JTextField input = new JTextField(defaultValue);
		input.setPreferredSize(new Dimension(380, input.getPreferredSize().height));
		d.getContentPanel().add(input);
		
		JButton okButton = new JButton(okText);
		d.addButton(okButton);
		d.getRootPane().setDefaultButton(okButton);
		okButton.setMnemonic(KeyEvent.VK_O);
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				rtn[0] = input.getText();
				d.dispose();
			}
		});
		
		JButton cancelButton = new JButton(cancelText);
		d.addButton(cancelButton);
		cancelButton.setMnemonic(KeyEvent.VK_A);
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				d.dispose();
			}
		});
		
		d.addEnterEscapeListener(okButton, cancelButton);
		Panacea.smartPack(d);
		input.selectAll();
		d.setVisible(true);
		return rtn[0];
	}
	
	public static int showOptionDialog(Window parent, String title, String message, String[] options) {return showOptionDialog(parent, title, message, null, options);}
	public static int showOptionDialog(Window parent, String title, String message, String details, String[] options) {
		final int[] rtn = {-1};
		
		final RichDialog d = new RichDialog(parent, title, message, true);
		final HashMap<Character, JButton> keys = new HashMap<Character, JButton>();
		
		if(details != null) {
			d.getContentPanel().setLayout(new BoxLayout(d.getContentPanel(), BoxLayout.LINE_AXIS));
			d.getContentPanel().setBorder(Panacea.makeBorder(10));
			final JLabel label = new JLabel(details);
			label.setAlignmentY(JLabel.TOP_ALIGNMENT);
			d.getContentPanel().add(label);
			d.getContentPanel().setPreferredSize(new Dimension(400, 150));
		}
		
		for(int i = 0; i < options.length; i++) {
			final int j = i;
			JButton b = new JButton(options[i]);
			if(options[i].contains("&")) {
				char hotkey=options[i].charAt(options[i].indexOf("&")+1);
				keys.put(Character.toLowerCase(hotkey),b);
				b.setMnemonic(hotkey);
				b.setText(options[i].replaceFirst("&",""));
			}
			d.addButton(b);
			b.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					rtn[0] = j;
					d.dispose();
				}
			});
		}
		
		Panacea.addGlobalKeyListener(d, new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) {
				if(e.getModifiers() == 0) {
					if(e.getKeyCode() == KeyEvent.VK_ESCAPE) {d.dispose();}
					for(char k : keys.keySet()) {
						if(Character.toLowerCase(e.getKeyChar()) == k) {
							keys.get(k).doClick();
						}
					}
				}
			}
		});
		
		Panacea.smartPack(d);
		d.setVisible(true);
		return rtn[0];
	}
}
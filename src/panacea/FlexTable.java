package panacea;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * FlexTable
 * @author  Michael Mrozek  Created Jan 5, 2007.
 */
@SuppressWarnings("serial") public class FlexTable extends JTable {
	private static class FlexModel extends AbstractTableModel {
		private FlexTable table;
		
		public FlexModel(FlexTable table) {this.table = table;}
		public int getColumnCount() {return this.table.columns.length;}
		@Override public String getColumnName(int i) {return this.table.columns[i];}
		public int getRowCount() {return this.table.rows.size();}
		public Object getValueAt(int rowIndex, int columnIndex) {return (this.table.rows.size() > rowIndex && this.table.rows.get(rowIndex).length > columnIndex) ? this.table.rows.get(rowIndex)[columnIndex] : null;}
		@Override public void setValueAt(Object value, int rowIndex, int columnIndex) {this.table.setElementAt(rowIndex, columnIndex, value);}
		@Override public Class<?> getColumnClass(int columnIndex) {return getValueAt(0, columnIndex) == null ? super.getColumnClass(columnIndex) : getValueAt(0, columnIndex).getClass();}
		@Override public boolean isCellEditable(int rowIndex, int columnIndex) {return this.table.isEditable;}
		public void dataChanged() {this.fireTableDataChanged();}
	}
	
	private String[] columns;
	private Vector<Object[]> rows = new Vector<Object[]>();
	private boolean isEditable = false;
	
//	private Condition<Integer> liteCondition;
//	private int[] litePercents;
//	private Color liteColor;
//	
//	private int sortRow = -1;
//	private boolean sortAscending = true;
	
	public FlexTable(String... columns) {
		this.columns = columns;
		
		FlexModel model = new FlexModel(this);
		this.setModel(model);
		this.setRowSorter(new TableRowSorter<TableModel>(model));
		this.getTableHeader().setBackground(new Color(220, 220, 220));
		this.getTableHeader().setReorderingAllowed(true);
	}
	
	public Object[][] getRows() {return this.rows.toArray(new Object[][] {});}
	
	public Object[] getRow(int row) {return this.rows.get(row);}
	
	public void addRow(Object... row) {
		if(row.length != this.columns.length) {throw new IllegalArgumentException("Column mismatch: Row columns (" + row.length + ") do not match header columns (" + this.columns.length + ")");}
		this.rows.add(row);
		((FlexModel)this.getModel()).dataChanged();
	}
	
	public void removeRow(int row) {
		this.rows.remove(row);
		((FlexModel)this.getModel()).dataChanged();
	}
	
	public void clearRows() {
		this.rows.clear();
		((FlexModel)this.getModel()).dataChanged();
	}
	
	public void setElementAt(int row,int column,Object object) {
		this.rows.get(row)[column] = object;
		((FlexModel)this.getModel()).dataChanged();
	}
	
	public void setEditable(boolean editable) {this.isEditable = editable;}
	public boolean isEdiable() {return this.isEditable;}
	
	public void setColumnWidths(int... widths) {
		if(widths.length != this.columns.length) {throw new IllegalArgumentException("Column mismatch: Call columns (" + widths.length + ") do not match header columns (" + this.columns.length + ")");}
		
		int remainingWidth = this.getWidth();
		if(remainingWidth == 0) {
			for(int i : widths) {
				if(i == 0) {
					throw new IllegalStateException("This FlexTable is not yet visible, so it cannot be sized with flex-columns, as it has no width");
				}
			}
		}
		
		Vector<TableColumn> flexColumns = new Vector<TableColumn>();
		
		for(int i = 0; i < widths.length; i++) {
			TableColumn column = this.getColumnModel().getColumn(i);
			if(widths[i] == 0) {
				flexColumns.add(column);
			} else {
				column.setPreferredWidth(widths[i]);
				remainingWidth -= widths[i];
			}
		}
		
		if(flexColumns.size() > 0) {
			int flexWidth = remainingWidth < 0 ? 0 : (int)(remainingWidth / flexColumns.size());
			for(TableColumn column : flexColumns) {column.setPreferredWidth(flexWidth);}
		}
	}
	
	public void sort(int column) {this.sort(column, true);}
	public void sort(int column, boolean ascending) {this.getRowSorter().setSortKeys(Arrays.asList(new RowSorter.SortKey[] {new RowSorter.SortKey(column, ascending ? SortOrder.ASCENDING : SortOrder.DESCENDING)}));}
	
	public void liteRows(final Condition<Integer> condition, final Color color) {
//		this.liteCondition = condition;
//		this.liteColor = color;
		
		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				setBackground(condition.satisfies(row) ? color : table.getBackground());
				return super.getTableCellRendererComponent(table, value, isSelected, hasFocus,row, column);
			}
		};
		
		for(String column : this.columns) {this.getColumn(column).setCellRenderer(renderer);}
	}
	
	public void liteRows(final int[] percents, final Color color) {
		int[][] full = new int[percents.length][this.columns.length];
		for(int i = 0; i < percents.length; i++) {
			Arrays.fill(full[i],percents[i]);
		}
		this.liteRows(full,color);
	}
	
	//percents: row,column
	public void liteRows(final int[][] percents, final Color color) {
		TableCellRenderer renderer = new TableCellRenderer() {
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				JProgressBar progressBar = new JProgressBar(0,100);
				
				if(row == 0 && column != FlexTable.this.columns.length) {progressBar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.BLACK));}
				if(row == 0 && column == FlexTable.this.columns.length) {progressBar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, Color.BLACK));}
				if(row != 0 && column != FlexTable.this.columns.length) {progressBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 1, Color.BLACK));}
				if(row != 0 && column == FlexTable.this.columns.length) {progressBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.BLACK));}
				
				progressBar.setValue(percents[row][column]);
				progressBar.setString(value.toString());
				progressBar.setStringPainted(true);
				progressBar.setUI(new BasicProgressBarUI() {
					//protected Color getSelectionBackground() {return new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue());}
					//protected Color getSelectionForeground() {return new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue());}
					@Override protected Color getSelectionBackground() {return Color.BLACK;}
					@Override protected Color getSelectionForeground() {return Color.BLACK;}
					@Override protected Point getStringPlacement(Graphics g, String progressString, int x, int y, int width, int height) {return new Point(5, super.getStringPlacement(g, progressString, x, y, width, height).y);}
					@Override protected void paintDeterminate(Graphics g, JComponent c) {
						c.setForeground(color);
						super.paintDeterminate(g, c);
					}
				});
				return progressBar;
			}
		};
		
		for(String column : this.columns) {this.getColumn(column).setCellRenderer(renderer);}
	}
	
	public void toggleVisibility() {
		this.setVisible(false);
		this.setVisible(true);
	}
}
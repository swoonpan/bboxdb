/*******************************************************************************
 *
 *    Copyright (C) 2015-2016
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package org.bboxdb.tools.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import org.bboxdb.distribution.DistributionGroupName;
import org.bboxdb.distribution.DistributionRegion;
import org.bboxdb.distribution.membership.DistributedInstance;
import org.bboxdb.distribution.membership.event.DistributedInstanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BBoxDBGui {
	
	/**
	 * The main frame
	 */
	protected JFrame mainframe;
	
	/**
	 * The main panel
	 */
	protected JSplitPane mainPanel;
	
	/**
	 * The list model for the distribution groups
	 */
	protected DefaultListModel<String> listModel;
	
	/**
	 * The left menu list
	 */
	protected JList<String> leftList;
	
	/**
	 * The Menu bar
	 */
	protected JMenuBar menuBar;
	
	/**
	 * The table Model
	 */
	protected BBoxDBInstanceTableModel tableModel;

	/**
	 * The GUI Model
	 */
	protected GuiModel guiModel;

	/**
	 * Shutdown the GUI ?
	 */
	public volatile boolean shutdown = false;
	
	/**
	 * The Logger
	 */
	protected final static Logger logger = LoggerFactory.getLogger(BBoxDBGui.class);
	
	public BBoxDBGui(final GuiModel guiModel) {
		this.guiModel = guiModel;
	}

	/**
	 * Build the BBoxDB dialog, init GUI components
	 * and assemble the dialog
	 */
	public void run() {
		
		mainframe = new JFrame("BBoxDB - Data Distribution");
		
		setupMenu();
		setupMainPanel();
		
		tableModel = getTableModel();
		final JTable table = new JTable(tableModel);
		table.getColumnModel().getColumn(0).setMaxWidth(40);
		table.getColumnModel().getColumn(2).setMinWidth(100);
		table.getColumnModel().getColumn(2).setMaxWidth(100);
		
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -2405592763548531423L;

			@Override
			public Component getTableCellRendererComponent(JTable table,
					Object value, boolean isSelected, boolean hasFocus,
					int row, int column) {
				
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				
		        final String state = (String) table.getModel().getValueAt(row, 4);
		        
		        if(DistributedInstanceState.READONLY.getZookeeperValue().equals(state)) {
		        	setBackground(Color.YELLOW);
		        } else if(DistributedInstanceState.READWRITE.getZookeeperValue().equals(state)) {
		        	setBackground(Color.GREEN);
		        } else {
		        	setBackground(table.getBackground());
		        }
				
				return this;
			}
		});

		final JScrollPane tableScrollPane = new JScrollPane(table);		
		final Dimension d = table.getPreferredSize();
		
		tableScrollPane.setPreferredSize(
		    new Dimension(d.width,table.getRowHeight()*7));
				
		mainframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainframe.setLayout(new BorderLayout());
		mainframe.add(mainPanel, BorderLayout.CENTER);
		mainframe.add(tableScrollPane, BorderLayout.SOUTH);

		mainframe.pack();
		GuiHelper.setCenterPosition(mainframe);
		mainframe.setVisible(true);
	}

	/** 
	 * Dispose the main frame
	 */
	public void dispose() {
		mainframe.dispose();
	}
	
	/**
	 * Get the table model for the schedules queries
	 * @return The table model
	 */
	private BBoxDBInstanceTableModel getTableModel() {
		final List<DistributedInstance> bboxDBInstances = guiModel.getBBoxDBInstances();
		return new BBoxDBInstanceTableModel(bboxDBInstances);
	}

	/**
	 * Initialize the GUI panel
	 * 
	 */
	protected void setupMainPanel() {
        
		
		final JPanel rightPanel = new JPanel() {
		
			private static final long serialVersionUID = -248493308846818192L;
			
			protected List<DistributionRegionComponent> regions = new ArrayList<DistributionRegionComponent>();
			
			protected void drawDistributionRegion(final Graphics2D graphics2d, final DistributionRegion distributionRegion) {

				if(distributionRegion == null) {
					return;
				}
				
				// The position of the root node
				final int rootPosX = getWidth() / 2;
				final int rootPosY = 30;
				
				final DistributionRegionComponent distributionRegionComponent = new DistributionRegionComponent(distributionRegion, rootPosX, rootPosY);
				distributionRegionComponent.drawComponent(graphics2d);
				regions.add(distributionRegionComponent);
				
				drawDistributionRegion(graphics2d, distributionRegion.getLeftChild());
				drawDistributionRegion(graphics2d, distributionRegion.getRightChild());
			}

			@Override
			protected void paintComponent(final Graphics g) {
				super.paintComponent(g);
	            final Graphics2D graphics2D = (Graphics2D) g;
	            graphics2D.setRenderingHint(
	                    RenderingHints.KEY_ANTIALIASING, 
	                    RenderingHints.VALUE_ANTIALIAS_ON);
	            
	            // Group is not set
	            if(guiModel.getTreeAdapter() == null) {
	            	return;
	            }
	            
	    		final DistributionRegion distributionRegion = guiModel.getTreeAdapter().getRootNode();

    			final int totalLevel = distributionRegion.getTotalLevel();
    			
    			final int totalWidth =  
    					+ ((DistributionRegionComponent.LEFT_RIGHT_OFFSET 
    							+ DistributionRegionComponent.WIDTH) * totalLevel) * 2;
    			final int totalHeight = DistributionRegionComponent.HEIGHT 
    					+ (totalLevel * DistributionRegionComponent.LEVEL_DISTANCE);

	            regions.clear();
	            drawDistributionRegion(graphics2D, distributionRegion);
	            
    			setPreferredSize(new Dimension(totalWidth, totalHeight));
    			setSize(new Dimension(totalWidth, totalHeight));
    			
				g.drawString("Cluster name: " + guiModel.getClustername(), 10, 20);
				g.drawString("Distribution group: " + guiModel.getDistributionGroup(), 10, 40);
				g.drawString("Replication factor: " + guiModel.getReplicationFactor(), 10, 60);
			}
			
			/**
			 * Get the text for the tool tip
			 */
			@Override
			public String getToolTipText(final MouseEvent event) {

	            for(final DistributionRegionComponent component : regions) {
	            	if(component.isMouseOver(event)) {
	            		return component.getToolTipText();
	            	}
	            }
	            
	            return "";
			}
		};

		rightPanel.setBackground(Color.WHITE);
		rightPanel.setToolTipText("");
		
		final JScrollPane rightScrollPanel = new JScrollPane(rightPanel);
		
		final JList<String> leftPanel = getLeftPanel();
		
		mainPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightScrollPanel);
		mainPanel.setOneTouchExpandable(true);
		mainPanel.setDividerLocation(150);
		mainPanel.setPreferredSize(new Dimension(800, 600));
	}

	/**
	 * Generate the left panel
	 * @return
	 */
	protected JList<String> getLeftPanel() {
	
		listModel = new DefaultListModel<String>();
		
		leftList = new JList<String>(listModel);
		
		refreshDistributionGroups(listModel);
	
		leftList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		leftList.addListSelectionListener(new ListSelectionListener() {
			
			@Override
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
	                guiModel.setDistributionGroup(leftList.getSelectedValue());
                }
			}
		});

		return leftList;
	}

	/**
	 * Refresh the distributions groups
	 * @param listModel
	 */
	protected void refreshDistributionGroups(final DefaultListModel<String> listModel) {
		final List<DistributionGroupName> distributionGroups = new ArrayList<DistributionGroupName>();
		
		try {
			distributionGroups.addAll(guiModel.getDistributionGroups());
		} catch (Exception e) {
			logger.error("Got an exception while loading distribution groups");
		}
		
		Collections.sort(distributionGroups);
		listModel.clear();
		for(final DistributionGroupName distributionGroupName : distributionGroups) {
			listModel.addElement(distributionGroupName.getFullname());
		}
	}

	/**
	 * Create the menu of the main window
	 */
	protected void setupMenu() {
		menuBar = new JMenuBar();
		final JMenu menu = new JMenu("File");
		menuBar.add(menu);
		
		final JMenuItem reloadItem = new JMenuItem("Reload Distribution Groups");
		reloadItem.addActionListener(new AbstractAction() {
			
			private static final long serialVersionUID = -5380326547117916348L;

			public void actionPerformed(final ActionEvent e) {
				refreshDistributionGroups(listModel);
			}
			
		});
		menu.add(reloadItem);
		
		final JMenuItem closeItem = new JMenuItem("Close");
		closeItem.addActionListener(new AbstractAction() {
			
			private static final long serialVersionUID = -5380326547117916348L;

			public void actionPerformed(final ActionEvent e) {
				shutdown = true;
			}
			
		});
		menu.add(closeItem);
		
		
        mainframe.setJMenuBar(menuBar);
	}
	
	/**
	 * Update the gui model
	 */
	public synchronized void updateStatus() {
		if(tableModel != null) {
			tableModel.fireTableDataChanged();
		}
	}
	
	/**
	 * Update the view. This method should be called periodically
	 */
	public void updateView() {
		updateStatus();
		mainframe.repaint();
	}
	
}
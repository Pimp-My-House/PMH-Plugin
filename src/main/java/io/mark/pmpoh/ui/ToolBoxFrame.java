package io.mark.pmpoh.ui;

import io.mark.pmpoh.PimpMyPohPlugin;
import io.mark.pmpoh.objects.ObjectManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

/**
 * External Swing frame for the POH editor toolbox.
 * 
 * Layout:
 * - Top toolbar (title + future actions)
 * - Center tabbed pane with \"Objects\" and \"House Manager\" tabs
 * - Objects tab: Split pane with paginated list on left, toolbar on right
 */
@Slf4j
@Singleton
public class ToolBoxFrame extends JFrame
{
    private static final boolean ALWAYS_ALLOW_FRAME = true; // Debug flag: allows opening toolbox without POH/save requirements
    
    private JTabbedPane tabbedPane;
    private JPanel scatterSettingsPanel;
    private JPanel carpetSettingsPanel;
    private String currentPlacementType = "single"; // "single", "scatter", "carpet"
    
    // Scatter settings
    @Getter
    private int scatterAmount = 5;
    @Getter
    private int scatterDensity = 5;
    @Getter
    private boolean scatterRandomRotations = false;
    
    // Carpet settings
    @Getter
    private String carpetSize = "3x3";
    
    @Inject
    private ObjectManager objectManager;
    
    @Inject
    private PimpMyPohPlugin plugin;

    @Inject
    public ToolBoxFrame()
    {
        setTitle("Pimp My POH - Toolbox");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Top toolbar
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel titleLabel = new JLabel("POH Toolbox");
        titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));

        toolbar.add(titleLabel, BorderLayout.WEST);

        // Placeholder toolbar buttons area for future actions
        JPanel toolbarButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbarButtons.setOpaque(false);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setFocusable(false);
        refreshButton.setToolTipText("Refresh toolbox state (placeholder action)");
        refreshButton.addActionListener(e -> log.debug("Toolbox refresh requested"));

        toolbarButtons.add(refreshButton);
        toolbar.add(toolbarButtons, BorderLayout.EAST);

        contentPanel.add(toolbar, BorderLayout.NORTH);

        // Center tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabbedPane.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        // Objects tab with split pane (will be created lazily)
        JPanel objectsTab = createObjectsTab();

        // House Manager tab
        JPanel houseManagerTab = new JPanel(new BorderLayout());
        houseManagerTab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        houseManagerTab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel houseLabel = new JLabel("House Manager tools coming soon...");
        houseLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        houseLabel.setHorizontalAlignment(SwingConstants.CENTER);
        houseManagerTab.add(houseLabel, BorderLayout.CENTER);

        tabbedPane.addTab("Objects", objectsTab);
        tabbedPane.addTab("House Manager", houseManagerTab);

        contentPanel.add(tabbedPane, BorderLayout.CENTER);

        setContentPane(contentPanel);
    }
    
    private JPanel createObjectsTab() {
        JPanel objectsTab = new JPanel(new BorderLayout());
        objectsTab.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Create split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(600); // Left side gets 600px, right side gets the rest
        splitPane.setDividerSize(5);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);
        splitPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Left side: Objects list panel (check for null to handle injection timing)
        if (objectManager != null && plugin != null) {
            ObjectsListPanel objectsListPanel = new ObjectsListPanel(objectManager, plugin);
            splitPane.setLeftComponent(objectsListPanel);
        } else {
            // Placeholder if injection hasn't happened yet
            JLabel placeholder = new JLabel("Loading objects...");
            placeholder.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            placeholder.setHorizontalAlignment(SwingConstants.CENTER);
            splitPane.setLeftComponent(placeholder);
        }
        
        // Right side: Toolbar with model buttons
        JPanel rightPanel = createRightToolbarPanel();
        splitPane.setRightComponent(rightPanel);
        
        objectsTab.add(splitPane, BorderLayout.CENTER);
        
        return objectsTab;
    }
    
    private JPanel createRightToolbarPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Floating toolbar panel - spans full width
        JPanel toolbarPanel = new JPanel();
        toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.Y_AXIS));
        toolbarPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        toolbarPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        toolbarPanel.setOpaque(true);
        toolbarPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolbarPanel.getMaximumSize().height));
        toolbarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Toolbar title
        JLabel toolbarTitle = new JLabel("Placement Type");
        toolbarTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        toolbarTitle.setFont(toolbarTitle.getFont().deriveFont(Font.PLAIN, 14f));
        toolbarTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbarPanel.add(toolbarTitle);
        toolbarPanel.add(Box.createVerticalStrut(10));
        
        // Placement type buttons in a row - full width
        JPanel buttonsRow = new JPanel(new GridLayout(1, 3, 5, 0));
        buttonsRow.setOpaque(false);
        buttonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        
        // Declare all buttons first
        JButton singleButton = new JButton("Single");
        JButton scatterButton = new JButton("Scatter");
        JButton carpetButton = new JButton("Carpet");
        
        // Create settings panels (need to be created before button listeners)
        scatterSettingsPanel = createScatterSettingsPanel();
        carpetSettingsPanel = createCarpetSettingsPanel();
        
        // Configure buttons with action listeners
        configurePlacementButton(singleButton, scatterButton, carpetButton, "single", 
            () -> {
                scatterSettingsPanel.setVisible(false);
                carpetSettingsPanel.setVisible(false);
            });
        configurePlacementButton(scatterButton, singleButton, carpetButton, "scatter",
            () -> {
                scatterSettingsPanel.setVisible(true);
                carpetSettingsPanel.setVisible(false);
            });
        configurePlacementButton(carpetButton, singleButton, scatterButton, "carpet",
            () -> {
                carpetSettingsPanel.setVisible(true);
                scatterSettingsPanel.setVisible(false);
            });
        
        // Set Single as default selected
        updateButtonStates(singleButton, scatterButton, carpetButton, singleButton);
        
        buttonsRow.add(singleButton);
        buttonsRow.add(scatterButton);
        buttonsRow.add(carpetButton);
        
        toolbarPanel.add(buttonsRow);
        toolbarPanel.add(Box.createVerticalStrut(10));
        
        // Add settings panels (initially hidden)
        scatterSettingsPanel.setVisible(false);
        carpetSettingsPanel.setVisible(false);
        toolbarPanel.add(scatterSettingsPanel);
        toolbarPanel.add(carpetSettingsPanel);
        
        // Add glue to push toolbar to top
        toolbarPanel.add(Box.createVerticalGlue());
        
        // Images panel (placeholder for now)
        JPanel imagesPanel = new JPanel(new BorderLayout());
        imagesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        imagesPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        imagesPanel.setPreferredSize(new Dimension(0, 200));
        
        JLabel imagesLabel = new JLabel("Images coming soon...");
        imagesLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        imagesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imagesPanel.add(imagesLabel, BorderLayout.CENTER);
        
        // Combine toolbar and images in a vertical box
        JPanel rightContent = new JPanel();
        rightContent.setLayout(new BoxLayout(rightContent, BoxLayout.Y_AXIS));
        rightContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightContent.setOpaque(false);
        
        // Make toolbar panel span full width
        toolbarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbarPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolbarPanel.getPreferredSize().height));
        
        rightContent.add(toolbarPanel);
        rightContent.add(Box.createVerticalStrut(10));
        rightContent.add(imagesPanel);
        rightContent.add(Box.createVerticalGlue());
        
        rightPanel.add(rightContent, BorderLayout.CENTER);
        
        return rightPanel;
    }
    
    private JPanel createScatterSettingsPanel() {
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        settingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, settingsPanel.getMaximumSize().height));
        
        // Scatter amount setting
        JPanel amountPanel = new JPanel(new BorderLayout(5, 0));
        amountPanel.setOpaque(false);
        amountPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel amountLabel = new JLabel("Amount:");
        amountLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        amountLabel.setPreferredSize(new Dimension(80, 20));
        
        JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        amountSpinner.setPreferredSize(new Dimension(60, 25));
        amountSpinner.addChangeListener(e -> {
            scatterAmount = (Integer) amountSpinner.getValue();
            log.debug("Scatter amount changed: {}", scatterAmount);
        });
        
        amountPanel.add(amountLabel, BorderLayout.WEST);
        amountPanel.add(amountSpinner, BorderLayout.CENTER);
        
        // Density setting
        JPanel densityPanel = new JPanel(new BorderLayout(5, 0));
        densityPanel.setOpaque(false);
        densityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel densityLabel = new JLabel("Density:");
        densityLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        densityLabel.setPreferredSize(new Dimension(80, 20));
        
        JSpinner densitySpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
        densitySpinner.setPreferredSize(new Dimension(60, 25));
        densitySpinner.addChangeListener(e -> {
            scatterDensity = (Integer) densitySpinner.getValue();
            log.debug("Density changed: {}", scatterDensity);
        });
        
        densityPanel.add(densityLabel, BorderLayout.WEST);
        densityPanel.add(densitySpinner, BorderLayout.CENTER);
        
        // Random rotations checkbox
        JCheckBox randomRotationsCheck = new JCheckBox("Random Rotations");
        randomRotationsCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        randomRotationsCheck.setOpaque(false);
        randomRotationsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomRotationsCheck.addActionListener(e -> {
            scatterRandomRotations = randomRotationsCheck.isSelected();
            log.debug("Random rotations: {}", scatterRandomRotations);
        });
        
        settingsPanel.add(amountPanel);
        settingsPanel.add(Box.createVerticalStrut(5));
        settingsPanel.add(densityPanel);
        settingsPanel.add(Box.createVerticalStrut(5));
        settingsPanel.add(randomRotationsCheck);
        
        return settingsPanel;
    }
    
    private JPanel createCarpetSettingsPanel() {
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        settingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        settingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, settingsPanel.getMaximumSize().height));
        
        // Size setting
        JPanel sizePanel = new JPanel(new BorderLayout(5, 0));
        sizePanel.setOpaque(false);
        sizePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel sizeLabel = new JLabel("Size:");
        sizeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sizeLabel.setPreferredSize(new Dimension(80, 20));
        
        String[] sizeOptions = {"1x1", "2x2", "3x3", "4x4", "5x5"};
        JComboBox<String> sizeComboBox = new JComboBox<>(sizeOptions);
        sizeComboBox.setSelectedIndex(2); // Default to 3x3
        sizeComboBox.setPreferredSize(new Dimension(80, 25));
        sizeComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        sizeComboBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        sizeComboBox.setFocusable(false);
        sizeComboBox.addActionListener(e -> {
            carpetSize = (String) sizeComboBox.getSelectedItem();
            log.debug("Carpet size changed: {}", carpetSize);
        });
        
        sizePanel.add(sizeLabel, BorderLayout.WEST);
        sizePanel.add(sizeComboBox, BorderLayout.CENTER);
        
        settingsPanel.add(sizePanel);
        
        return settingsPanel;
    }
    
    private void configurePlacementButton(JButton button, JButton other1, JButton other2, String type, Runnable onSelect) {
        button.setFocusable(false);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        button.addActionListener(e -> {
            log.debug("{} placement selected", type);
            currentPlacementType = type;
            updateButtonStates(button, other1, other2, button);
            onSelect.run();
        });
    }
    
    private void updateButtonStates(JButton selected, JButton unselected1, JButton unselected2, JButton defaultButton) {
        selected.setBackground(ColorScheme.DARK_GRAY_COLOR);
        selected.setForeground(Color.WHITE);
        unselected1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        unselected1.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        unselected2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        unselected2.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
    }

    public void showToolbox()
    {
        // Don't allow opening if objects are still loading
        if (objectManager != null && (objectManager.isLoading() || !objectManager.isReady())) {
            log.warn("Cannot open toolbox: objects are still loading or not ready");
            return;
        }
        
        // Ensure objects tab is properly initialized (in case injection happened after construction)
        refreshObjectsTab();
        
        // Debug mode: only check ObjectManager is ready, skip POH/save checks
        if (ALWAYS_ALLOW_FRAME && objectManager != null && objectManager.isReady()) {
            setVisible(true);
            toFront();
            repaint();
            return;
        }
        
        setVisible(true);
        toFront();
        repaint();
    }
    
    private void refreshObjectsTab() {
        if (tabbedPane == null) {
            return;
        }
        
        // Get the objects tab component
        Component objectsTabComponent = tabbedPane.getComponentAt(0); // Objects tab is first
        if (objectsTabComponent instanceof JPanel) {
            JPanel objectsTab = (JPanel) objectsTabComponent;
            
            // Check if we need to update the left component
            JSplitPane splitPane = findSplitPane(objectsTab);
            if (splitPane != null) {
                Component leftComponent = splitPane.getLeftComponent();
                
                // If left component is a placeholder or ObjectsListPanel wasn't created, create it now
                if (leftComponent instanceof JLabel || (objectManager != null && plugin != null && !(leftComponent instanceof ObjectsListPanel))) {
                    if (objectManager != null && plugin != null) {
                        ObjectsListPanel objectsListPanel = new ObjectsListPanel(objectManager, plugin);
                        splitPane.setLeftComponent(objectsListPanel);
                        objectsTab.revalidate();
                        objectsTab.repaint();
                    }
                }
            }
        }
    }
    
    private JSplitPane findSplitPane(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JSplitPane) {
                return (JSplitPane) comp;
            } else if (comp instanceof Container) {
                JSplitPane found = findSplitPane((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}


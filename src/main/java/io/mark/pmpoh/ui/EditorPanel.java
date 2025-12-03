package io.mark.pmpoh.ui;

import io.mark.pmpoh.PimpMyPohPlugin;
import io.mark.pmpoh.objects.ObjectManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

public class EditorPanel extends PluginPanel
{
    private static final boolean ALWAYS_ALLOW_FRAME = true; // Debug flag: allows opening toolbox without POH/save requirements
    
    private PimpMyPohPlugin pimpMyPohPlugin;
    
    @Inject
    private ToolBoxFrame toolBoxFrame;
    
    @Inject
    private Client client;
    
    @Inject
    private ObjectManager objectManager;
    
    private JButton toolBoxButton;
    private JButton retryButton;
    private PluginErrorPanel errorPanel;
    
    private Timer statusCheckTimer;

    @Inject
    private EditorPanel(PimpMyPohPlugin pimpMyPohPlugin)
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.pimpMyPohPlugin = pimpMyPohPlugin;
    }
    
    public void setup()
    {
        setLayout(new BorderLayout());
        
        // Error panel for status messages (centered)
        errorPanel = new PluginErrorPanel();
        add(errorPanel, BorderLayout.CENTER);
        
        // Toolbox button at the bottom
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BorderLayout());
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        toolBoxButton = new JButton("Open Toolbox");
        toolBoxButton.setToolTipText("Opens an external interface for advanced POH editing tools");
        toolBoxButton.setFocusable(false);
        toolBoxButton.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                toolBoxFrame.showToolbox();
                revalidate();
                repaint();
            });
        });
        
        retryButton = new JButton("Try Again");
        retryButton.setToolTipText("Retry downloading objects.json");
        retryButton.setFocusable(false);
        retryButton.addActionListener(e -> {
            objectManager.retryLoad();
            updateToolboxButtonState();
        });
        retryButton.setVisible(false);
        
        // Add toolbox button by default
        buttonPanel.add(toolBoxButton, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Start timer to periodically check loading state
        statusCheckTimer = new Timer(500, e -> updateToolboxButtonState());
        statusCheckTimer.start();
        
        // Update button state
        updateToolboxButtonState();
    }
    
    /**
     * Update the toolbox button state based on conditions
     */
    public void updateToolboxButtonState() {
        if (toolBoxButton == null || errorPanel == null || objectManager == null) {
            return; // Not initialized yet
        }
        
        // Check if objects are still loading
        if (objectManager.isLoading()) {
            // Make sure toolbox button is showing (not retry button)
            if (retryButton != null && retryButton.getParent() != null && retryButton.isVisible()) {
                JPanel buttonPanel = (JPanel) retryButton.getParent();
                buttonPanel.remove(retryButton);
                buttonPanel.add(toolBoxButton, BorderLayout.CENTER);
                buttonPanel.revalidate();
                buttonPanel.repaint();
            }
            toolBoxButton.setEnabled(false);
            toolBoxButton.setVisible(true);
            errorPanel.setContent("Loading Objects", "Downloading and parsing objects.json... Please wait.");
            toolBoxFrame.setVisible(false);
            return;
        }
        

        if (!objectManager.isReady() && objectManager.getLoadingError() != null) {
            if (retryButton != null && toolBoxButton.getParent() != null) {
                JPanel buttonPanel = (JPanel) toolBoxButton.getParent();
                buttonPanel.remove(toolBoxButton);
                buttonPanel.add(retryButton, BorderLayout.CENTER);
                retryButton.setVisible(true);
                retryButton.setEnabled(true);
                buttonPanel.revalidate();
                buttonPanel.repaint();
            }
            errorPanel.setContent("Failed to Load Objects", "Please try again..");
            toolBoxFrame.setVisible(false);
            return;
        }
        
        // Objects are ready, check other conditions
        // Swap back to toolbox button if retry button is showing
        if (retryButton != null && retryButton.getParent() != null && retryButton.isVisible()) {
            JPanel buttonPanel = (JPanel) retryButton.getParent();
            buttonPanel.remove(retryButton);
            buttonPanel.add(toolBoxButton, BorderLayout.CENTER);
            toolBoxButton.setVisible(true);
            buttonPanel.revalidate();
            buttonPanel.repaint();
        }
        
        // Debug mode: only check ObjectManager is ready, skip POH/save checks
        if (ALWAYS_ALLOW_FRAME && objectManager.isReady()) {
            toolBoxButton.setEnabled(true);
            errorPanel.setContent("Toolbox Ready (Debug Mode)", "Click 'Open Toolbox' to use advanced POH editing tools.");
            toolBoxButton.setToolTipText("Opens an external interface for advanced POH editing tools");
            return;
        }
        
        boolean isLoggedIn = client.getGameState() == GameState.LOGGED_IN;
        boolean inPoh = client.getGameState() == GameState.LOGGED_IN && pimpMyPohPlugin.isInPoh();
        boolean hasSave = client.getGameState() == GameState.LOGGED_IN && pimpMyPohPlugin.hasSaveFile();

        if (!isLoggedIn) {
            toolBoxButton.setEnabled(false);
            errorPanel.setContent("Not Logged In", "You must be logged in to use the toolbox");
            toolBoxButton.setToolTipText("You must be logged in to use the toolbox");
            toolBoxFrame.setVisible(false);
        } else if (!inPoh) {
            toolBoxButton.setEnabled(false);
            errorPanel.setContent("Not in POH", "You must be in your POH to use the toolbox");
            toolBoxButton.setToolTipText("You must be in your POH to use the toolbox");
            toolBoxFrame.setVisible(false);
        } else if (!hasSave) {
            toolBoxButton.setEnabled(false);
            errorPanel.setContent("No Save File", "Please view your POH first to create a save file");
            toolBoxButton.setToolTipText("Please view your POH first to create a save file");
            toolBoxFrame.setVisible(false);
        } else {
            toolBoxButton.setEnabled(true);
            errorPanel.setContent("Toolbox Ready", "Click 'Open Toolbox' to use advanced POH editing tools.");
            toolBoxButton.setToolTipText("Opens an external interface for advanced POH editing tools");
        }
    }
}

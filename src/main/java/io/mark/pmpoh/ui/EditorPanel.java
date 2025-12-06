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
            return;
        }
        
        // Check if objects are still loading
        if (objectManager.isLoading()) {
            showToolboxButton();
            toolBoxButton.setEnabled(false);
            toolBoxButton.setVisible(true);
            errorPanel.setContent("Loading Objects", "Downloading and parsing objects.json... Please wait.");
            toolBoxFrame.setVisible(false);
            return;
        }
        
        // Check if loading failed
        if (!objectManager.isReady() && objectManager.getLoadingError() != null) {
            showRetryButton();
            errorPanel.setContent("Failed to Load Objects", "Please try again..");
            toolBoxFrame.setVisible(false);
            return;
        }
        
        // Objects are ready
        showToolboxButton();
        
        // Dev mode: only check ObjectManager is ready, skip POH/save checks
        if (pimpMyPohPlugin != null && pimpMyPohPlugin.isDevMode() && objectManager.isReady()) {
            toolBoxButton.setEnabled(true);
            errorPanel.setContent("Toolbox Ready (Dev Mode)", "Click 'Open Toolbox' to use advanced POH editing tools.");
            toolBoxButton.setToolTipText("Opens an external interface for advanced POH editing tools");
            return;
        }
        
        // Normal mode: check login, POH, and save file
        boolean isLoggedIn = client.getGameState() == GameState.LOGGED_IN;
        boolean inPoh = isLoggedIn && pimpMyPohPlugin.isInPoh();
        boolean hasSave = isLoggedIn && pimpMyPohPlugin.hasSaveFile();

        if (!isLoggedIn) {
            setButtonState(false, "Not Logged In", "You must be logged in to use the toolbox");
        } else if (!inPoh) {
            setButtonState(false, "Not in POH", "You must be in your POH to use the toolbox");
        } else if (!hasSave) {
            setButtonState(false, "No Save File", "Please view your POH first to create a save file");
        } else {
            setButtonState(true, "Toolbox Ready", "Click 'Open Toolbox' to use advanced POH editing tools.");
        }
    }
    
    private void showToolboxButton() {
        if (retryButton != null && retryButton.getParent() != null && retryButton.isVisible()) {
            swapButton(retryButton, toolBoxButton);
        }
    }
    
    private void showRetryButton() {
        if (retryButton != null && toolBoxButton.getParent() != null) {
            swapButton(toolBoxButton, retryButton);
            retryButton.setVisible(true);
            retryButton.setEnabled(true);
        }
    }
    
    private void swapButton(JButton toRemove, JButton toAdd) {
        JPanel buttonPanel = (JPanel) toRemove.getParent();
        if (buttonPanel != null) {
            buttonPanel.remove(toRemove);
            buttonPanel.add(toAdd, BorderLayout.CENTER);
            buttonPanel.revalidate();
            buttonPanel.repaint();
        }
    }
    
    private void setButtonState(boolean enabled, String title, String message) {
        toolBoxButton.setEnabled(enabled);
        errorPanel.setContent(title, message);
        toolBoxButton.setToolTipText(message);
        toolBoxFrame.setVisible(false);
    }
}

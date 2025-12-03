package io.mark.pmpoh.ui;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

class ObjectListSlot extends JPanel
{
    @Getter(AccessLevel.PACKAGE)
    private final String gameval;

    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PACKAGE)
    private boolean isSelected;

    private final JLabel uiLabelName;
    private final JLabel favoriteIconLabel;
    private final JPopupMenu contextMenu;
    private final int objectId;
    private boolean isFavorite;
    private final boolean showFavoriteIcon;

    ObjectListSlot(String gameval, int objectId, Consumer<String> onSelect, Consumer<String> onEdit, Consumer<String> onToggleFavorite, boolean isFavorite, ImageIcon favoriteIcon, boolean showFavoriteIcon)
    {
        this.gameval = gameval;
        this.objectId = objectId;
        this.isFavorite = isFavorite;
        this.showFavoriteIcon = showFavoriteIcon;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(new EmptyBorder(7, 12, 7, 7));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        // Create context menu
        contextMenu = new JPopupMenu();
        JMenuItem editMenuItem = new JMenuItem("Edit");
        editMenuItem.addActionListener(e -> onEdit.accept(gameval));
        contextMenu.add(editMenuItem);
        
        JMenuItem favoriteMenuItem = new JMenuItem(isFavorite ? "Remove from Favorites" : "Add to Favorites");
        favoriteMenuItem.addActionListener(e -> {
            onToggleFavorite.accept(gameval);
            // Update menu text after toggle (isFavorite will be updated by setFavorite)
            favoriteMenuItem.setText(this.isFavorite ? "Remove from Favorites" : "Add to Favorites");
        });
        contextMenu.add(favoriteMenuItem);

        // Mouse listener for selection and context menu
        MouseAdapter mouseListener = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (!isSelected)
                {
                    setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
                }
                else
                {
                    // Keep green when selected, but slightly brighter on hover
                    setBackground(new Color(0, 180, 0));
                }
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                updateBackground();
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e))
                {
                    onSelect.accept(gameval);
                }
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton(e))
                {
                    contextMenu.show(ObjectListSlot.this, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton(e))
                {
                    contextMenu.show(ObjectListSlot.this, e.getX(), e.getY());
                }
            }
        };
        addMouseListener(mouseListener);

        // Make cursor change to hand cursor on hover
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Favorite icon label (only shown in "all" tab when favorited)
        favoriteIconLabel = new JLabel();
        if (favoriteIcon != null && showFavoriteIcon && isFavorite) {
            favoriteIconLabel.setIcon(favoriteIcon);
            favoriteIconLabel.setPreferredSize(new Dimension(favoriteIcon.getIconWidth(), favoriteIcon.getIconHeight()));
        }
        favoriteIconLabel.setVisible(showFavoriteIcon && isFavorite);

        // Label
        uiLabelName = new JLabel(gameval);
        uiLabelName.setForeground(Color.WHITE);
        uiLabelName.setHorizontalAlignment(SwingConstants.LEFT);

        // Layout structure: icon (if shown) then label
        if (showFavoriteIcon) {
            add(favoriteIconLabel);
            add(Box.createHorizontalStrut(5)); // Small gap between icon and text
        }
        add(uiLabelName);
        add(Box.createHorizontalGlue());
    }
    
    void setFavorite(boolean favorite, ImageIcon favoriteIcon)
    {
        this.isFavorite = favorite;
        if (favoriteIconLabel != null && favoriteIcon != null) {
            favoriteIconLabel.setIcon(favoriteIcon);
            favoriteIconLabel.setVisible(showFavoriteIcon && favorite);
            revalidate();
            repaint();
        }
    }

    void setSelected(boolean selected)
    {
        this.isSelected = selected;
        updateBackground();
    }

    private void updateBackground()
    {
        if (isSelected)
        {
            // Nice green color for selected state
            setBackground(new Color(0, 150, 0)); // Dark green
        }
        else
        {
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
        }
    }

    @Override
    public void setBackground(Color color)
    {
        super.setBackground(color);
        if (uiLabelName != null)
        {
            repaint();
        }
    }

}
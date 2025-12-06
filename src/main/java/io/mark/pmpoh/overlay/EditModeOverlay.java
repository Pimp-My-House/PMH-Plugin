package io.mark.pmpoh.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;

import io.mark.pmpoh.PimpMyPohConfig;
import io.mark.pmpoh.tooling.impl.ObjectAction;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class EditModeOverlay extends OverlayPanel {

    private final Client client;
    private final ObjectAction objectAction;
    private final PimpMyPohConfig config;

    @Inject
    public EditModeOverlay(Client client, ObjectAction objectAction, PimpMyPohConfig config)
    {
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        this.client = client;
        this.objectAction = objectAction;
        this.config = config;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Only show overlay when in edit mode
        if (!objectAction.isEditMode())
        {
            return null;
        }

        Font boldFont = graphics.getFont().deriveFont(Font.BOLD);

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("EDIT MODE ACTIVE")
            .color(Color.YELLOW)
            .build());

        // Tips
        panelComponent.getChildren().add(LineComponent.builder()
            .leftFont(boldFont)
            .left("Press " + config.editModeKeybind().toString() + " to toggle edit mode")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Press " + config.rotateKeybind().toString() + " to rotate object")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Left-click to place object")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Other actions are blocked")
            .build());

        return super.render(graphics);
    }
}

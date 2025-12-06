package io.mark.pmpoh;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

import java.awt.event.KeyEvent;

@ConfigGroup(PimpMyPohConfig.CONFIG_GROUP)
public interface PimpMyPohConfig extends Config {

    String CONFIG_GROUP = "pmpoh";

    @ConfigItem(
        keyName = "editModeKeybind",
        name = "Toggle Edit Mode",
        description = "Keybind to toggle edit mode on/off"
    )
    default Keybind editModeKeybind()
    {
        return Keybind.CTRL;
    }

    @ConfigItem(
        keyName = "rotateKeybind",
        name = "Rotate Object",
        description = "Keybind to rotate the selected object"
    )
    default Keybind rotateKeybind()
    {
        return new Keybind(KeyEvent.VK_T, 0);
    }
}
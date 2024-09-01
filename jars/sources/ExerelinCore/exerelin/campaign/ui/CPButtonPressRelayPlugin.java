package exerelin.campaign.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;

/**
 * Used to relay button presses from the innermost custom panel to one actually able to handle the buttons.
 * See https://fractalsoftworks.com/forum/index.php?topic=5061.msg398449#msg398449
 */
public class CPButtonPressRelayPlugin extends BaseCustomUIPanelPlugin {

    public CustomUIPanelPlugin buttonHandler;

    public CPButtonPressRelayPlugin(CustomUIPanelPlugin buttonHandler) {
        this.buttonHandler = buttonHandler;
    }

    @Override
    public void buttonPressed(Object buttonId) {
        buttonHandler.buttonPressed(buttonId);
    }
}

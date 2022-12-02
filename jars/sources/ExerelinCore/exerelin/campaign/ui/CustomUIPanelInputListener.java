package exerelin.campaign.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.Map;

public interface CustomUIPanelInputListener {

    public void notifyInput(CustomUIPanelPlugin plugin, InputEventAPI input, Map<String, Object> params);

}

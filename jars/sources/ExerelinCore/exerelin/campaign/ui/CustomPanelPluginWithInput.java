package exerelin.campaign.ui;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public abstract class CustomPanelPluginWithInput implements CustomUIPanelPlugin {

    protected PositionAPI pos;
    protected boolean lmbDown;
    protected boolean rmbDown;

    protected Set<CustomUIPanelInputListener> listeners = new HashSet<>();

    @Override
    public void positionChanged(PositionAPI pos) {
        this.pos = pos;
    }

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {

            if (event.isLMBDownEvent() && !lmbDown && pos.containsEvent(event)) {
                lmbDown = true;
                //String format = String.format("LMB down: x/y [%s, %s], dx/dy [%s, %s]", event.getX(), event.getY(), event.getDX(), event.getDY());
                //log.info(format);

            }
            else if (event.isLMBUpEvent() && lmbDown) {
                lmbDown = false;
                if (pos.containsEvent(event)) {
                    notifyListeners(event, new HashMap<String, Object>());
                }
                //String format = String.format("LMB up: x/y [%s, %s], dx/dy [%s, %s]", event.getX(), event.getY(), event.getDX(), event.getDY());
                //log.info(format);
            }
        }
    }

    public void notifyListeners(InputEventAPI event, Map<String, Object> params) {
        for (CustomUIPanelInputListener listener : listeners) {
            listener.notifyInput(this, event, params);
        }
    }

    public void addListener(CustomUIPanelInputListener listener) {
        listeners.add(listener);
    }

    public boolean removeListener(CustomUIPanelInputListener listener) {
        return listeners.remove(listener);
    }
}

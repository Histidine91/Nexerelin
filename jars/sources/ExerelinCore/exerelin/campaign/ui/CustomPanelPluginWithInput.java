package exerelin.campaign.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public abstract class CustomPanelPluginWithInput extends BaseCustomUIPanelPlugin {

    protected PositionAPI pos;
    protected boolean lmbDown;
    protected boolean rmbDown;
    public boolean isCheckButtonsEveryFrame = true;

    protected List<ButtonEntry> buttons = new LinkedList<>();
    protected Map<Object, ButtonEntry> buttonsById = new HashMap<>();

    protected Set<CustomUIPanelInputListener> listeners = new HashSet<>();

    @Getter @Setter CustomPanelPluginWithInput buttonPressHandler;

    @Override
    public void positionChanged(PositionAPI pos) {
        this.pos = pos;
    }

    public void checkAllButtons() {
        Iterator<ButtonEntry> iter = buttons.iterator();
        while (iter.hasNext()) {
            iter.next().checkButton();
        }
    }

    @Override
    public void advance(float amount) {
        if (isCheckButtonsEveryFrame) checkAllButtons();
    }

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

    public void addButton(ButtonEntry entry) {
        buttons.add(entry);
        buttonsById.put(entry.id, entry);
    }

    public void addListener(CustomUIPanelInputListener listener) {
        listeners.add(listener);
    }

    public boolean removeListener(CustomUIPanelInputListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (isCheckButtonsEveryFrame) return;
        if (buttonPressHandler != null) {
            buttonPressHandler.buttonPressed(buttonId);
            return;
        }
        // note: doesn't get called if the button is inside a CustomPanelAPI without a plugin
        if (buttonsById.containsKey(buttonId)) {
            buttonsById.get(buttonId).checkButton();
        }
    }

    public static abstract class ButtonEntry {
        public ButtonAPI button;
        public boolean state;
        public String id;

        public ButtonEntry() {
        }

        public ButtonEntry(ButtonAPI button, String id) {
            this.button = button;
            this.id = id;
            state = button.isChecked();
        }

        public void setState(boolean state) {
            this.state = state;
            button.setChecked(state);
        }

        public void checkButton() {
            if (state != button.isChecked()) {
                state = button.isChecked();
                onToggle();
            }
            //Global.getLogger(this.getClass()).info("Button " + id + ": " + button.isChecked());
        }

        public abstract void onToggle();
    }

    public static class RadioButtonEntry extends ButtonEntry {

        public List<RadioButtonEntry> buttons;

        /**
         * Use the other constructor, the one that specifies the buttons.
         * @param button
         * @param id
         */
        @Deprecated
        public RadioButtonEntry(ButtonAPI button, String id) {
            super(button, id);
        }

        public RadioButtonEntry(ButtonAPI button, String id, List<RadioButtonEntry> buttons) {
            super(button, id);
            this.buttons = buttons;
        }

        @Override
        public void onToggle() {
            for (RadioButtonEntry entry : buttons) {
                if (entry != this) {
                    entry.setState(false);
                    //Global.getLogger(this.getClass()).info("Toggling other button " + entry.id);
                }
                else entry.setState(true);
            }
            onToggleImpl();
        }

        public void onToggleImpl() {

        }
    }
}

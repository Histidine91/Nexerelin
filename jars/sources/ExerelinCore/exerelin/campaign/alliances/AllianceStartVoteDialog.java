package exerelin.campaign.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.utilities.StringHelper;

import java.util.Map;

public class AllianceStartVoteDialog implements CustomDialogDelegate {

    protected InteractionDialogAPI dialog;
    protected InteractionDialogCustomPanelPlugin plugin = new InteractionDialogCustomPanelPlugin(0, false);
    protected Map<String, Integer> ships;
    protected FactionAPI faction;
    protected MemoryAPI mem;
    protected Map<String, MemoryAPI> memoryMap;
    protected CustomPanelAPI panel;
    protected CustomPanelAPI header;

    public AllianceStartVoteDialog(InteractionDialogAPI dialog, String factionId, Map<String, MemoryAPI> memoryMap) {
        this.dialog = dialog;
        faction = Global.getSector().getFaction(factionId);
        this.memoryMap = memoryMap;
        mem = memoryMap.get(MemKeys.LOCAL);
    }

    /*
        header
        top row: our allies and their votes for the current action
        body: list of factions we can war/peace with
     */

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {

    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return StringHelper.getString("confirm", true);
    }

    @Override
    public String getCancelText() {
        return StringHelper.getString("cancel", true);
    }

    @Override
    public void customDialogConfirm() {

    }

    @Override
    public void customDialogCancel() {

    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return plugin;
    }
}

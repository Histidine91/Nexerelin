package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import exerelin.SectorManager;

public class WelcomeDialogPlugin implements InteractionDialogPlugin
{
    private InteractionDialogAPI dialog;
    private TextPanelAPI textPanel;
    private OptionPanelAPI options;
    private VisualPanelAPI visual;

    private Integer currentStage = 0;

    private enum option
    {
        NEXT,
        LEAVE
    }

    public void init(InteractionDialogAPI dialog)
    {
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        this.visual = dialog.getVisualPanel();

        options.addOption("Next", option.NEXT);
        options.addOption("Leave", option.LEAVE);
        showText(0);
    }

    public void optionSelected(String text, Object optionData)
    {
        if((option)optionData == option.LEAVE)
        {
            Global.getSector().setPaused(false);
            dialog.dismiss();
        }
        else
        {
            currentStage++;
            showText(currentStage);
        }
    }

    private void showText(Integer stage)
    {
        if(stage == 0)
        {
            this.textPanel.addParagraph("Welcome to Sector Exerelin.");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("Your goal is to conquer this sector for the " + Global.getSector().getFaction(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getDisplayName() + " faction.");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("Your faction has started in " + SectorManager.getCurrentSectorManager().getFactionDirector(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getHomeSystem().getName());
            this.textPanel.addParagraph("");
        }
    }

    public void optionMousedOver(String optionText, Object optionData)
    {

    }

    public void advance(float amount)
    {

    }

    public Object getContext() {
        return null;
    }

    public void backFromEngagement(EngagementResultAPI battleResult)
    {

    }


}

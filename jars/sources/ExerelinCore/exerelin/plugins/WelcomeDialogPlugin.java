package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import exerelin.SectorManager;

import java.awt.*;

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
        BACK,
        LEAVE
    }

    public void init(InteractionDialogAPI dialog)
    {
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        this.visual = dialog.getVisualPanel();

        options.addOption("Next", option.NEXT);
        options.addOption("Back", option.BACK);
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
        else if ((option)optionData == option.NEXT)
        {
            currentStage++;
            showText(currentStage);
        }
        else if((option)optionData == option.BACK)
        {
            currentStage--;
            showText(currentStage);
        }
    }

    private void showText(Integer stage)
    {
        if(stage == 0)
        {
            options.setEnabled(option.BACK, false);
            this.textPanel.addParagraph("Welcome to the Exerelin Mod for StarSector.");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("Exerelin primarily adds a number of extra features to the current StarSector campaign. Exerelin is compatible with a large number of community made faction mods, which you can add for a more varied gameplay experience.");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("Factions within the sector will wage war, create alliances and compete to control a greater share of the sector.");
            this.textPanel.addParagraph("");
            if(!SectorManager.getCurrentSectorManager().isPlayerInPlayerFaction())
            {
                this.textPanel.addParagraph("Your goal is to conquer this sector for the " + Global.getSector().getFaction(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getDisplayName() + " faction.");
                this.textPanel.addParagraph("");
                this.textPanel.addParagraph("Your faction has started in " + SectorManager.getCurrentSectorManager().getFactionDirector(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getHomeSystem().getName() + ".");
                this.textPanel.addParagraph("");
            }
            else
            {
                this.textPanel.addParagraph("You have started unaligned from any faction and will need to raise your influence before you can trade and/or apply to join any factions.");
                this.textPanel.addParagraph("");
            }
            this.textPanel.addParagraph("");
        }

        if(stage == 1)
        {
            options.setEnabled(option.NEXT, true);
            options.setEnabled(option.BACK, true);
            this.textPanel.addParagraph("INFLUENCE:", Color.RED);
            this.textPanel.addParagraph("Your actions will gain/lose you influence with the factions in the Sector.");
            this.textPanel.addParagraph("The higher your influence with a faction, the greater access to their support and technology you will have.");
            this.textPanel.addParagraph("Although you can choose to go your own way, the more advanced features are only available while part of a faction.");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("");
        }

        if(stage == 2)
        {
            options.setEnabled(option.NEXT, true);
            options.setEnabled(option.BACK, true);
            this.textPanel.addParagraph("RESOURCES:", Color.RED);
            this.textPanel.addParagraph("Faction stations require fuel, crew, marines and supplies to build fleets.");
            this.textPanel.addParagraph("The greater the supply of resources, the larger fleets will be.");
            this.textPanel.addParagraph("Factions (and you) can mine supplies from asteriods and fuel from gas giants. Crew are more prevelant in stations orbiting a terran-like world.");
            this.textPanel.addParagraph("Mining fleets require mining drone wings (or equiavalent).");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("");
        }

        if(stage == 3)
        {
            options.setEnabled(option.NEXT, true);
            options.setEnabled(option.BACK, true);
            this.textPanel.addParagraph("STATIONS:", Color.RED);
            this.textPanel.addParagraph("A number of stations pre-exist within the sector. These stations can be won and lost be the factions.");
            this.textPanel.addParagraph("Stations can be conquered by boarding with marines to fight the defenders.");
            this.textPanel.addParagraph("Boarding fleets require a super-freighter (i.e. Atlas) and a troop transport (i.e. Valkyrie).");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("");
        }

        if(stage == 4)
        {
            options.setEnabled(option.NEXT, true);
            options.setEnabled(option.BACK, true);
            this.textPanel.addParagraph("ALLIANCES:", Color.RED);
            this.textPanel.addParagraph("Factions can combine into alliances. Once a faction is within an alliance, that alliance acts as a diplomatic bloc for all factions within that alliance.");
            this.textPanel.addParagraph("Factions will join/break alliances depending on their relationship with other factions.");
            this.textPanel.addParagraph("Once joined a faction, you can impact the relationship by exchanging high value prisoners and planting agents.");
            this.textPanel.addParagraph("");
            this.textPanel.addParagraph("");
        }

        if(stage == 5)
        {
            options.setEnabled(option.NEXT, false);
            this.textPanel.addParagraph("Most of all, have fun and please leave any feedback on the StarSector forums :)");
            this.textPanel.addParagraph("");
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

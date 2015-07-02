package exerelin.campaign;

import java.util.Map;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager.VictoryType;
import exerelin.utilities.StringHelper;
import java.util.HashMap;

// adapted from UpdateNotificationScript in LazyWizard's Version Checker
public class VictoryScreenScript implements EveryFrameScript
{
    private boolean isDone = false;
    private String faction = "player_npc";
    private VictoryType victoryType = VictoryType.CONQUEST;

    public VictoryScreenScript(String faction, VictoryType victoryType)
    {
        this.faction = faction;
        this.victoryType = victoryType;
    }

    @Override
    public boolean isDone()
    {
        return isDone;
    }

    @Override
    public boolean runWhilePaused()
    {
        return true;
    }

    
    @Override
    public void advance(float amount)
    {
        // Don't do anything while in a menu/dialog
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog())
        {
            return;
        }

        if (!isDone)
        {
            ui.showInteractionDialog(new VictoryDialog(faction, victoryType), Global.getSector().getPlayerFleet());
            Map<String, Object> params = new HashMap<>();
            boolean playerVictory = faction.equals(PlayerFactionStore.getPlayerFactionId());
            params.put("victorFactionId", faction);
            params.put("diplomaticVictory", victoryType == VictoryType.DIPLOMATIC);
            params.put("playerVictory", playerVictory);
            Global.getSector().getEventManager().startEvent(
                    new CampaignEventTarget(Global.getSector().getPlayerFleet()), "exerelin_victory", params);
            
            isDone = true;
        }
    }

    private static class VictoryDialog implements InteractionDialogPlugin
    {
        
        private InteractionDialogAPI dialog;
        private TextPanelAPI text;
        private OptionPanelAPI options;
        private final String faction;
        private final VictoryType victoryType;

        private enum Menu
        {
            CREDITS,
            STATS,
            EXIT
        }

        private VictoryDialog(String faction, VictoryType victoryType)
        {
            this.faction = faction;
            this.victoryType = victoryType;
        }
        
        private void printCreditLine(String name, String contribution)
        {
            text.addParagraph(name + ": " + contribution);
            text.highlightInLastPara(name);
        }
        
        private void printCredits()
        {
            printCreditLine("Zaphide", StringHelper.getString("exerelin_credits", "contribution_zaphide"));
            printCreditLine("Histidine", StringHelper.getString("exerelin_credits", "contribution_histidine"));
            printCreditLine("Dark.Revenant", StringHelper.getString("exerelin_credits", "contribution_darkRevenant"));
            printCreditLine("LazyWizard", StringHelper.getString("exerelin_credits", "contribution_lazyWizard"));
            printCreditLine("Psiyon", StringHelper.getString("exerelin_credits", "contribution_psiyon"));
            printCreditLine("Tartiflette", StringHelper.getString("exerelin_credits", "contribution_tartiflette"));
            printCreditLine("The SS mod community", StringHelper.getString("exerelin_credits", "contribution_ssModCommunity"));
            printCreditLine("Alex, David, Stian, Ivaylo", StringHelper.getString("exerelin_credits", "contribution_fractalSoftworks"));
        }

        @Override
        public void init(InteractionDialogAPI dialog)
        {
            this.dialog = dialog;
            this.options = dialog.getOptionPanel();
            this.text = dialog.getTextPanel();

            //dialog.setTextWidth(Display.getWidth() * .9f);
            
            String factionName = Global.getSector().getFaction(faction).getDisplayName();
            String theFactionName = Global.getSector().getFaction(faction).getDisplayNameWithArticle();
            String TheFactionName =  Misc.ucFirst(theFactionName);
            String firstStar = SectorManager.getFirstStarName();
            String message = "";
            if (victoryType == VictoryType.CONQUEST)
            {
                message = StringHelper.getString("exerelin_victoryScreen", "victoryConquest");
            }
            else if (victoryType == VictoryType.DIPLOMATIC)
            {
                message = StringHelper.getString("exerelin_victoryScreen", "victoryDiplomatic");
            }
            message = StringHelper.substituteToken(message, "$TheFactionName", theFactionName);
            text.addParagraph(message);
            text.highlightInLastPara(TheFactionName);
            text.addParagraph(StringHelper.getStringAndSubstituteToken("exerelin_victoryScreen", "youHaveWon", "$victoryType", victoryType.toString().toLowerCase()));
            text.highlightInLastPara(victoryType.toString().toLowerCase());
            
            dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/terran_orbit.jpg", 640, 400));
            Global.getSoundPlayer().playUISound("music_campaign_victory_theme", 1, 1);
            
            options.addOption(Misc.ucFirst(StringHelper.getString("credits")), Menu.CREDITS);
            options.addOption(Misc.ucFirst(StringHelper.getString("close")), Menu.EXIT);
            dialog.setPromptText(StringHelper.getString("exerelin_victoryScreen", "whatNow"));
        }

        @Override
        public void optionSelected(String optionText, Object optionData)
        {
            if (optionText != null) {
                    text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
            }

            // Option was a menu? Go to that menu
            if (optionData == Menu.CREDITS)
            {
                printCredits();
                //options.clearOptions();
                //options.addOption("Close", Menu.EXIT);
            }
            else if (optionData == Menu.EXIT)
            {
                dialog.dismiss();
            }
        }

        @Override
        public void optionMousedOver(String optionText, Object optionData)
        {
        }

        @Override
        public void advance(float amount)
        {
        }

        @Override
        public void backFromEngagement(EngagementResultAPI battleResult)
        {
        }

        @Override
        public Object getContext()
        {
            return null;
        }

        @Override
        public Map<String, MemoryAPI> getMemoryMap()
        {
            return null;
        }
    }
}

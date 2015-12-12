package exerelin.campaign;

import java.util.Map;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
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
            
            String victoryTypeStr = victoryType.toString().toLowerCase();
            boolean playerVictory = !victoryTypeStr.startsWith("defeat_");
            
            params.put("victorFactionId", faction);
            params.put("diplomaticVictory", victoryTypeStr.contains("diplomatic"));
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
        private final String factionId;
        private final VictoryType victoryType;

        private enum Menu
        {
            CREDITS,
            STATS,
            EXIT
        }

        private VictoryDialog(String factionId, VictoryType victoryType)
        {
            this.factionId = factionId;
            this.victoryType = victoryType;
        }
        
        protected String getString(String id)
        {
            return StringHelper.getString("exerelin_victoryScreen", id);
        }
        
        private void printCreditLine(String name, String contribution)
        {
            text.addParagraph(name + ": " + contribution);
            text.highlightInLastPara(name);
        }
               
        private void printKeyValueLine(String key, String value)
        {
            text.addParagraph(key + ": " + value);
            text.highlightInLastPara(value);
        }
        
        private void printCredits()
        {
            String category = "exerelin_credits";
            printCreditLine("Zaphide", StringHelper.getString(category, "contribution_zaphide"));
            printCreditLine("Histidine", StringHelper.getString(category, "contribution_histidine"));
            printCreditLine("Dark.Revenant", StringHelper.getString(category, "contribution_darkRevenant"));
            printCreditLine("LazyWizard", StringHelper.getString(category, "contribution_lazyWizard"));
            printCreditLine("Psiyon", StringHelper.getString(category, "contribution_psiyon"));
            printCreditLine("Tartiflette", StringHelper.getString(category, "contribution_tartiflette"));
            printCreditLine("The SS mod community", StringHelper.getString(category, "contribution_ssModCommunity"));
            printCreditLine("Alex, David, Stian, Ivaylo", StringHelper.getString(category, "contribution_fractalSoftworks"));
        }
        
        private void printStats()
        {
            StatsTracker tracker = StatsTracker.getStatsTracker();
            //CampaignClockAPI clock = Global.getSector().getClock();
            
            printKeyValueLine(getString("statsLevel"), Global.getSector().getPlayerPerson().getStats().getLevel()+"");
            //printKeyValueLine(getString("statsDaysElapsed"), Global.getSector().getClock().convertToDays(time)+"");
            printKeyValueLine(getString("statsFpKilled"), Misc.getWithDGS((int)tracker.getFpKilled()));
            printKeyValueLine(getString("statsFpLost"), Misc.getWithDGS((int)tracker.getFpLost()));
            printKeyValueLine(getString("statsOrphansMade"),  Misc.getWithDGS(tracker.getOrphansMade()));
            printKeyValueLine(getString("statsMarketsCaptured"), tracker.getMarketsCaptured()+"");
            printKeyValueLine(getString("statsAgentsUsed"), tracker.getAgentsUsed()+"");
            printKeyValueLine(getString("statsSaboteursUsed"), tracker.getSaboteursUsed()+"");
            printKeyValueLine(getString("statsPrisonersRepatriated"), tracker.getPrisonersRepatriated()+"");
            printKeyValueLine(getString("statsPrisonersRansomed"), tracker.getPrisonersRansomed()+"");
            printKeyValueLine(getString("statsSlavesSold"), tracker.getSlavesSold()+"");
        }

        @Override
        public void init(InteractionDialogAPI dialog)
        {
            this.dialog = dialog;
            this.options = dialog.getOptionPanel();
            this.text = dialog.getTextPanel();

            //dialog.setTextWidth(Display.getWidth() * .9f);
            
            FactionAPI faction = Global.getSector().getFaction(factionId);
            String factionName = faction.getDisplayName();
            String theFactionName = faction.getDisplayNameWithArticle();
            String TheFactionName =  Misc.ucFirst(theFactionName);
            String firstStar = SectorManager.getFirstStarName();
            String message = "";
            String victoryTypeStr = victoryType.toString().toLowerCase();
            
            if (victoryTypeStr.startsWith("defeat_"))
                message = getString(victoryTypeStr);
            else
                message = getString("victory_" + victoryTypeStr);
            message = StringHelper.substituteFactionTokens(message, faction);
            message = StringHelper.substituteToken(message, "$clusterName", firstStar);
            text.addParagraph(message);
            text.highlightInLastPara(Misc.getHighlightColor(), TheFactionName, theFactionName);
            
            if (victoryType != VictoryType.DEFEAT_CONQUEST && victoryType != VictoryType.DEFEAT_DIPLOMATIC)
            {
                victoryTypeStr = victoryTypeStr.replaceAll("_", " ");
                text.addParagraph(StringHelper.getStringAndSubstituteToken("exerelin_victoryScreen", "youHaveWon", "$victoryType", victoryTypeStr));
                text.highlightInLastPara(victoryTypeStr);
                dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/terran_orbit.jpg", 640, 400));
                Global.getSoundPlayer().playUISound("music_campaign_victory_theme", 1, 1);
            }
            else {
                dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/space_wreckage.jpg", 640, 400));
                Global.getSoundPlayer().playUISound("music_campaign_defeat_theme", 1, 1);
            }
            
            options.addOption(Misc.ucFirst(StringHelper.getString("stats")), Menu.STATS);
            options.addOption(Misc.ucFirst(StringHelper.getString("credits")), Menu.CREDITS);
            options.addOption(Misc.ucFirst(StringHelper.getString("close")), Menu.EXIT);
            dialog.setPromptText(getString("whatNow"));
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
            else if (optionData == Menu.STATS)
            {
                printStats();
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

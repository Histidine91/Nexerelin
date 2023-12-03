package exerelin.campaign.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.campaign.SectorManager.VictoryType;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.StatsTracker.DeadOfficerEntry;
import exerelin.campaign.intel.VictoryIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class VictoryScreenScript extends DelayedDialogScreenScript
{
	protected String faction = Factions.PLAYER;
	protected VictoryType victoryType = VictoryType.CONQUEST;
	protected CustomVictoryParams customparams;

	public VictoryScreenScript(String faction, VictoryType victoryType)
	{
		this.faction = faction;
		this.victoryType = victoryType;
	}
	
	@Override
	protected void showDialog() {
		VictoryDialog dialog = new VictoryDialog(faction, victoryType);
		dialog.customparams = customparams;
		Global.getSector().getCampaignUI().showInteractionDialog(dialog, null);

		boolean playerWon = !victoryType.isDefeat() && victoryType != VictoryType.RETIRED;
		if (customparams != null) playerWon = !customparams.isDefeat;
		VictoryIntel intel = new VictoryIntel(faction, victoryType, playerWon, customparams);
		Global.getSector().getIntelManager().addIntel(intel);
		intel.setImportant(true);
	}
	
	public void setCustomParams(CustomVictoryParams params) {
		customparams = params;
	}
	
	public static class CustomVictoryParams {
		public String id;
		public String name;
		public String factionId = Factions.PLAYER;
		public String text;
		public Highlights highlights;
		public String intelText;
		public String image;
		public String music;
		public boolean isDefeat;
		
		/**
		 * Creates params for a custom victory.
		 * @param id Unused.
		 * @param name
		 * @param factionId Winning faction ID (defaults to {@code player}).
		 * @param text Text shown in victory dialog.
		 * @param highlights {@code text} highlights
		 * @param intelText Text shown in intel item.
		 * @param image Optional, image to display in victory screen.
		 * @param music Optional, 'music' to play in victory screen (actually uses a UI sound).
		 * @param isDefeat
		 */
		public CustomVictoryParams(String id, String name, String factionId, String text, 
            Highlights highlights, String intelText, String image, String music, boolean isDefeat)
		{
			this.id = id;
			this.name = name;
			if (factionId != null)
				this.factionId = factionId;
			this.text = text;
			this.highlights = highlights;
			this.image = image;
			this.music = music;
			this.isDefeat = isDefeat;
		}
		
		public CustomVictoryParams() {
			
		}
	}

	public static boolean haveOfficerDeaths() {
		return NexConfig.officerDeaths || Global.getSettings().getModManager().isModEnabled("price_of_command")
				|| !StatsTracker.getStatsTracker().getDeadOfficers().isEmpty();
	}

	public static class VictoryDialog implements InteractionDialogPlugin
	{
		protected boolean officerDeaths = haveOfficerDeaths();
		
		protected InteractionDialogAPI dialog;
		protected TextPanelAPI text;
		protected OptionPanelAPI options;
		protected final String factionId;
		protected final VictoryType victoryType;
		protected CustomVictoryParams customparams;

		protected enum Menu
		{
			CREDITS,
			STATS,
			MEMORIAL,
			EXIT
		}

		protected VictoryDialog(String factionId, VictoryType victoryType)
		{
			this.factionId = factionId;
			this.victoryType = victoryType;
		}
		
		public static String getString(String id)
		{
			return StringHelper.getString("exerelin_victoryScreen", id);
		}
		
		protected void printCreditLine(String name, String contribution)
		{
			text.addParagraph(name + ": " + contribution);
			text.highlightInLastPara(name);
		}
			   
		protected void printKeyValueLine(String key, String value)
		{
			text.addParagraph(key + ": " + value);
			text.highlightInLastPara(value);
		}
		
		protected String getCreditsString(String id) {
			return StringHelper.getString("nex_credits", id);
		}
		
		protected void printCredits()
		{
			for (int i=1; i<=10; i++) {
				if (i==5) continue;
				printCreditLine(getCreditsString("name" + i), getCreditsString("contrib" + i));
			}
		}
		
		protected void printStats()
		{
			StatsTracker tracker = StatsTracker.getStatsTracker();
			//CampaignClockAPI clock = Global.getSector().getClock();
			
			printKeyValueLine(getString("statsLevel"), Global.getSector().getPlayerPerson().getStats().getLevel()+"");
			printKeyValueLine(getString("statsDaysElapsed"), (int) NexUtils.getTrueDaysSinceStart() + "");
			printKeyValueLine(getString("statsShipsKilled"), tracker.getShipsKilled() + "");
			printKeyValueLine(getString("statsShipsLost"), tracker.getShipsLost() + "");
			printKeyValueLine(getString("statsFpKilled"), Misc.getWithDGS((int)tracker.getFpKilled()));
			printKeyValueLine(getString("statsFpLost"), Misc.getWithDGS((int)tracker.getFpLost()));
			if (officerDeaths)
				printKeyValueLine(getString("statsOfficersLost"), tracker.getNumOfficersLost() + "");
			printKeyValueLine(getString("statsOrphansMade"),  Misc.getWithDGS(tracker.getOrphansMade()));
			printKeyValueLine(getString("statsMarketsCaptured"), tracker.getMarketsCaptured()+"");
			printKeyValueLine(getString("statsMarketsRaided"), tracker.getMarketsRaided()+"");
			printKeyValueLine(getString("statsTacticalBombardments"), tracker.getMarketsTacBombarded()+"");
			printKeyValueLine(getString("statsSaturationBombardments"), tracker.getMarketsSatBombarded()+"");
			//printKeyValueLine(getString("statsAgentsUsed"), tracker.getAgentsUsed()+"");
			//printKeyValueLine(getString("statsSaboteursUsed"), tracker.getSaboteursUsed()+"");
			printKeyValueLine(getString("statsPrisonersRepatriated"), tracker.getPrisonersRepatriated()+"");
			printKeyValueLine(getString("statsPrisonersRansomed"), tracker.getPrisonersRansomed()+"");
			//printKeyValueLine(getString("statsSlavesSold"), tracker.getSlavesSold()+"");
		}
		
		protected void printMemorial()
		{
			List<DeadOfficerEntry> deadOfficers = StatsTracker.getStatsTracker().getDeadOfficersSorted();
			if (deadOfficers.isEmpty())
			{
				text.addParagraph(StringHelper.getString("exerelin_officers", "noOfficersDead"));
				return;
			}
			
			text.setFontSmallInsignia();
			text.addParagraph(StringHelper.HR);
			Color col = Misc.getHighlightColor();
			for (DeadOfficerEntry dead : deadOfficers)
			{
				text.addParagraph(dead.officer.getPerson().getName().getFullName());

				String level = Misc.ucFirst(StringHelper.getString("level")) + " " + dead.officer.getPerson().getStats().getLevel();
				text.addParagraph("  " + level);
				text.highlightLastInLastPara(dead.officer.getPerson().getStats().getLevel() + "", col);

				String diedOn = StringHelper.getString("exerelin_officers", "diedOn");
				String diedDate = dead.getDeathDate();
				diedOn = StringHelper.substituteToken(diedOn, "$date", diedDate );
				text.addParagraph("  " + diedOn);
				text.highlightLastInLastPara(diedDate, col);

				String lastCommand = StringHelper.getString("exerelin_officers", "lastCommand");
				lastCommand = StringHelper.substituteToken(lastCommand, "$shipName", dead.shipName);
				lastCommand = StringHelper.substituteToken(lastCommand, "$shipClass", dead.shipClass);
				text.addParagraph("  " + lastCommand);
				text.highlightFirstInLastPara(dead.shipName, col);
				
				String cod = Misc.ucFirst(StringHelper.getString("exerelin_officers", "causeOfDeath")  + ": ");
				String cod2 = Misc.ucFirst(dead.causeOfDeath);
				cod += cod2;
				text.addParagraph("  " + cod);
				text.highlightLastInLastPara(cod2, col);
			}
			text.addParagraph(StringHelper.HR);
			text.setFontInsignia();
		}

		@Override
		public void init(InteractionDialogAPI dialog)
		{
			this.dialog = dialog;
			this.options = dialog.getOptionPanel();
			this.text = dialog.getTextPanel();

			//dialog.setTextWidth(Display.getWidth() * .9f);
			
			FactionAPI faction = Global.getSector().getFaction(factionId);
			String factionName = faction.getDisplayNameWithArticleWithoutArticle();
			//String firstStar = SectorManager.getFirstStarName();
			String message = "";
			String victoryTypeStr = victoryType.toString().toLowerCase();
			boolean isDefeat = victoryType.isDefeat() || (customparams != null && customparams.isDefeat);
			
			if (customparams != null) {
				text.addPara(customparams.text);
				if (customparams.highlights != null)
					text.setHighlightsInLastPara(customparams.highlights);
			}
			else {
				if (victoryTypeStr.startsWith("defeat_") || victoryType == VictoryType.RETIRED)
					message = getString(victoryTypeStr);
				else
					message = getString("victory_" + victoryTypeStr);
				message = StringHelper.substituteFactionTokens(message, faction);
				//message = StringHelper.substituteToken(message, "$clusterName", firstStar);
				text.addParagraph(message);
				text.highlightInLastPara(faction.getBaseUIColor(), factionName);
			}
			
			// image (and victory string)
			if (customparams != null)
			{
				if (customparams.image != null)
					dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(customparams.image, 640, 400));
				else
					dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(isDefeat ? 
							"graphics/illustrations/space_wreckage.jpg" : "graphics/illustrations/terran_orbit.jpg", 640, 400));
			}
			else if (victoryType == VictoryType.RETIRED)
			{
				dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/fly_away.jpg", 640, 400));
			}
			else if (!isDefeat)
			{
				victoryTypeStr = victoryTypeStr.replaceAll("_", " ");
				text.addParagraph(StringHelper.getStringAndSubstituteToken("exerelin_victoryScreen", "youHaveWon", "$victoryType", victoryTypeStr));
				text.highlightInLastPara(victoryTypeStr);
				dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/terran_orbit.jpg", 640, 400));
			}
			else {
				dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/space_wreckage.jpg", 640, 400));
			}
			
			// music
			if (customparams != null && customparams.music != null) {
				Global.getSoundPlayer().playUISound(customparams.music, 1, 1);
			}
			else if (victoryType == VictoryType.RETIRED) {
				// do nothing
			}
			else if (!isDefeat) {
				Global.getSoundPlayer().playUISound("music_campaign_victory_theme", 1, 1);
			}
			else {
				Global.getSoundPlayer().playUISound("music_campaign_defeat_theme", 1, 1);
			}
			
			options.addOption(Misc.ucFirst(StringHelper.getString("stats")), Menu.STATS);
			options.addOption(Misc.ucFirst(StringHelper.getString("credits")), Menu.CREDITS);
			if (officerDeaths)
				options.addOption(Misc.ucFirst(getString("officerMemorial")), Menu.MEMORIAL);
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
			else if (optionData == Menu.MEMORIAL)
			{
				printMemorial();
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
	
	// runcode exerelin.campaign.VictoryScreenScript.debug(num)
	public static void debug(int arg) {
		SectorManager man = SectorManager.getManager();
		String loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
				+ "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
				+ "Dolor sed viverra ipsum nunc aliquet bibendum enim. In massa tempor nec feugiat. "
				+ "Nunc aliquet bibendum enim facilisis gravida. Nisl nunc mi ipsum faucibus vitae "
				+ "aliquet nec ullamcorper. Amet luctus venenatis lectus magna fringilla. "
				+ "Volutpat maecenas volutpat blandit aliquam etiam erat velit scelerisque in. "
				+ "Egestas egestas fringilla phasellus faucibus scelerisque eleifend. "
				+ "Sagittis orci a scelerisque purus semper eget duis. Nulla pharetra diam sit amet nisl suscipit. "
				+ "Sed adipiscing diam donec adipiscing tristique risus nec feugiat in. Fusce ut placerat orci nulla. "
				+ "Pharetra vel turpis nunc eget lorem dolor. Tristique senectus et netus et malesuada.\n" 
				+ "\n"
				+ "Etiam tempor orci eu lobortis elementum nibh tellus molestie. Neque egestas congue quisque egestas. "
				+ "Egestas integer eget aliquet nibh praesent tristique. Vulputate mi sit amet mauris. "
				+ "Sodales neque sodales ut etiam sit. Dignissim suspendisse in est ante in. "
				+ "Volutpat commodo sed egestas egestas. Felis donec et odio pellentesque diam. "
				+ "Pharetra vel turpis nunc eget lorem dolor sed viverra. Porta nibh venenatis cras sed felis eget. "
				+ "Aliquam ultrices sagittis orci a. Dignissim diam quis enim lobortis. Aliquet porttitor lacus "
				+ "luctus accumsan. Dignissim convallis aenean et tortor at risus viverra adipiscing at.";
		String qbf = "The quick brown fox jumps over the lazy dog~";
		CustomVictoryParams params = new CustomVictoryParams();
		params.id = "bla";
		params.text = loremIpsum;
		params.intelText = qbf;
		params.name = "Custom Victory";
		Highlights hl = new Highlights();
		hl.setColors(Misc.getHighlightColor(), Misc.getNegativeHighlightColor(), Misc.getPositiveHighlightColor());
		hl.setText("Lorem ipsum", "Volutpat maecenas", "Etiam tempor");
		params.highlights = hl;
		
		switch (arg) {
			case 1:
				params.image = "graphics/illustrations/bombardment_saturation.jpg";
				params.factionId = Factions.INDEPENDENT;
				params.name = "Hax Victory";
				params.intelText = "The independents have won the game! LOL";
				break;
			case 2:
				params.image = "graphics/illustrations/raid_plunder.jpg";
				params.isDefeat = true;
				break;
			case 3:
				break;
			case 4:
				params.isDefeat = true;
				break;
			case 5:
				params.music = "ui_interdict_off";
				break;
		}
		man.customVictory(params);
	}
}

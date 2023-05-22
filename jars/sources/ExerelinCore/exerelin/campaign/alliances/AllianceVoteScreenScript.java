package exerelin.campaign.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.AllianceVoter.Vote;
import exerelin.campaign.alliances.AllianceVoter.VoteResult;
import exerelin.campaign.ui.DelayedDialogScreenScript;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.Map;

/**
 * Prompts player to do an alliance vote
 */
public class AllianceVoteScreenScript extends DelayedDialogScreenScript
{
	protected String factionId1, factionId2;
	protected boolean isWar;

	public AllianceVoteScreenScript(String factionId1, String factionId2, boolean isWar)
	{
		this.factionId1 = factionId1;
		this.factionId2 = factionId2;
		this.isWar = isWar;
	}

	@Override
	public boolean runWhilePaused()
	{
		return true;
	}

	@Override
	protected void showDialog() {
		Global.getSector().getCampaignUI().showInteractionDialog(
				new AllianceVoteDialog(factionId1, factionId2, isWar), null);
	}

	protected static class AllianceVoteDialog implements InteractionDialogPlugin, CoreInteractionListener
	{
		public static final String OPT_DEFY = "defy";

		protected InteractionDialogAPI dialog;
		protected TextPanelAPI text;
		protected OptionPanelAPI options;
		protected String factionId1, factionId2;
		protected boolean isWar;
		protected String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		protected boolean willDefy;
		
		public AllianceVoteDialog(String factionId1, String factionId2, boolean isWar) {
			super();
			this.factionId1 = factionId1;
			this.factionId2 = factionId2;
			this.isWar = isWar;
		}

		private void populateOptions()
		{
			options.clearOptions();
			
			String yes = Misc.ucFirst(StringHelper.getString("yes"));
			String no = Misc.ucFirst(StringHelper.getString("no"));
			String defy = getString("voteDefy") + ": " + StringHelper.getString(willDefy ? "enabled" : "disabled");
			options.addOption(yes, Vote.YES);
			options.addOption(no, Vote.NO);
			options.addOption(Misc.ucFirst(StringHelper.getString("abstain")), Vote.ABSTAIN);
			options.addOption(defy, OPT_DEFY);
			options.setTooltip(OPT_DEFY, getString("voteDefyTooltip"));
			options.addOption(StringHelper.getString("exerelin_misc", "intelScreen"), CoreUITabId.INTEL);
			
			for (Vote opt : Vote.values())
			{
				options.addOptionConfirmation(opt, getString("voteConfirm"), yes, no);
			}
		}
		
		protected String getString(String id)
		{
			return StringHelper.getString("exerelin_alliances", id);
		}
		
		protected String getFactionName(String factionId, boolean withArticle)
		{
			if (withArticle) return Global.getSector().getFaction(factionId).getDisplayNameWithArticle();
			return Global.getSector().getFaction(factionId).getDisplayName();
		}
		
		/**
		 * List the members of the specified faction's alliance (if any) and their market size sum.
		 * @param factionId
		 */
		protected void printAlliance(String factionId)
		{
			Color hlColor = Misc.getHighlightColor();
			
			Alliance ally = AllianceManager.getFactionAlliance(factionId);
			if (ally == null)	// just print info for the one faction
			{
				String str = getString("voteNoAllysLine1");
				str = StringHelper.substituteFactionTokens(str, factionId);
				
				text.addParagraph(str);
				Highlights hl = StringHelper.getFactionHighlights(factionId);
				Color facColor = Global.getSector().getFaction(factionId).getBaseUIColor();
				hl.setColors(facColor, facColor, facColor, facColor);
				text.setHighlightsInLastPara(hl);
				
				String size = NexUtilsFaction.getFactionMarketSizeSum(factionId) + "";
				str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", 
						"voteNoAllysLine2", "$factionSize", size);
				text.addParagraph("  " + str);
				text.highlightFirstInLastPara(size, hlColor);
				return;
			}
			String allySize = ally.getAllianceMarketSizeSum() + "";
			String str = getString("voteAllianceHeader");
			str = StringHelper.substituteToken(str, "$allianceName", ally.getName());
			str = StringHelper.substituteToken(str, "$allianceSize", allySize);
			
			text.addParagraph(str);
			text.highlightInLastPara(hlColor, ally.getName(), allySize);
			text.setFontSmallInsignia();
			text.addParagraph(StringHelper.HR);
			for (String memberId : ally.getMembersCopy())
			{
				String name = Misc.ucFirst(getFactionName(memberId, false));
				String size = NexUtilsFaction.getFactionMarketSizeSum(memberId) + "";
				text.addParagraph("  " + name + ": " + size);
				text.highlightInLastPara(name, size);
				text.setHighlightColorsInLastPara(Global.getSector().getFaction(memberId).getBaseUIColor(), hlColor);
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
			
			// strings + highlights, code is quite lengthy
			String fName1 = getFactionName(factionId1, false);
			String fName2 = getFactionName(factionId2, false);
			String myName = getFactionName(playerFactionId, true);
			
			String str = getString(isWar ? "voteIntroLineWar" : "voteIntroLinePeace");
			str = StringHelper.substituteToken(str, "$faction1", fName1);
			str = StringHelper.substituteToken(str, "$Faction1", Misc.ucFirst(fName1));
			str = StringHelper.substituteToken(str, "$faction2", fName2);
			str = StringHelper.substituteToken(str, "$Faction2", Misc.ucFirst(fName2));
			
			Color col1 = Global.getSector().getFaction(factionId1).getBaseUIColor();
			Color col2 = Global.getSector().getFaction(factionId2).getBaseUIColor();
			
			Highlights hl = new Highlights();
			String warOrPeace = isWar ? StringHelper.getString("war") : StringHelper.getString("peace");
			Color warOrPeaceColor = isWar? Color.RED : Color.CYAN;
			hl.setText(warOrPeace, Misc.ucFirst(warOrPeace),
					fName1, Misc.ucFirst(fName1), fName2, Misc.ucFirst(fName2));
			hl.setColors(warOrPeaceColor, warOrPeaceColor,
					col1, col1, col2, col2);
			
			text.addParagraph(str);
			text.setHighlightsInLastPara(hl);
			
			text.addParagraph("");
			printAlliance(factionId1);
			printAlliance(factionId2);
			text.addParagraph("");
			text.addParagraph(StringHelper.getStringAndSubstituteToken("exerelin_alliances", 
					"voteQuestionLine", "$myFaction", myName));
			
			populateOptions();
			dialog.setPromptText(Misc.ucFirst(StringHelper.getString("vote")));
		}

		@Override
		public void optionSelected(String optionText, Object optionData)
		{
			if (optionText != null) {
					text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
			}
			if (optionData.equals(CoreUITabId.INTEL)) {
				dialog.getVisualPanel().showCore(CoreUITabId.INTEL, Global.getSector().getPlayerFleet(), this);
				return;
			}
			if (optionData.equals(OPT_DEFY)) {
				willDefy = !willDefy;
				populateOptions();
				return;
			}
			
			Alliance alliance = AllianceManager.getFactionAlliance(playerFactionId);
			Global.getSector().getFaction(playerFactionId).getMemoryWithoutUpdate().
					set(AllianceVoter.VOTE_MEM_KEY, optionData, 15);
			Global.getSector().getFaction(playerFactionId).getMemoryWithoutUpdate().
					set(AllianceVoter.VOTE_DEFY_MEM_KEY, willDefy, 15);
			
			// allied with faction 1
			if (AllianceManager.areFactionsAllied(playerFactionId, factionId1))
			{
				VoteResult result = AllianceVoter.allianceVote(alliance, factionId1, factionId2, isWar);
				AllianceVoter.allianceVote(factionId1, factionId2, isWar, result, null);
			}
			// allied with faction 2
			else
			{
				VoteResult result = AllianceVoter.allianceVote(alliance, factionId2, factionId1, isWar);
				AllianceVoter.allianceVote(factionId1, factionId2, isWar, null, result);
			}
			dialog.dismiss();
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
		
		@Override
		public void coreUIDismissed() {
			
		}
	}
}

package exerelin.campaign.intel.colony;

import java.awt.Color;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ColonyManager;
import exerelin.utilities.StringHelper;

// mostly a copy of PEAvertInteractionDialogPluginImpl
public class ColonyAvertInteractionDialogPlugin implements InteractionDialogPlugin {

	private static enum OptionId {
		INIT,
		USE_CONNECTIONS,
		BRIBE,
		LEAVE,
		
		CONFIRM,
		CANCEL,
	}
	
	//public static float BRIBE_BASE = 0;
	public static int BRIBE_MULT = 100000;
	public static int BRIBE_MAX = 1000000;
	
	public static RepLevel MIN_REP = RepLevel.FAVORABLE;
	public static float REP_COST  = 0.1f;
	
	protected InteractionDialogAPI dialog;
	protected TextPanelAPI textPanel;
	protected OptionPanelAPI options;
	protected VisualPanelAPI visual;
	
	protected CampaignFleetAPI playerFleet;

	protected ColonyExpeditionIntel intel;
	protected IntelUIAPI ui;
	
	public ColonyAvertInteractionDialogPlugin(ColonyExpeditionIntel intel, IntelUIAPI ui) {
		this.intel = intel;
		this.ui = ui;
	}

	@Override
	public void init(InteractionDialogAPI dialog) {
		this.dialog = dialog;
		textPanel = dialog.getTextPanel();
		options = dialog.getOptionPanel();
		visual = dialog.getVisualPanel();

		playerFleet = Global.getSector().getPlayerFleet();
		
		visual.setVisualFade(0.25f, 0.25f);
		//visual.showImagePortion("illustrations", "quartermaster", 640, 400, 0, 0, 480, 300);
		visual.showPlanetInfo(intel.getTarget().getPrimaryEntity());
	
		dialog.setOptionOnEscape(StringHelper.getString("leave"), OptionId.LEAVE);
		
		optionSelected(null, OptionId.INIT);
	}
	
	@Override
	public Map<String, MemoryAPI> getMemoryMap() {
		return null;
	}
	
	@Override
	public void backFromEngagement(EngagementResultAPI result) {
		// no combat here, so this won't get called
	}
	
	protected int computeBribeAmount() {
		int numAverted = ColonyManager.getManager().getNumAvertedExpeditions();
		
		int bribe = (int) ((numAverted + 1) * BRIBE_MULT);
		if (bribe > BRIBE_MAX) bribe = BRIBE_MAX;
		return bribe;
	}
	
	protected void printOptionDesc(OptionId option) {
		Color tc = Misc.getTextColor();
		FactionAPI faction = intel.getFaction();
		String str;
		
		switch (option) {
		case BRIBE:
			int bribe = computeBribeAmount();
			str = StringHelper.getString("nex_avertDialog", "descBribe");
			str = StringHelper.substituteToken(str, "$theExpeditionName", intel.getActionNameWithArticle(), true);
			textPanel.addPara(str);
			
			int credits = (int) playerFleet.getCargo().getCredits().get();
			Color costColor = Misc.getHighlightColor();
			if (bribe > credits) costColor = Misc.getNegativeHighlightColor();
			
			str = StringHelper.getString("nex_avertDialog", "descBribe2");
			str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
			textPanel.addPara(str, costColor, Misc.getDGSCredits(bribe));
			
			textPanel.addPara(StringHelper.getString("nex_avertDialog", "descBribe3"), Misc.getHighlightColor(),
					Misc.getDGSCredits(credits));
			
			break;
		case USE_CONNECTIONS:
			boolean canUseConnections = faction.isAtWorst(Factions.PLAYER, MIN_REP);
			
			if (canUseConnections) {
				str = StringHelper.getString("nex_avertDialog", "descConnections");
				str = StringHelper.substituteToken(str, "$theExpeditionName", intel.getActionNameWithArticle(), true);
				textPanel.addPara(str);
			} else {
				str = StringHelper.getString("nex_avertDialog", "descConnectionsTooLow");
				str = StringHelper.substituteToken(str, "$faction", intel.getFaction().getPersonNamePrefix());
				textPanel.addPara(str);
				CoreReputationPlugin.addRequiredStanding(faction, MIN_REP, null, textPanel, null, tc, 0, true);
			}
			
			CoreReputationPlugin.addCurrentStanding(faction, null, textPanel, null, tc, 0f);
			
			if (canUseConnections) {
				str = StringHelper.getString("nex_avertDialog", "descConnections2");
				str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
				textPanel.addPara(str, Misc.getHighlightColor(), "" + (int) Math.round(REP_COST * 100f));
			}
			
			break;
		}
	}
	
	protected void addChoiceOptions() {
		options.clearOptions();
		
		options.addOption(StringHelper.getString("nex_avertDialog", "optionBribe"), OptionId.BRIBE, null);
		options.addOption(StringHelper.getString("nex_avertDialog", "optionConnections"), OptionId.USE_CONNECTIONS, null);
		
		options.addOption(StringHelper.getString("dismiss", true), OptionId.LEAVE, null);
		options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected void addDismissOption() {
		options.clearOptions();
		options.addOption("Dismiss", OptionId.LEAVE, null);
		options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected OptionId beingConfirmed = null;
	protected void addConfirmOptions() {
		if (beingConfirmed == null) return;
		
		options.clearOptions();
		
		printOptionDesc(beingConfirmed);
		
		options.addOption(StringHelper.getString("nex_avertDialog", "takeAction"), OptionId.CONFIRM, null);
		options.addOption(StringHelper.getString("neverMind", true), OptionId.CANCEL, null);
		options.setShortcut(OptionId.CANCEL, Keyboard.KEY_ESCAPE, false, false, false, true);

		if (beingConfirmed == OptionId.BRIBE) {
			int bribe = computeBribeAmount();
			if (bribe > playerFleet.getCargo().getCredits().get()) {
				options.setEnabled(OptionId.CONFIRM, false);
				options.setTooltip(OptionId.CONFIRM, StringHelper.getString("nex_avertDialog", "notEnoughCredits"));
			}
		} else if (beingConfirmed == OptionId.USE_CONNECTIONS) {
			FactionAPI faction = intel.getFaction();
			boolean canUseConnections = faction.isAtWorst(Factions.PLAYER, MIN_REP);
			if (!canUseConnections) {
				options.setEnabled(OptionId.CONFIRM, false);
				options.setTooltip(OptionId.CONFIRM, StringHelper.getString("nex_avertDialog", "notEnoughStanding"));
			}
		}
	}
	
	
	public void printInit() {
		TooltipMakerAPI info = textPanel.beginTooltip();
		info.setParaSmallInsignia();
		intel.addInitialDescSection(info, 0);
		textPanel.addTooltip();
		
		String str = StringHelper.getString("nex_avertDialog", "descStart");
		str = StringHelper.substituteToken(str, "$theExpeditionName", intel.getActionNameWithArticle(), true);
		textPanel.addPara(str);
	}
	
	public void optionSelected(String text, Object optionData) {
		if (optionData == null) return;
		
		OptionId option = (OptionId) optionData;
		
		if (text != null) {
			textPanel.addParagraph(text, Global.getSettings().getColor("buttonText"));
		}
		
		switch (option) {
		case INIT:
			printInit();
			addChoiceOptions();
			break;
		case BRIBE:
			beingConfirmed = OptionId.BRIBE;
			addConfirmOptions();
			break;
		case USE_CONNECTIONS:
			beingConfirmed = OptionId.USE_CONNECTIONS;
			addConfirmOptions();
			break;
		case CONFIRM:
			if (beingConfirmed == OptionId.BRIBE) {
				int bribe = computeBribeAmount();
				AddRemoveCommodity.addCreditsLossText(bribe, textPanel);
				playerFleet.getCargo().getCredits().subtract(bribe);
			} else if (beingConfirmed == OptionId.USE_CONNECTIONS) {
				CustomRepImpact impact = new CustomRepImpact();
				impact.delta = -REP_COST;
				ReputationAdjustmentResult repResult = Global.getSector().adjustPlayerReputation(
						new RepActionEnvelope(RepActions.CUSTOM, 
								impact, null, textPanel, false, true),
								intel.getFaction().getId());
			}
			intel.setColonyOutcome(ColonyExpeditionIntel.ColonyOutcome.AVERTED);
			intel.getOrganizeStage().abort();
			intel.forceFail(false);
			intel.sendUpdate(ColonyExpeditionIntel.OUTCOME_UPDATE, textPanel);
			addDismissOption();
			break;
		case CANCEL:
			addChoiceOptions();
			break;
		case LEAVE:
			leave();
			break;
		}
	}
	
	protected void leave() {
		dialog.dismiss();
		ui.updateUIForItem(intel);	
	}
	
	@Override
	public void optionMousedOver(String optionText, Object optionData) {

	}
	
	@Override
	public void advance(float amount) {
		
	}
	
	@Override
	public Object getContext() {
		return null;
	}
}
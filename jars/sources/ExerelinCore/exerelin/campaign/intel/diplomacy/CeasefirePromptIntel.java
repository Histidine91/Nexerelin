package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.ui.PopupDialogScript;
import exerelin.campaign.ui.PopupDialogScript.PopupDialog;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CeasefirePromptIntel extends TimedDiplomacyIntel implements PopupDialog,
		CoreInteractionListener {

	public static final Object DIALOG_OPT_ACCEPT = new Object();
	public static final Object DIALOG_OPT_REJECT = new Object();
	public static final Object DIALOG_OPT_OPEN = new Object();
	public static final Object DIALOG_OPT_CLOSE = new Object();

	protected boolean isPeaceTreaty;
	
	//runcode new exerelin.campaign.intel.diplomacy.CeasefirePromptIntel("luddic_church", false).init();
	
	public CeasefirePromptIntel(String factionId, boolean isPeaceTreaty)
	{
		super(MathUtils.getRandomNumberInRange(5, 7));
		this.factionId = factionId;
		this.isPeaceTreaty = isPeaceTreaty;
	}
	
	public void init() {
		this.setImportant(true);
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
		if (NexConfig.ceasefireNotificationPopup)
			Global.getSector().addScript(new PopupDialogScript(this));
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
	}

	@Override
	public void createGeneralDescription(TooltipMakerAPI info, float width, float opad) {
		Color h = Misc.getHighlightColor();

		FactionAPI faction = Global.getSector().getFaction(factionId);
		FactionAPI faction2 = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());

		info.addImages(width, 96, opad, opad, faction.getLogo(), faction2.getLogo());

		Map<String, String> replace = new HashMap<>();

		String factionName = faction.getDisplayNameWithArticle();
		String days = Math.round(DiplomacyBrain.CEASEFIRE_LENGTH) + "";
		String cfOrPt = isPeaceTreaty ? StringHelper.getString("peaceTreaty")
				: StringHelper.getString("ceasefire");
		replace.put("$days", days);
		replace.put("$ceasefireOrPeaceTreaty", cfOrPt);
		replace.put("$theFaction", factionName);
		replace.put("$TheFaction", Misc.ucFirst(factionName));
		replace.put("$isOrAre", faction.getDisplayNameIsOrAre());
		//StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);

		String str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", "intelCeasefireDesc", replace);
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(),
				cfOrPt, days);
		label.setHighlightColors(faction.getBaseUIColor(), h, h);
	}

	@Override
	public void createOutcomeDescription(TooltipMakerAPI info, float width, float opad) {
		FactionAPI faction = Global.getSector().getFaction(factionId);

		boolean accepted = state == 1;
		String acceptOrReject = accepted ? StringHelper.getString("accepted") : StringHelper.getString("rejected");
		Color hl = accepted ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		String str = StringHelper.getString("exerelin_diplomacy", "intelCeasefireDescResult");
		str = StringHelper.substituteToken(str, "$acceptedOrRejected", acceptOrReject);
		info.addPara(str, opad, hl, acceptOrReject);

		if (repResult != null) {
			// display relationship change from event, and relationship following event
			Color deltaColor = repResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
			String delta = (int)Math.abs(repResult.delta * 100) + "";
			String newRel = NexUtilsReputation.getRelationStr(storedRelation);
			String fn = NexUtilsFaction.getFactionShortName(factionId);
			str = StringHelper.getString("exerelin_diplomacy", "intelRepResultPositivePlayer");
			str = StringHelper.substituteToken(str, "$faction", fn);
			str = StringHelper.substituteToken(str, "$deltaAbs", delta);
			str = StringHelper.substituteToken(str, "$newRelationStr", newRel);

			LabelAPI para = info.addPara(str, opad);
			para.setHighlight(fn, delta, newRel);
			para.setHighlightColors(faction.getBaseUIColor(),
					deltaColor, NexUtilsReputation.getRelColor(storedRelation));

			// days ago
			info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
		}
	}
	
	@Override
	public void acceptImpl() {
		String eventId = isPeaceTreaty ? "peace_treaty" : "ceasefire";
		float reduction = isPeaceTreaty ? NexConfig.warWearinessPeaceTreatyReduction : NexConfig.warWearinessCeasefireReduction;

		FactionAPI faction = getFactionForUIColors();
		FactionAPI player = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());

		DiplomacyIntel intel = DiplomacyManager.createDiplomacyEventV2(faction, player, eventId, null);
		if (intel != null) repResult = intel.reputation;
		else repResult = new ExerelinReputationAdjustmentResult(0);
		DiplomacyManager.getManager().modifyWarWeariness(factionId, -reduction);
		DiplomacyManager.getManager().modifyWarWeariness(PlayerFactionStore.getPlayerFactionId(), -reduction);
		storedRelation = faction.getRelationship(PlayerFactionStore.getPlayerFactionId());
		Global.getSoundPlayer().playUISound("ui_rep_raise", 1, 1);
	}

	@Override
	protected void rejectImpl() {}

	@Override
	public void onExpire() {
		if (NexConfig.acceptCeasefiresOnTimeout)
			accept();
		else
			setState(-1);
	}

	@Override
	protected void advanceImpl(float amount) {
		if (this.isEnding() || this.isEnded())
			return;
		
		// auto-accept if already non-hostile
		if (!Global.getSector().getPlayerFaction().isHostileTo(factionId)) {
			accept();
			endAfterDelay();
			return;
		}
		
		super.advanceImpl(amount);
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName(false);
	}

	protected String getName() {
		return getName(true);
	}
	
	public String getName(boolean withStatus) {
		String str = StringHelper.getString("exerelin_diplomacy", isPeaceTreaty ? 
				"intelPeaceTreatyTitle" : "intelCeasefireTitle");
		if (!withStatus) return str;

		if (listInfoParam == EXPIRED_UPDATE)
			str += " - " + StringHelper.getString("expired");
		else if (state == 1)
			str += " - " + StringHelper.getString("accepted");
		else if (state == -1)
			str += " - " + StringHelper.getString("rejected");
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString(Tags.INTEL_AGREEMENTS, true));
		tags.add(StringHelper.getString("diplomacy", true));
		tags.add(factionId);
		tags.add(PlayerFactionStore.getPlayerFactionId());
		return tags;
	}
	
	@Override
	public String getCommMessageSound() {
		return "nex_ui_ceasefire_prompt";
	}
		
	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("diplomacy", true);
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(factionId);
	}

	@Override
	public void populateOptions(OptionPanelAPI opts) {
		opts.addOption(StringHelper.getString("exerelin_diplomacy", "dialogCeasefireOptionGotoIntel"), DIALOG_OPT_OPEN);
		opts.addOption(StringHelper.getString("accept", true), DIALOG_OPT_ACCEPT);
		opts.addOption(StringHelper.getString("reject", true), DIALOG_OPT_REJECT);
		opts.addOption(StringHelper.getString("close", true), DIALOG_OPT_CLOSE);
		opts.setShortcut(DIALOG_OPT_OPEN, Keyboard.KEY_RETURN, false, false, false, false);
		opts.setShortcut(DIALOG_OPT_CLOSE, Keyboard.KEY_ESCAPE, false, false, false, false);
	}

	@Override
	public void init(InteractionDialogAPI dialog) {
		FactionAPI faction = getFactionForUIColors();
		TextPanelAPI text = dialog.getTextPanel();
		
		text.addPara(StringHelper.getString("exerelin_diplomacy", "dialogCeasefireTitle"), 
				Misc.getHighlightColor());
		
		Map<String, String> replace = new HashMap<>();
		
		String factionName = faction.getDisplayNameWithArticle();
		String days = Math.round(DiplomacyBrain.CEASEFIRE_LENGTH) + "";
		String cfOrPt = isPeaceTreaty ? StringHelper.getString("peaceTreaty") 
				: StringHelper.getString("ceasefire");
		days = Math.round(daysRemaining) + "";
		String daysStr = getDaysString(daysRemaining);
		replace.put("$timeLeft", days);
		replace.put("$days", daysStr);
		replace.put("$ceasefireOrPeaceTreaty", cfOrPt);
		replace.put("$theFaction", factionName);
		replace.put("$TheFaction", Misc.ucFirst(factionName));
		
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", "dialogCeasefireText", replace);
		LabelAPI label = text.addPara(str);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), 
				cfOrPt, days);
		label.setHighlightColors(faction.getBaseUIColor(), Misc.getHighlightColor(), 
				Misc.getHighlightColor());
		
		text.setFontSmallInsignia();
		str = StringHelper.getString("exerelin_diplomacy", "dialogCeasefireText2");
		dialog.getTextPanel().addPara(str, Color.CYAN, NexConfig.CONFIG_PATH);
		text.setFontInsignia();
	}

	@Override
	public void optionSelected(InteractionDialogAPI dialog, Object optionData) {
		if (optionData == DIALOG_OPT_OPEN) {
			dialog.getVisualPanel().showCore(CoreUITabId.INTEL, null, this, this);
			return;
		}
		else if (optionData == DIALOG_OPT_CLOSE) {
			dialog.dismiss();
		}
		
		else if (optionData == DIALOG_OPT_ACCEPT) {
			this.accept();
			endAfterDelay();
		}
		else if (optionData == DIALOG_OPT_REJECT) {
			state = -1;
			endAfterDelay();
		}
		dialog.dismiss();
	}

	@Override
	public void coreUIDismissed() {
		Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
	}
	
	// runcode new exerelin.campaign.intel.diplomacy.CeasefirePromptIntel("pirates", false).init()

	@Override
	public boolean shouldCancel() {
		return state != 0;
	}

	@Override
	public String getStrategicActionName() {
		return getName(false);
	}
}

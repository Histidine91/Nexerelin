package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

public class CeasefirePromptIntel extends BaseIntelPlugin {
	
	public static final Object EXPIRED_UPDATE = new Object();
	public static final String BUTTON_ACCEPT = "Accept";
	public static final String BUTTON_REJECT = "Reject";
	
	protected String factionId;
	protected boolean isPeaceTreaty;
	protected int state = 0;	// 0 = pending, 1 = accepted, -1 = rejected
	protected float daysRemaining = MathUtils.getRandomNumberInRange(14, 21);
	protected ExerelinReputationAdjustmentResult repResult;
	protected float storedRelation;
	
	public CeasefirePromptIntel(String factionId, boolean isPeaceTreaty)
	{
		this.factionId = factionId;
		this.isPeaceTreaty = isPeaceTreaty;
	}
	
	public void init() {
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
	}
	
	// bullet points
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = state == 0 ? Misc.getBasePlayerColor() : Misc.getGrayColor();
		info.addPara(getName(), c, 0f);
		bullet(info);

		float initPad = 3f, pad = 0;
		Color tc = getBulletColorForMode(mode);
		addFactionNamePara(info, initPad, tc, getFactionForUIColors());
	}
	
	// text sidebar
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		FactionAPI faction2 = Global.getSector().getFaction(Factions.PLAYER);
		
		info.addImages(width, 96, opad, opad, faction.getLogo(), faction2.getLogo());
		
		Map<String, String> replace = new HashMap<>();
		
		String factionName = faction.getDisplayNameWithArticle();
		String days = Math.round(DiplomacyBrain.CEASEFIRE_LENGTH) + "";
		String cfOrPt = isPeaceTreaty ? "peace treaty" : "ceasefire";
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
		label.setHighlightColors(faction.getBaseUIColor(), Misc.getHighlightColor(), 
				Misc.getHighlightColor());
		
		if (state == 0) {
			replace.clear();
			days = Math.round(daysRemaining) + "";
			String daysStr = getDaysString(daysRemaining);
			replace.put("$timeLeft", days);
			replace.put("$days", daysStr);
			str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", "intelCeasefireDescTime", replace);
			info.addPara(str, opad, Misc.getHighlightColor(), days);

			ButtonAPI button = info.addButton(StringHelper.getString("accept", true), BUTTON_ACCEPT, 
							getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
						  (int)(width), 20f, opad * 3f);
			ButtonAPI button2 = info.addButton(StringHelper.getString("reject", true), BUTTON_REJECT, 
							getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
						  (int)(width), 20f, opad);
		} else {
			info.addSectionHeading(StringHelper.getString("result", true), Alignment.MID, opad);
			boolean accepted = state == 1;
			String acceptOrReject = accepted ? StringHelper.getString("accepted") : StringHelper.getString("rejected");
			Color hl = accepted ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
			str = StringHelper.getString("exerelin_diplomacy", "intelCeasefireDescResult");
			str = StringHelper.substituteToken(str, "$acceptedOrRejected", acceptOrReject);
			info.addPara(str, opad, hl, acceptOrReject);
			
			if (repResult != null) {
				// display relationship change from event, and relationship following event
				Color deltaColor = repResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
				String delta = (int)Math.abs(repResult.delta * 100) + "";
				String newRel = NexUtilsReputation.getRelationStr(storedRelation);
				String fn = ExerelinUtilsFaction.getFactionShortName(factionId);
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
	}
	
	protected static void addFactionNamePara(TooltipMakerAPI info, float pad, Color color, FactionAPI faction) {
		String name = faction.getDisplayName();
		info.addPara(name, pad, color, faction.getBaseUIColor(), name);
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		prompt.addPara(StringHelper.getString("exerelin_diplomacy", "intelCeasefireConfirm"), 0);
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return true;
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_ACCEPT) {
			String eventId = isPeaceTreaty ? "peace_treaty" : "ceasefire";
			float reduction = isPeaceTreaty ? ExerelinConfig.warWearinessPeaceTreatyReduction : ExerelinConfig.warWearinessCeasefireReduction;
			
			FactionAPI faction = getFactionForUIColors();
			FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);
			
			repResult = DiplomacyManager.createDiplomacyEvent(faction, player, eventId, null);
			DiplomacyManager.getManager().modifyWarWeariness(factionId, -reduction);
			DiplomacyManager.getManager().modifyWarWeariness(Factions.PLAYER, -reduction);
			storedRelation = faction.getRelationship(Factions.PLAYER);
			Global.getSoundPlayer().playUISound("ui_rep_raise", 1, 1);
			state = 1;
		}
		else if (buttonId == BUTTON_REJECT) {
			state = -1;
		}
		
		endAfterDelay();
		super.buttonPressConfirmed(buttonId, ui);
	}
	
	@Override
	protected void advanceImpl(float amount) {
		if (this.isEnding() || this.isEnded())
			return;
		
		daysRemaining -= Global.getSector().getClock().convertToDays(amount);
		
		if (daysRemaining <= 0) {
			state = -1;
			sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
			endAfterDelay();
		}
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String str = StringHelper.getString("exerelin_diplomacy", isPeaceTreaty ? 
				"intelPeaceTreatyTitle" : "intelCeasefireTitle");
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
		tags.add(StringHelper.getString("diplomacy", true));
		tags.add(factionId);
		tags.add(Factions.PLAYER);
		return tags;
	}
		
	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public String getSortString() {
		return "Diplomacy";
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(factionId);
	}
}
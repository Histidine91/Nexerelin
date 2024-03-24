package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// largely a copypaste of CeasefirePromptIntel
public class AllianceOfferIntel extends BaseIntelPlugin implements StrategicActionDelegate {

	public static final Object EXPIRED_UPDATE = new Object();
	public static final String BUTTON_ACCEPT = "Accept";
	public static final String BUTTON_REJECT = "Reject";
	public static final float COOLDOWN = 150;
	public static final String MEM_KEY_COOLDOWN = "$nex_allianceOffer_cooldown";

	protected String factionId;
	protected int state = 0;	// 0 = pending, 1 = accepted, -1 = rejected
	protected float daysRemaining = MathUtils.getRandomNumberInRange(20, 30);
	@Getter	@Setter	protected StrategicAction strategicAction;
	@Nullable @Getter protected Alliance alliance;

	//runcode new exerelin.campaign.intel.diplomacy.AllianceOfferIntel("luddic_church", null).init();
	public AllianceOfferIntel(String offeringFactionId, @Nullable Alliance alliance)
	{
		this.factionId = offeringFactionId;
		this.alliance = alliance;
	}
	
	public void init() {
		this.setImportant(true);
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);

		PlayerFactionStore.getPlayerFaction().getMemoryWithoutUpdate().set(MEM_KEY_COOLDOWN, true, COOLDOWN);
		Global.getSector().getFaction(factionId).getMemoryWithoutUpdate().set(MEM_KEY_COOLDOWN, true, COOLDOWN);	// just in case, my own code confuses me
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
	}
	
	@Override
	public Color getTitleColor(ListInfoMode mode) {
		return state == 0 ? Misc.getBasePlayerColor() : Misc.getGrayColor();
	}
	
	// text sidebar
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		FactionAPI faction2 = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		
		info.addImages(width, 96, opad, opad, faction.getLogo(), faction2.getLogo());
		
		Map<String, String> replace = new HashMap<>();
		
		String factionName = faction.getDisplayNameWithArticle();
		String days = Math.round(DiplomacyBrain.CEASEFIRE_LENGTH) + "";
		//replace.put("$days", days);
		replace.put("$theFaction", factionName);
		replace.put("$TheFaction", Misc.ucFirst(factionName));
		replace.put("$isOrAre", faction.getDisplayNameIsOrAre());
		//StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);

		// join existing alliance, or form new alliance? apply descriptions for each case
		String strId = "intelAllianceDescNew";
		if (AllianceManager.getFactionAlliance(factionId) != null) {
			strId = "intelAllianceDescInvite";
		} else if (AllianceManager.getFactionAlliance(PlayerFactionStore.getPlayerFactionId()) != null) {
			strId = "intelAllianceDescJoin";
		}
		if (alliance != null) {
			replace.put("$alliance", alliance.getName());
		}
		
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", strId, replace);
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), alliance != null ? alliance.getName() : "");
		label.setHighlightColors(faction.getBaseUIColor(), h);
		
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
			info.addSectionHeading(StringHelper.getString("result", true), getFactionForUIColors().getBaseUIColor(), 
					getFactionForUIColors().getDarkUIColor(), Alignment.MID, opad);
			boolean accepted = state == 1;
			String acceptOrReject = accepted ? StringHelper.getString("accepted") : StringHelper.getString("rejected");
			Color hl = accepted ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
			str = StringHelper.getString("exerelin_diplomacy", "intelCeasefireDescResult");
			str = StringHelper.substituteToken(str, "$acceptedOrRejected", acceptOrReject);
			info.addPara(str, opad, hl, acceptOrReject);
		}
	}
	
	public void accept() {
		if (state == 1) return;

		alliance = AllianceManager.createAlliance(factionId, PlayerFactionStore.getPlayerFactionId());
		state = 1;
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
			accept();
		}
		else if (buttonId == BUTTON_REJECT) {
			state = -1;
		}

		endAfterDelay();
		super.buttonPressConfirmed(buttonId, ui);
	}
	
	@Override
	protected void advanceImpl(float amount) {
		if (isEnding() || isEnded())
			return;
		
		if (!SectorManager.isFactionAlive(factionId)) {
			state =-1;
			sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
			endAfterDelay();
			return;
		}

		// auto-reject if relations become too poor
		if (Global.getSector().getPlayerFaction().isAtBest(factionId, RepLevel.NEUTRAL)) {
			state = -1;
			endAfterDelay();
			return;
		}
		
		daysRemaining -= Global.getSector().getClock().convertToDays(amount);
		
		if (daysRemaining <= 0) {
			state = -1;
			sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
			endAfterDelay();
		}
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName(false);
	}

	protected String getName() {
		return getName(true);
	}
	
	public String getName(boolean withStatus) {
		String str = StringHelper.getString("exerelin_diplomacy", "intelAllianceTitle");
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
		tags.add(StringHelper.getString("diplomacy", true));
		tags.add(StringHelper.getString("alliances", true));
		tags.add(factionId);
		tags.add(PlayerFactionStore.getPlayerFactionId());
		return tags;
	}

	@Override
	public String getCommMessageSound() {
		//return "nex_ui_ceasefire_prompt";
		return super.getCommMessageSound();
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
	public ActionStatus getStrategicActionStatus() {
		switch (state) {
			case 0:
				return ActionStatus.IN_PROGRESS;
			case 1:
				return ActionStatus.SUCCESS;
			case -1:
				return ActionStatus.FAILURE;
			default:
				return ActionStatus.CANCELLED;
		}
	}

	@Override
	public float getStrategicActionDaysRemaining() {
		return daysRemaining;
	}

	@Override
	public void abortStrategicAction() {
		state =-1;
		sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
		endAfterDelay();
	}

	@Override
	public String getStrategicActionName() {
		return getName(false);
	}
}

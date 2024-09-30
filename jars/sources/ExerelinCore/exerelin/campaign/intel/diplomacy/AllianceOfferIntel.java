package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AllianceOfferIntel extends TimedDiplomacyIntel {

	public static final float COOLDOWN = 60;	// tripled if player rejects offer or it expires
	public static final String MEM_KEY_COOLDOWN = "$nex_allianceOffer_cooldown";

	@Nullable @Getter protected Alliance alliance;
	@Nullable @Getter protected Alliance alliance2;
	protected boolean applyExtendedCooldown;

	//runcode new exerelin.campaign.intel.diplomacy.AllianceOfferIntel("hegemony", null, null).init();
	public AllianceOfferIntel(String offeringFactionId, @Nullable Alliance alliance, @Nullable Alliance alliance2)
	{
		super(MathUtils.getRandomNumberInRange(20, 30));
		this.factionId = offeringFactionId;
		this.alliance = alliance;
		this.alliance2 = alliance2;
	}
	
	public void init() {
		this.setImportant(true);
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);

		saveCooldown(COOLDOWN);
	}

	public void saveCooldown(float duration) {
		Set<String> factionsToApply = new HashSet<>();
		factionsToApply.add(PlayerFactionStore.getPlayerFactionId());
		factionsToApply.add(factionId);

		Alliance all = AllianceManager.getFactionAlliance(factionId);
		if (all != null) factionsToApply.addAll(all.getMembersCopy());

		for (String f : factionsToApply) {
			Global.getSector().getFaction(f).getMemoryWithoutUpdate().set(MEM_KEY_COOLDOWN, true, duration);
		}
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		if (alliance2 != null) {
			alliance.getIntel().printFactionList(info, alliance.getMembersSorted(), alliance.getName(), initPad);
			alliance2.getIntel().printFactionList(info, alliance2.getMembersSorted(), alliance2.getName(), 0);
		} else if (alliance != null) {
			if (!alliance.getMembersCopy().contains(factionId)) {
				NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
				initPad = 0;
			}
			alliance.getIntel().printFactionList(info, alliance.getMembersSorted(), alliance.getName(), initPad);
		} else {
			NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
		}
	}

	@Override
	public void createGeneralDescription(TooltipMakerAPI info, float width, float opad) {
		Color h = Misc.getHighlightColor();

		FactionAPI faction = Global.getSector().getFaction(factionId);
		FactionAPI faction2 = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());

		info.addImages(width, 96, opad, opad, faction.getLogo(), faction2.getLogo());

		Map<String, String> replace = new HashMap<>();

		String factionName = faction.getDisplayNameWithArticle();
		//replace.put("$days", days);
		replace.put("$theFaction", factionName);
		replace.put("$TheFaction", Misc.ucFirst(factionName));
		replace.put("$isOrAre", faction.getDisplayNameIsOrAre());
		//StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);

		// merge alliances, join existing alliance, or form new alliance? apply descriptions for each case
		String strId = "intelAllianceDescNew";
		if (alliance != null && alliance2 != null) {
			strId = "intelAllianceDescMerge";
		} else if (alliance != null) {
			String player = PlayerFactionStore.getPlayerFactionId();
			if (alliance.getMembersCopy().contains(player)) {
				strId = "intelAllianceDescJoin";
			} else {
				strId = "intelAllianceDescInvite";
			}
		}
		if (alliance != null) {
			replace.put("$alliance", alliance.getName());
		}
		if (alliance2 != null) {
			replace.put("$otherAlliance", alliance2.getName());
		}

		String str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", strId, replace);
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), alliance != null ? alliance.getName() : "", alliance2 != null ? alliance2.getName() : "");
		label.setHighlightColors(faction.getBaseUIColor(), h);
	}

	@Override
	public void createPendingDescription(TooltipMakerAPI info, float width, float opad) {
		Map<String, String> replace = new HashMap<>();
		String days = Math.round(daysRemaining) + "";
		String daysStr = getDaysString(daysRemaining);
		replace.put("$timeLeft", days);
		replace.put("$days", daysStr);
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", "intelCeasefireDescTime", replace);
		info.addPara(str, opad, Misc.getHighlightColor(), days);

		ButtonAPI button = info.addButton(StringHelper.getString("accept", true), BUTTON_ACCEPT,
				getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
				(int)(width), 20f, opad * 2f);
		ButtonAPI button2 = info.addButton(StringHelper.getString("reject", true), BUTTON_REJECT,
				getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
				(int)(width), 20f, opad);
	}

	@Override
	public void createOutcomeDescription(TooltipMakerAPI info, float width, float opad) {
		boolean accepted = state == 1;
		String acceptOrReject = accepted ? StringHelper.getString("accepted") : StringHelper.getString("rejected");
		Color hl = accepted ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		String str = StringHelper.getString("exerelin_diplomacy", "intelCeasefireDescResult");
		str = StringHelper.substituteToken(str, "$acceptedOrRejected", acceptOrReject);
		info.addPara(str, opad, hl, acceptOrReject);
	}
	
	@Override
	public void acceptImpl() {
		if (alliance2 != null) AllianceManager.getManager().mergeAlliance(alliance, alliance2);
		else {
			// don't try to remove player from existing alliance before joining new one, it'll break the "other faction joins us" case
			alliance = AllianceManager.createAlliance(factionId, PlayerFactionStore.getPlayerFactionId());
		}
	}

	public void rejectImpl() {
		if (applyExtendedCooldown) {
			saveCooldown(COOLDOWN * 3);
		}
	}

	@Override
	public void onExpire() {
		applyExtendedCooldown = true;
		reject();
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_REJECT) {
			applyExtendedCooldown = true;
		}

		super.buttonPressConfirmed(buttonId, ui);
	}
	
	@Override
	protected void advanceImpl(float amount) {
		if (isEnding() || isEnded())
			return;

		// auto-reject if relations become too poor to form new alliance
		boolean bad = alliance == null && alliance2 == null && Global.getSector().getPlayerFaction().isAtBest(factionId, RepLevel.NEUTRAL);
		if (bad) {
			reject();
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
		tags.add(Tags.INTEL_AGREEMENTS);
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
	public String getStrategicActionName() {
		return getName(false);
	}
}

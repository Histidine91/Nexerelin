package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.econ.TributeCondition;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TributeIntel extends BaseIntelPlugin {
	
	public static final Object EXPIRED_UPDATE = new Object();
	public static final Object CANCELLED_UPDATE = new Object();
	public static final String BUTTON_ACCEPT = "Accept";
	public static final String BUTTON_REJECT = "Reject";
	public static final String BUTTON_CANCEL = "Cancel";
	public static final String REJECTION_FACTION_MEM_KEY = "$nex_tributeRefusedFaction";
	public static final float REJECT_REP_PENALTY = 0.05f;
	
	public enum TributeStatus {
		PENDING, ACTIVE, REJECTED, CANCELLED;
		
		public boolean isOver() {
			return this == TributeStatus.REJECTED || this == TributeStatus.CANCELLED;
		}
	}
	
	protected String factionId;
	protected MarketAPI market;
	protected TributeStatus status = TributeStatus.PENDING;
	protected MarketConditionAPI cond;
	//protected int state = 0;	// 0 = pending, 1 = accepted, -1 = rejected
	protected float daysRemaining = MathUtils.getRandomNumberInRange(14, 21);
	protected ExerelinReputationAdjustmentResult repResult;
	protected float storedRelation;
	protected Long cancelTime;
	
	public TributeIntel(String factionId, MarketAPI market)
	{
		this.factionId = factionId;
		this.market = market;
	}
	
	public void init() {
		this.setImportant(true);
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		float pad = 0;
		NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
		info.addPara(market.getName(), tc, pad);
	}
	
	// text sidebar
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		Color base = getFactionForUIColors().getBaseUIColor();
		Color dark = getFactionForUIColors().getDarkUIColor();
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		FactionAPI faction2 = Global.getSector().getFaction(Factions.PLAYER);
		
		// image
		info.addImages(width, 128, opad, opad, faction.getCrest(), faction2.getCrest());
		
		Map<String, String> replace = new HashMap<>();
		
		// first description para
		String theFactionName = faction.getDisplayNameWithArticle();
		String factionName = faction.getDisplayNameWithArticleWithoutArticle();
		replace.put("$theFaction", theFactionName);
		replace.put("$TheFaction", Misc.ucFirst(theFactionName));
		replace.put("$hasOrHave", faction.getDisplayNameHasOrHave());
		replace.put("$market", market.getName());
		replace.put("$system", market.getContainingLocation().getNameWithLowercaseType());
		replace.put("$playerName", Global.getSector().getPlayerPerson().getNameString());
		//StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);
		
		String str = StringHelper.getStringAndSubstituteTokens("nex_tribute", "intel_desc1", replace);
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(factionName, market.getName());
		label.setHighlightColors(faction.getBaseUIColor(), h);
		
		// second description para
		replace.clear();
		replace.put("$theFaction", theFactionName);
		replace.put("$TheFaction", Misc.ucFirst(theFactionName));		
		str = StringHelper.getStringAndSubstituteTokens("nex_tribute", "intel_desc2", replace);
		
		info.addPara(str, opad, h, 
				Math.round(TributeCondition.getIncomePenalty() * 100) + "%", 
				Math.round(100 - TributeCondition.getImmigrationMult() * 100) + "%",
				TributeCondition.MAX_SIZE + "");		
		
		// report on current status
		if (status == TributeStatus.PENDING) {
			// buttons to accept/reject the tribute demand
			replace.clear();
			String days = Math.round(daysRemaining) + "";
			String daysStr = getDaysString(daysRemaining);
			replace.put("$timeLeft", days);
			replace.put("$days", daysStr);
			str = StringHelper.getStringAndSubstituteTokens("nex_tribute", "intel_descTime", replace);
			info.addPara(str, opad, h, days);

			ButtonAPI button = info.addButton(StringHelper.getString("accept", true), BUTTON_ACCEPT, 
							base, dark, (int)(width), 20f, opad * 3f);
			ButtonAPI button2 = info.addButton(StringHelper.getString("reject", true), BUTTON_REJECT, 
							getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
						  (int)(width), 20f, opad);
		} else if (status == TributeStatus.ACTIVE) {
			info.addSectionHeading(StringHelper.getString("status", true), base, dark, Alignment.MID, opad);
			info.addPara(StringHelper.getStringAndSubstituteToken("nex_tribute", 
							"intel_descAccepted", "$market", market.getName()), opad);
			ButtonAPI button = info.addButton(StringHelper.getString("cancel", true), BUTTON_REJECT, 
							base, dark, (int)(width), 20f, opad);
		} else {
			info.addSectionHeading(StringHelper.getString("result", true), base, dark, Alignment.MID, opad);
			
			switch (status) {
				case REJECTED:
					info.addPara(StringHelper.getStringAndSubstituteToken("nex_tribute", 
							"intel_descRejected", "$market", market.getName()), opad);
					break;
				case CANCELLED:
				default:
					info.addPara(StringHelper.getString("nex_tribute", "intel_descOver"), opad);
					break;
			}
			
			if (repResult != null) {
				// display relationship change from event, and relationship following event
				Color deltaColor = repResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
				String delta = (int)Math.abs(repResult.delta * 100) + "";
				String newRel = NexUtilsReputation.getRelationStr(storedRelation);
				String fn = NexUtilsFaction.getFactionShortName(factionId);
				str = StringHelper.getString("exerelin_diplomacy", "intelRepResultNegativePlayer");
				str = StringHelper.substituteToken(str, "$faction", fn);
				str = StringHelper.substituteToken(str, "$deltaAbs", delta);
				str = StringHelper.substituteToken(str, "$newRelationStr", newRel);

				LabelAPI para = info.addPara(str, opad);
				para.setHighlight(fn, delta, newRel);
				para.setHighlightColors(faction.getBaseUIColor(), 
						deltaColor, NexUtilsReputation.getRelColor(storedRelation));

				// days ago
				//if (cancelTime != null) 
				//	info.addPara(Misc.getAgoStringForTimestamp(cancelTime) + ".", opad);
			}
		}
	}
	
	public void accept() {
		String condId = market.addCondition(TributeCondition.CONDITION_ID);
		cond = market.getSpecificCondition(condId);
		((TributeCondition)cond.getPlugin()).setup(getFactionForUIColors(), this);
		status = TributeStatus.ACTIVE;
		setImportant(false);
	}
	
	public void reject() {
		market.getMemoryWithoutUpdate().set(REJECTION_FACTION_MEM_KEY, factionId);
		
		int size = market.getSize() - 1;
		if (size < 1) size = 1;
		repResult = NexUtilsReputation.adjustPlayerReputation(getFactionForUIColors(), -REJECT_REP_PENALTY * size);
		storedRelation = getFactionForUIColors().getRelationship(Factions.PLAYER);
		DiplomacyManager.getManager().getDiplomacyBrain(factionId).reportDiplomacyEvent(
				PlayerFactionStore.getPlayerFactionId(), -repResult.delta);
		
		status = TributeStatus.REJECTED;
		endEvent();
		setImportant(false);
	}
	
	public void cancel() {
		status = TributeStatus.CANCELLED;
		market.getMemoryWithoutUpdate().unset(REJECTION_FACTION_MEM_KEY);
		cancelTime = Global.getSector().getClock().getTimestamp();
		sendUpdateIfPlayerHasIntel(CANCELLED_UPDATE, false);
		endEvent();
		setImportant(false);
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		prompt.addPara(StringHelper.getString("nex_tribute", "intel_dialogConfirm"), 0);
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
			reject();
		}
		super.buttonPressConfirmed(buttonId, ui);
	}
	
	protected void endEvent() {
		market.removeCondition(TributeCondition.CONDITION_ID);
		cond = null;
		endAfterDelay();
	}
	
	/**
	 * Checks if the conditions for the market to pay tribute are still valid.
	 */
	protected void checkContinueTribute() {
		if (!market.isInEconomy()) {
			cancel();
			return;
		}
		
		if (market.getSize() > TributeCondition.MAX_SIZE) {
			cancel();
			return;
		}
		
		if (!market.getFaction().isPlayerFaction()) {
			cancel();
			return;
		}
		
		FactionAPI claimingFaction = NexUtilsFaction.getClaimingFaction(market.getPrimaryEntity());
		if (claimingFaction == null || !claimingFaction.getId().equals(factionId) 
				|| claimingFaction.isHostileTo(Factions.PLAYER) 
				|| claimingFaction == Misc.getCommissionFaction()) 
		{
			cancel();
			return;
		}
	}
	
	@Override
	protected void advanceImpl(float amount) {
		if (this.isEnding() || this.isEnded())
			return;
		
		checkContinueTribute();
		
		// past here is countdown to accept/reject
		if (status != TributeStatus.PENDING)
			return;
		
		daysRemaining -= Global.getSector().getClock().convertToDays(amount);
		
		if (daysRemaining <= 0) {
			reject();
			sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
			endAfterDelay();
		}
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String str = StringHelper.getString("nex_tribute", "intel_title");
		if (listInfoParam == EXPIRED_UPDATE)
			str += " - " + StringHelper.getString("expired");
		if (listInfoParam == CANCELLED_UPDATE)
			str += " - " + StringHelper.getString("cancelled");
		else if (status == TributeStatus.ACTIVE)
			str += " - " + StringHelper.getString("accepted");
		else if (status == TributeStatus.REJECTED)
			str += " - " + StringHelper.getString("rejected");
		else if (isEnding() || isEnded())
			str += " - " + StringHelper.getString("over");
		
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
		//return getFactionForUIColors().getCrest();
		return "graphics/exerelin/icons/intel/lose_credits.png";
	}
	
	@Override
	public String getSortString() {
		return "Diplomacy";
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return market.getPrimaryEntity();
	}
	
	@Override
	public String getCommMessageSound() {
		return getSoundColonyThreat();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(factionId);
	}
	
	public static void debug(String factionId, String marketId) {
		new TributeIntel(factionId, Global.getSector().getEconomy().getMarket(marketId)).init();
	}
	
	/**
	 * Has player rejected a past tribute demand by {@code factionId} for this market?
	 * @param factionId
	 * @param market
	 * @return 
	 */
	public static boolean hasRejectedTribute(String factionId, MarketAPI market) {
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (!mem.contains(REJECTION_FACTION_MEM_KEY))
			return false;
		return mem.getString(REJECTION_FACTION_MEM_KEY).equals(factionId);
	}
	
	public static boolean hasOngoingIntel(MarketAPI market) {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(TributeIntel.class)) {
			TributeIntel ti = (TributeIntel)intel;
			if (ti.isEnding() || ti.isEnded()) continue;
			if (ti.market == market)
				return true;
		}
		return false;
	}
	
	public static TributeIntel getOngoingIntel(MarketAPI market) {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(TributeIntel.class)) {
			TributeIntel ti = (TributeIntel)intel;
			if (ti.isEnding() || ti.isEnded()) continue;
			if (ti.market == market)
				return ti;
		}
		return null;
	}
}

package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.econ.TributeCondition;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

public class TributeIntel extends BaseIntelPlugin {
	
	public static final Object EXPIRED_UPDATE = new Object();
	public static final Object CANCELLED_UPDATE = new Object();
	public static final String BUTTON_ACCEPT = "Accept";
	public static final String BUTTON_REJECT = "Reject";
	public static final String BUTTON_CANCEL = "Cancel";
	public static final String REJECTION_FACTION_MEM_KEY = "$nex_tributeFaction";
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
	
	// bullet points
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = !status.isOver() ? Misc.getBasePlayerColor() : Misc.getGrayColor();
		info.addPara(getName(), c, 0f);
		bullet(info);

		float initPad = 3f, pad = 0;
		Color tc = getBulletColorForMode(mode);
		ExerelinUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
		info.addPara(market.getName(), tc, pad);
	}
	
	// text sidebar
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f, pad = 3f;
		
		Color h = Misc.getHighlightColor();
		
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
		label.setHighlight(market.getName(), factionName);
		label.setHighlightColors(h, faction.getBaseUIColor());
		
		// second description para
		replace.clear();
		replace.put("$theFaction", theFactionName);
		replace.put("$TheFaction", Misc.ucFirst(theFactionName));		
		str = StringHelper.getStringAndSubstituteTokens("nex_tribute", "intel_desc2", replace);
		
		info.addPara(str, opad, h, 
				Math.round(TributeCondition.TRIBUTE_INCOME_FACTOR * 100) + "%", 
				Math.round(100 - TributeCondition.TRIBUTE_IMMIGRATION_MULT * 100) + "%",
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
							getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
						  (int)(width), 20f, opad * 3f);
			ButtonAPI button2 = info.addButton(StringHelper.getString("reject", true), BUTTON_REJECT, 
							getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
						  (int)(width), 20f, opad);
		} else if (status == TributeStatus.ACTIVE) {
			// TODO: list tribute status
			info.addSectionHeading(StringHelper.getString("status", true), Alignment.MID, opad);
			info.addPara(StringHelper.getStringAndSubstituteToken("nex_tribute", 
							"intel_descAccepted", "$market", market.getName()), opad);
			ButtonAPI button = info.addButton(StringHelper.getString("cancel", true), BUTTON_REJECT, 
							getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
						  (int)(width), 20f, opad);
		} else {
			info.addSectionHeading(StringHelper.getString("result", true), getFactionForUIColors().getBaseUIColor(), 
					getFactionForUIColors().getDarkUIColor(), Alignment.MID, opad);
			
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
				String fn = ExerelinUtilsFaction.getFactionShortName(factionId);
				str = StringHelper.getString("exerelin_diplomacy", "intelRepResultNegativePlayer");
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
	
	public void accept() {
		String condId = market.addCondition(TributeCondition.CONDITION_ID);
		cond = market.getSpecificCondition(condId);
		((TributeCondition)cond.getPlugin()).setup(getFactionForUIColors(), this);
		status = TributeStatus.ACTIVE;
	}
	
	public void reject() {
		market.getMemoryWithoutUpdate().set(REJECTION_FACTION_MEM_KEY, factionId);
		
		int size = market.getSize() - 1;
		if (size < 1) size = 1;
		repResult = NexUtilsReputation.adjustPlayerReputation(getFactionForUIColors(), -REJECT_REP_PENALTY * size);
		storedRelation = getFactionForUIColors().getRelationship(Factions.PLAYER);
		
		status = TributeStatus.REJECTED;
		endEvent();
	}
	
	public void cancel() {
		status = TributeStatus.CANCELLED;
		market.getMemoryWithoutUpdate().unset(REJECTION_FACTION_MEM_KEY);
		sendUpdateIfPlayerHasIntel(CANCELLED_UPDATE, false);
		endEvent();
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
		
		FactionAPI claimingFaction = Misc.getClaimingFaction(market.getPrimaryEntity());
		if (claimingFaction == null || !claimingFaction.getId().equals(factionId) 
				|| claimingFaction.isHostileTo(Factions.PLAYER)) {
			cancel();
			return;
		}
	}
	
	@Override
	protected void advanceImpl(float amount) {
		if (this.isEnding() || this.isEnded())
			return;
				
		// TODO: check if system is still controlled by someone else, or grown too large
		checkContinueTribute();
		
		// past here is countdown to accept/reject
		if (status != TributeStatus.PENDING)
			return;
		
		daysRemaining -= Global.getSector().getClock().convertToDays(amount);
		
		if (daysRemaining <= 0) {
			status = TributeStatus.REJECTED;
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
}

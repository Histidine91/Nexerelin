package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the event for player governorship of a market.
 */
public class BuyColonyIntel extends BaseIntelPlugin implements InvasionListener {
	
	public static final Object EXPIRED_UPDATE = new Object();
	public static final String BUTTON_GOTO = "goToPlanet";
	
	protected String factionId;
	protected MarketAPI market;
	protected Status status = Status.ACTIVE;
	//protected Float refund;
	
	public static enum Status {
		ACTIVE, QUIT, RESIGNED_COMMISSION, LOST, DESTROYED
	}
	
	public BuyColonyIntel(String factionId, MarketAPI market)
	{
		this.factionId = factionId;
		this.market = market;
	}
	
	public void init() { //to my knowledge only the player can buy colonies, queueIntel not needed
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().getListenerManager().addListener(this);
		Global.getSector().addScript(this);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {	
		NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
	}
	
	// text sidebar
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f, pad = 3f;
		
		Color h = Misc.getHighlightColor();
		Color base = getFactionForUIColors().getBaseUIColor();
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		// image
		info.addImage(getFactionForUIColors().getLogo(), width, 128, pad);
		
		Map<String, String> replace = new HashMap<>();
		String cat = "nex_buyColony";
		
		// first description para
		String theFactionName = faction.getDisplayNameWithArticle();
		String factionName = faction.getDisplayNameWithArticleWithoutArticle();
		replace.put("$theFaction", theFactionName);
		replace.put("$TheFaction", Misc.ucFirst(theFactionName));
		replace.put("$market", market.getName());
		replace.put("$location", market.getContainingLocation().getNameWithLowercaseType());
		String str;
		LabelAPI label;
		
		switch (status) {
			case ACTIVE:
				str = StringHelper.getStringAndSubstituteTokens(cat, "intelDesc", replace);
				label = info.addPara(str, opad);
				label.setHighlight(market.getName(), factionName);
				label.setHighlightColors(h, base);
				break;
			case QUIT:
				str = StringHelper.getStringAndSubstituteTokens(cat, "intelDescQuit", replace);
				label = info.addPara(str, opad);
				label.setHighlight(market.getName(), factionName);
				label.setHighlightColors(h, base);
				break;
			case LOST:
				FactionAPI otherFaction = market.getFaction();
				String theOtherFactionName = otherFaction.getDisplayNameWithArticle();
				replace.put("$theOtherFaction", theOtherFactionName);
				replace.put("$TheOtherFaction", Misc.ucFirst(theOtherFactionName));
				str = StringHelper.getStringAndSubstituteTokens(cat, "intelDescLost", replace);				
				label = info.addPara(str, opad);
				label.setHighlight(market.getName(), otherFaction.getDisplayNameWithArticleWithoutArticle());
				label.setHighlightColors(h, otherFaction.getBaseUIColor());
				break;
			case RESIGNED_COMMISSION:
				str = StringHelper.getStringAndSubstituteTokens(cat, "intelDescResignedCommission", replace);
				label = info.addPara(str, opad);
				label.setHighlight(factionName, market.getName());
				label.setHighlightColors(base, h);
				break;
		}
		
		// refund would go here, if we had any
		/*
		if (status == Status.ACTIVE) {
			str = StringHelper.getString(cat, "intelDescPotentialRefund");
			label = info.addPara(str, opad);
		}
		*/
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.OUTPOSTS, market);
	}
	
	protected void reassignAdmin() {
		FactionAPI player = Global.getSector().getPlayerFaction();
		ColonyManager.reassignAdminIfNeeded(market, player, player);
	}
	
	public void cancel(Status newStatus) {
		market.setPlayerOwned(false);
		SectorManager.updateSubmarkets(market, market.getFactionId(), market.getFactionId());
		reassignAdmin();
		ColonyManager.getManager().checkGatheringPoint(market);
		status = newStatus;
		endAfterDelay();
		this.sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
	}
	
	/**
	 * Checks if the conditions for the market to be governed are still valid.
	 */
	protected void checkCancel() {
		if (!market.isInEconomy()) {
			cancel(Status.DESTROYED);
		} else if (!factionId.equals(Misc.getCommissionFactionId())) {
			cancel(Status.RESIGNED_COMMISSION);
		}
	}
	
	@Override
	protected void advanceImpl(float amount) {
		if (this.isEnding() || this.isEnded())
			return;
		
		checkCancel();
	}
	
	@Override
	protected void notifyEnding() {
		Global.getSector().getListenerManager().removeListener(this);
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String str = StringHelper.getStringAndSubstituteToken("nex_buyColony", "intelTitle", "$market", market.getName());
		if (isEnding() || isEnded())
			str += " - " + StringHelper.getString("over");
		else if (status == Status.LOST)
			str += " - " + StringHelper.getString("suspended");
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("colonies", true));
		tags.add(factionId);
		return tags;
	}
		
	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public String getSortString() {
		return "Governorship";
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return market.getPrimaryEntity();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(factionId);
	}
	
	
	@Override
	public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, 
			Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {
	}

	@Override
	public void reportInvasionRound(InvasionRound.InvasionRoundResult result, 
			CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {
	}

	@Override
	public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, 
			MarketAPI market, float numRounds, boolean success) {
	}

	@Override
	public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, 
			boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
		if (market != this.market)
			return;
		
		if (!newOwner.getId().equals(factionId)) {
			status = Status.LOST;
			market.setPlayerOwned(false);
			reassignAdmin();
			ColonyManager.getManager().checkGatheringPoint(market);
		}
		else if (status == Status.LOST && newOwner.getId().equals(factionId)) {
			status = Status.ACTIVE;
			market.setPlayerOwned(true);
			SectorManager.updateSubmarkets(market, Factions.PLAYER, Factions.PLAYER);
			reassignAdmin();
		}
	}
	
	public static boolean hasOngoingIntel(MarketAPI market) {
		return getOngoingIntel(market) != null;
	}
	
	public static BuyColonyIntel getOngoingIntel(MarketAPI market) {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(BuyColonyIntel.class)) {
			BuyColonyIntel ti = (BuyColonyIntel)intel;
			if (ti.isEnding() || ti.isEnded()) continue;
			if (ti.market == market)
				return ti;
		}
		return null;
	}
}

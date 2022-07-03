package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.BASE_PRICE_MULT;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.ILLEGAL_QUANTITY_MULT;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.MAX_BASE_VALUE;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.MIN_BASE_VALUE;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.MISSION_DAYS;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.PROB_BAR_UNDERWORLD;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.PROB_ILLEGAL_IF_UNDERWORLD;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.PROB_REMOTE;
import static com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission.SAME_CONTACT_DEBUG;
import static com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission.getRoundNumber;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;

// Fixes https://fractalsoftworks.com/forum/index.php?topic=24770.0
public class Nex_CheapCommodityMission extends CheapCommodityMission {
	
	// only thing changed is one commented-out line
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		if (barEvent) {
			if (rollProbability(PROB_BAR_UNDERWORLD)) {
				setGiverRank(Ranks.CITIZEN);
				setGiverPost(pickOne(Ranks.POST_SMUGGLER, Ranks.POST_GANGSTER, 
							 		 Ranks.POST_FENCE, Ranks.POST_CRIMINAL));
				setGiverImportance(pickImportance());
				setGiverFaction(Factions.PIRATES);
				setGiverTags(Tags.CONTACT_UNDERWORLD);
			} else {
				setGiverRank(Ranks.CITIZEN);
				setGiverPost(pickOne(Ranks.POST_TRADER, Ranks.POST_COMMODITIES_AGENT, 
							 		 Ranks.POST_MERCHANT, Ranks.POST_INVESTOR, Ranks.POST_PORTMASTER));
				setGiverImportance(pickImportance());
				setGiverTags(Tags.CONTACT_TRADE);
			}
			findOrCreateGiver(createdAt, false, false);
		}
		
		PersonAPI person = getPerson();
		if (person == null) return false;
		MarketAPI market = person.getMarket();
		if (market == null) return false;
		
		if (!setPersonMissionRef(person, "$cheapCom_ref")) {
			return false;
		}
		
		if (barEvent) {
			setGiverIsPotentialContactOnSuccess();
		}
		
		PersonImportance importance = person.getImportance();
		boolean canOfferRemote = importance.ordinal() >= PersonImportance.MEDIUM.ordinal();
		boolean preferExpensive = getQuality() >= PersonImportance.HIGH.getValue();
		variation = Variation.LOCAL;
		if (canOfferRemote && rollProbability(PROB_REMOTE)) {
			variation = Variation.REMOTE;
		}
		if (SAME_CONTACT_DEBUG) variation = Variation.REMOTE;

		CommodityOnMarketAPI com = null;
		if (variation == Variation.LOCAL) {
			requireMarketIs(market);
			//requireMarketLocationNot(createdAt.getContainingLocation());
			requireCommodityIsNotPersonnel();
			requireCommodityDeficitAtMost(0);
			requireCommodityAvailableAtLeast(1);
			requireCommoditySurplusAtLeast(1);
			if (person.hasTag(Tags.CONTACT_UNDERWORLD) && rollProbability(PROB_ILLEGAL_IF_UNDERWORLD)) {
				preferCommodityIllegal();
			} else {
				requireCommodityLegal();
			}
			if (preferExpensive) {
				preferCommodityTags(ReqMode.ALL, Commodities.TAG_EXPENSIVE);
			}
			com = pickCommodity();
		} 
		
		if (com == null && canOfferRemote) {
			variation = Variation.REMOTE;
		}
		
		
		if (variation == Variation.REMOTE) {
			requireMarketIsNot(market);
			requireMarketFaction(market.getFactionId());
			if (SAME_CONTACT_DEBUG) {
				requireMarketIs("jangala");
			}
			requireCommodityIsNotPersonnel();
			requireCommodityDeficitAtMost(0);
			requireCommodityAvailableAtLeast(1);
			requireCommoditySurplusAtLeast(1);
			preferMarketInDirectionOfOtherMissions();
			if (person.hasTag(Tags.CONTACT_UNDERWORLD) && rollProbability(PROB_ILLEGAL_IF_UNDERWORLD)) {
				preferCommodityIllegal();
			} else {
				requireCommodityLegal();
			}
			if (preferExpensive) {
				preferCommodityTags(ReqMode.ALL, Commodities.TAG_EXPENSIVE);
			}
			com = pickCommodity();
			if (com != null) remoteMarket = com.getMarket();
		}
		
		if (SAME_CONTACT_DEBUG) {
			com = Global.getSector().getEconomy().getMarket("jangala").getCommodityData(Commodities.ORGANICS);
			remoteMarket = com.getMarket();
		}
		
		if (com == null) return false;
		
		commodityId = com.getId();
		
		float value = MIN_BASE_VALUE + (MAX_BASE_VALUE - MIN_BASE_VALUE) * getQuality();
		quantity = getRoundNumber(value / com.getCommodity().getBasePrice());
		if (com.isIllegal()) {
			quantity *= ILLEGAL_QUANTITY_MULT;
		}
		
		if (quantity < 10) quantity = 10;
		pricePerUnit = (int) (com.getMarket().getSupplyPrice(com.getId(), quantity, true) / (float) quantity * 
							  BASE_PRICE_MULT / getRewardMult());
		pricePerUnit = getRoundNumber(pricePerUnit);
		if (pricePerUnit < 2) pricePerUnit = 2;
		
		
		if (variation == Variation.REMOTE) {
			remoteContact = findOrCreateTrader(remoteMarket.getFactionId(), remoteMarket, true);
			//person = findOrCreateCriminal(market, true);
			if (remoteContact == null || !setPersonMissionRef(remoteContact, "$cheapCom_ref")) {
				return false;
			}
			setPersonDoGenericPortAuthorityCheck(remoteContact);
			makeImportant(remoteContact, "$cheapCom_hasCommodity", Stage.TALK_TO_PERSON);
			
			setStartingStage(Stage.TALK_TO_PERSON);
			setSuccessStage(Stage.COMPLETED);
			setFailureStage(Stage.FAILED);
			
			setStageOnMemoryFlag(Stage.COMPLETED, remoteContact, "$cheapCom_completed");
			setTimeLimit(Stage.FAILED, MISSION_DAYS, null);
			
		}
		
		if (getQuality() < 0.5f) {
			setRepFactionChangesVeryLow();
		} else {
			setRepFactionChangesLow();
		}
		setRepPersonChangesMedium();
		
		return true;
	}
}

package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;


public class FactionInsuranceEvent extends BaseEventPlugin {

	public static final float HARD_MODE_MULT = 0.5f;
	public static final boolean COMPENSATE_DMODS = false;
	public static final float DMOD_BASE_COST = Global.getSettings().getFloat("baseRestoreCostMult");
	public static final float DMOD_COST_PER_MOD = Global.getSettings().getFloat("baseRestoreCostMultPerDMod");
	public static final float LIFE_INSURANCE_PER_LEVEL = 2000f;
	public static final float BASE_HULL_VALUE_MULT_FOR_DMODS = 0.4f;
	
	public static Logger log = Global.getLogger(FactionInsuranceEvent.class);
	
	protected float paidAmount = 0f;
	protected Map<FleetMemberAPI, Float[]> disabledOrDestroyedMembers = new HashMap<>();	// value is base buy value and number of D mods
	protected List<OfficerDataAPI> deadOfficers = new ArrayList<>();
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
	
	// log all friendly ships that die, so we can compare how many D mods they have now to how many they have when they get recovered
	@Override
	public void reportPlayerEngagement(EngagementResultAPI result) {
		EngagementResultForFleetAPI er = result.didPlayerWin() ? result.getWinnerResult() : result.getLoserResult();
		List<FleetMemberAPI> disabledOrDestroyed = new ArrayList<>();
		disabledOrDestroyed.addAll(er.getDisabled());
		disabledOrDestroyed.addAll(er.getDestroyed());
		
		for (FleetMemberAPI member : disabledOrDestroyed)
		{
			if (disabledOrDestroyedMembers.containsKey(member))
				continue;	// though this shouldn't happen anyway
			if (member.isAlly())
				continue;
			if (member.isFighterWing())
				continue;
			//log.info("Member " + member.getShipName() + " disabled or destroyed");
			disabledOrDestroyedMembers.put(member, new Float[]{member.getBaseBuyValue(), (float)countDMods(member)});
		}
	}
	
	@Override
	public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
	{
		if (!battle.isPlayerInvolved()) return;
		// no insurance during tutorial
		if (Global.getSector().getMemoryWithoutUpdate().contains("$tutStage"))
		{
			return;
		}
			
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		
		float value = 0f;
		String stage = "report";
		
		String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
		//if (alignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;  // no self-insurance
		
		// Exi is not technically alive in Corvus mode, but still treated as present due to Tasserus
		if (ExerelinUtilsFaction.isExiInCorvus(alignedFactionId))
		{
			// do nothing
		}
		else if (!SectorManager.isFactionAlive(alignedFactionId)) 
			return;
		
		FactionAPI alignedFaction = Global.getSector().getFaction(alignedFactionId);
		
		List<FleetMemberAPI> fleetCurrent = fleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
			// dead, not recovered
			if (!fleetCurrent.contains(member)) {
				float amount = member.getBaseBuyValue();
				if (disabledOrDestroyedMembers.containsKey(member))
				{
					Float[] entry = disabledOrDestroyedMembers.get(member);
					amount = entry[0];
				}				
				log.info("Insuring lost ship " + member.getShipName() + " for " + amount);
				value += amount;
			}
			// dead, recovered; compare "before" to "after" in D mod count and base value
			else if (disabledOrDestroyedMembers.containsKey(member))
			{
				Float[] entry = disabledOrDestroyedMembers.get(member);
				float prevValue = entry[0];
				int dmodsOld = (int)(float)entry[1];
				int dmods = countDMods(member);
				
				float dmodCompensation = 0;
				if (dmods > dmodsOld && COMPENSATE_DMODS)
				{
					dmodCompensation = member.getHullSpec().getBaseValue() * BASE_HULL_VALUE_MULT_FOR_DMODS;
					if (dmodsOld == 0)
						dmodCompensation *= DMOD_BASE_COST;
					dmodCompensation *= Math.pow(DMOD_COST_PER_MOD, dmods - dmodsOld);
				}
				
				float amount = prevValue - member.getBaseBuyValue() + dmodCompensation;
				log.info("Insuring recovered ship " + member.getShipName() + " for " + amount);
				log.info("Compensation for D-mods: " + dmodCompensation);
				value += amount;
			}
		}
		for (OfficerDataAPI deadOfficer : deadOfficers)
		{
			float amount = deadOfficer.getPerson().getStats().getLevel() * LIFE_INSURANCE_PER_LEVEL;
			log.info("Insuring dead officer " + deadOfficer.getPerson().getName().getFullName() + " for " + amount);
			value += amount;
		}
		
		disabledOrDestroyedMembers.clear();
		deadOfficers.clear();
		
		if (value <= 0) return;
		
		if (alignedFaction.isAtBest("player", RepLevel.SUSPICIOUS))
		{
			paidAmount = 0;
			stage = "report_unpaid";
		}
		else paidAmount = value * ExerelinConfig.playerInsuranceMult;
		
		if (SectorManager.getHardMode())
			paidAmount *= HARD_MODE_MULT; 
		
		MarketAPI closestMarket = ExerelinUtils.getClosestMarket(alignedFactionId);
		if (closestMarket != null)
		{
			Global.getSector().reportEventStage(this, stage, closestMarket.getPrimaryEntity(), MessagePriority.ENSURE_DELIVERY, new BaseOnMessageDeliveryScript() {
				public void beforeDelivery(CommMessageAPI message) {
					Global.getSector().getPlayerFleet().getCargo().getCredits().add(paidAmount);
				}
			});
		}
	}
	
	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_events", "insurance");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		FactionAPI faction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		String factionName = ExerelinUtilsFaction.getFactionShortName(faction);
		String theFactionName = faction.getDisplayNameLongWithArticle();
		map.put("$sender", factionName);
		map.put("$employer", factionName);
		map.put("$Employer", Misc.ucFirst(factionName));
		map.put("$theEmployer", theFactionName);
		map.put("$TheEmployer", Misc.ucFirst(theFactionName));
		map.put("$paid", Misc.getWithDGS((int)paidAmount) + Strings.C);
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$paid");
		return result.toArray(new String[0]);
	}
	
	@Override
	public String getCurrentImage() {
		FactionAPI myFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		return myFaction.getLogo();
	}
	
	@Override
	public boolean isDone() {
		return false;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
	
	protected int countDMods(FleetMemberAPI member)
	{
		Set<String> permamods = member.getVariant().getPermaMods();
		int dmods = 0;
		for (String mod : permamods)
		{
			HullModSpecAPI modspec = Global.getSettings().getHullModSpec(mod);
			if (modspec.hasTag("dmod"))
			{
				dmods++;
			}
		}
		//log.info("Fleet member " + member.getShipName() + " has " + dmods + " D-mods");
		return dmods;
	}
	
	public void addDeadOfficers(List<OfficerDataAPI> officers)
	{
		deadOfficers.addAll(officers);
	}
}
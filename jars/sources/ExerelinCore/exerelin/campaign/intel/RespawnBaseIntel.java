package exerelin.campaign.intel;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyIntel.BountyResult;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyIntel.BountyResultType;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.procgen.MarkovNames;
import com.fs.starfarer.api.impl.campaign.procgen.MarkovNames.MarkovNameResult;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.AddedEntity;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.EntityLocation;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.LocationType;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.invasion.RespawnInvasionIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.StringHelper;
import java.util.List;
import java.util.Random;

// Adapted from PirateBaseIntel
// Note: This doesn't actually manage the respawn fleets or anything;
// it just makes a market that the fleets can spawn from
public class RespawnBaseIntel extends BaseIntelPlugin implements EveryFrameScript, FleetEventListener,
																EconomyUpdateListener {
		
	//public static Object BOUNTY_EXPIRED_PARAM = new Object();
	public static Object DISCOVERED_PARAM = new Object();
	public static Object ABANDONED_PARAM = new Object();
	
	/*
	public static class BaseBountyData {
		public float bountyElapsedDays = 0f;
		public float bountyDuration = 0;
		public float baseBounty = 0;
		public float repChange = 0;
		public FactionAPI bountyFaction = null;
	}
	*/
	
	public static Logger log = Global.getLogger(RespawnBaseIntel.class);
	
	protected String factionId;
	protected StarSystemAPI system;
	protected MarketAPI market;
	protected SectorEntityToken entity;
	protected boolean destroyed;
	
	protected float elapsedDays = 0f;
	protected float duration = 360f;
	
	//protected BaseBountyData bountyData = null;
		
	protected IntervalUtil monthlyInterval = new IntervalUtil(20f, 40f);
	//protected int raidTimeoutMonths = 0;
	
	public RespawnBaseIntel(StarSystemAPI system, String factionId) {
		this.system = system;
		this.factionId = factionId;
	
		market = Global.getFactory().createMarket(Misc.genUID(), 
				StringHelper.getString("nex_respawn", "marketName"), 3);
		market.setSize(3);
		market.setHidden(true);
		
		market.setFactionId(factionId);
		
		market.setSurveyLevel(SurveyLevel.FULL);
		
		market.setFactionId(factionId);
		market.addCondition(Conditions.POPULATION_3);
		
		market.addIndustry(Industries.POPULATION);
		market.addIndustry(Industries.SPACEPORT);
		market.addIndustry(Industries.MILITARYBASE);
		market.addIndustry(Industries.GROUNDDEFENSES);
		
		market.addSubmarket(Submarkets.SUBMARKET_OPEN);
		market.addSubmarket(Submarkets.SUBMARKET_BLACK);
		
		market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());
		
		WeightedRandomPicker<EntityLocation> locs = getLocsPicker(system);
		EntityLocation loc = locs.pick();
		
		if (loc == null) {
			log.info("No location found");
			endImmediately();
			return;
		}
		
		AddedEntity added = BaseThemeGenerator.addNonSalvageEntity(system, loc, Entities.MAKESHIFT_STATION, factionId);
		
		if (added == null || added.entity == null) {
			log.info("Failed to generate entity");
			endImmediately();
			return;
		}
		
		entity = added.entity;
		
		String name = generateName();
		if (name == null) {
			log.info("Failed to generate name");
			endImmediately();
			return;
		}
		
		String stationType = pickStationType();
		if (stationType == null) {
			log.info("No station industry available to spawn");
			endImmediately();
			return;
		}
		
		market.setName(name);
		entity.setName(name);
		
		BaseThemeGenerator.convertOrbitWithSpin(entity, -5f);
		
		market.setPrimaryEntity(entity);
		entity.setMarket(market);
		
		entity.setSensorProfile(1f);
		entity.setDiscoverable(true);
		entity.getDetectedRangeMod().modifyFlat("gen", 5000f);
		
		market.setEconGroup(market.getId());
		market.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
		
		market.reapplyIndustries();
		
		Global.getSector().getEconomy().addMarket(market, true);
		market.addIndustry(stationType);	// down here so it doesn't break something if the station spawn aborts
		
		log.info(String.format("Added respawn base in [%s] for faction %s", system.getName(), market.getFaction().getDisplayName()));
		
		Global.getSector().getIntelManager().addIntel(this, true);
		Global.getSector().addScript(this);
		timestamp = null;
		
		Global.getSector().getListenerManager().addListener(this);
		Global.getSector().getEconomy().addUpdateListener(this);
	}
	
	@Override
	public boolean isHidden() {
		if (super.isHidden()) return true;
		//if (true) return false;
		return timestamp == null;
	}
	
	public StarSystemAPI getSystem() {
		return system;
	}

	protected String pickStationType() {
		WeightedRandomPicker<String> stations = new WeightedRandomPicker<String>();
		
		if (getFactionForUIColors().getCustom().has(Factions.CUSTOM_PIRATE_BASE_STATION_TYPES)) {
			try {
				JSONObject json = getFactionForUIColors().getCustom().getJSONObject(Factions.CUSTOM_PIRATE_BASE_STATION_TYPES);
				for (String key : JSONObject.getNames(json)) {
					stations.add(key, (float) json.optDouble(key, 0f));
				}
			} catch (JSONException e) {
				stations.clear();
			}
		}
		
		if (stations.isEmpty()) {
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			return conf.getRandomDefenceStation(new Random(), 0);
		}
		else
			return stations.pick();
	}
	
	protected Industry getStationIndustry() {
		for (Industry curr : market.getIndustries()) {
			if (curr.getSpec().hasTag(Industries.TAG_STATION)) {
				return curr;
			}
		}
		return null;
	}
	
	protected CampaignFleetAPI addedListenerTo = null;
	@Override
	protected void advanceImpl(float amount) {
		//makeKnown();
		float days = Global.getSector().getClock().convertToDays(amount);
		//days *= 1000f;
		//Global.getSector().getCurrentLocation().getName()
		//entity.getContainingLocation().getName()
		if (getPlayerVisibleTimestamp() == null && entity.isInCurrentLocation() && isHidden()) {
			makeKnown();
			sendUpdateIfPlayerHasIntel(DISCOVERED_PARAM, false);
		}
		
		/*
		if (!sentBountyUpdate && bountyData != null && 
				(Global.getSector().getIntelManager().isPlayerInRangeOfCommRelay() ||
						(!isHidden() && DebugFlags.SEND_UPDATES_WHEN_NO_COMM))) {
			makeKnown();
			sendUpdateIfPlayerHasIntel(bountyData, false);
			sentBountyUpdate = true;
		}
		*/
		
		CampaignFleetAPI fleet = Misc.getStationFleet(market);
		if (fleet != null && addedListenerTo != fleet) {
			if (addedListenerTo != null) {
				addedListenerTo.removeEventListener(this);
			}
			fleet.addEventListener(this);
			addedListenerTo = fleet;			
		}
		
		monthlyInterval.advance(days);
		if (monthlyInterval.intervalElapsed()) {
		}

//		if (bountyData == null && target != null) {
//			setBounty();
//		}
		
		/*
		if (bountyData != null) {
			boolean canEndBounty = !entity.isInCurrentLocation();
			bountyData.bountyElapsedDays += days;
			if (bountyData.bountyElapsedDays > bountyData.bountyDuration && canEndBounty) {
				endBounty();
			}
		}
		*/
		
		// limit base's lifespan
		elapsedDays += days;
		checkExpiry();
	}
	
	protected void checkExpiry() {
		if (getTimeRemainingFraction() <= 0 && Misc.getDistanceToPlayerLY(entity) >= 5) {
			List<RespawnInvasionIntel> ongoingRespawns = RespawnInvasionIntel.getOngoing();
			
			// don't despawn while any respawn events from our base are ongoing
			for (RespawnInvasionIntel intel : ongoingRespawns) {
				if (!intel.isEnding() && !intel.isEnded() && intel.getMarketFrom() == market)
					return;
			}
			
			log.info("Base " + entity.getName() + " has expired, cleaning up");
			Misc.fadeAndExpire(entity);
			endAfterDelay();
			sendUpdateIfPlayerHasIntel(null, false);
			result = new BountyResult(BountyResultType.END_OTHER, 0, null);
		}
	}

	public void makeKnown() {
		makeKnown(null);
	}
	public void makeKnown(TextPanelAPI text) {
//		entity.setDiscoverable(null);
//		entity.setSensorProfile(null);
//		entity.getDetectedRangeMod().unmodify("gen");
		
		if (getPlayerVisibleTimestamp() == null) {
			Global.getSector().getIntelManager().removeIntel(this);
			Global.getSector().getIntelManager().addIntel(this, text == null, text);
		}
	}
	
	@Override
	public float getTimeRemainingFraction() {
		float f = 1f - elapsedDays / duration;
		return f;
	}

	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		log.info(String.format("Removing respawn base at [%s]", system.getName()));
		Global.getSector().getListenerManager().removeListener(this);
		
		Global.getSector().getEconomy().removeMarket(market);
		Global.getSector().getEconomy().removeUpdateListener(this);
		Misc.removeRadioChatter(market);
		market.advance(0f);
	}
	
	@Override
	protected void notifyEnded() {
		super.notifyEnded();
	}


	protected BountyResult result = null;
	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
		if (isEnding()) return;
		
		//CampaignFleetAPI station = Misc.getStationFleet(market); // null here since it's the skeleton station at this point
		if (addedListenerTo != null && fleet == addedListenerTo) {
			Misc.fadeAndExpire(entity);
			if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE)
				destroyed = true;
			endAfterDelay();
			
			result = new BountyResult(BountyResultType.END_OTHER, 0, null);
			
			/*
			if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE && 
					param instanceof BattleAPI) {
				BattleAPI battle = (BattleAPI) param;
				if (battle.isPlayerInvolved()) {
					int payment = 0;
					if (bountyData != null) {
						payment = (int) (bountyData.baseBounty * battle.getPlayerInvolvementFraction());
					}
					if (payment > 0) {
						Global.getSector().getPlayerFleet().getCargo().getCredits().add(payment);
						
						CustomRepImpact impact = new CustomRepImpact();
						impact.delta = bountyData.repChange * battle.getPlayerInvolvementFraction();
						if (impact.delta < 0.01f) impact.delta = 0.01f;
						ReputationAdjustmentResult rep = Global.getSector().adjustPlayerReputation(
								new RepActionEnvelope(RepActions.CUSTOM, 
										impact, null, null, false, true),
										bountyData.bountyFaction.getId());
						
						result = new BountyResult(BountyResultType.END_PLAYER_BOUNTY, payment, rep);
					} else {
						result = new BountyResult(BountyResultType.END_PLAYER_NO_REWARD, 0, null);
					}
				}
			}
			*/
			
			boolean sendUpdate = DebugFlags.SEND_UPDATES_WHEN_NO_COMM ||
			 					 result.type != BountyResultType.END_OTHER ||
			 					 Global.getSector().getIntelManager().isPlayerInRangeOfCommRelay();
			sendUpdate = true;
			if (sendUpdate) {
				sendUpdateIfPlayerHasIntel(result, false);
			}
		}
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		
	}
	
	@Override
	public boolean runWhilePaused() {
		return false;
	}
		
	@Override
	public String getSortString() {
		String base = Misc.ucFirst(getFactionForUIColors().getPersonNamePrefix());
		return base + " Base";
		//return "Pirate Base";
	}
	
	public String getName() {
		String str = StringHelper.getStringAndSubstituteToken("nex_respawn", "baseIntelTitle",
				"$faction", Misc.ucFirst(getFactionForUIColors().getPersonNamePrefix()));
		
		/*
		if (getListInfoParam() == bountyData && bountyData != null) {
			return base + " Base - Bounty Posted";
		} else if (getListInfoParam() == BOUNTY_EXPIRED_PARAM) {
			return base + " Base - Bounty Expired";
		}
		
		if (result != null) {
			if (result.type == BountyResultType.END_PLAYER_BOUNTY) {
				return base + " Base - Bounty Completed";
			} else if (result.type == BountyResultType.END_PLAYER_NO_REWARD) {
				return base + " Base - Destroyed";
			}
		}
		*/
		
		String name = market.getName();
		if (isEnding()) {
			//return "Base Abandoned - " + name;
			if (destroyed)
				return str + " - " +  StringHelper.getString("exerelin_misc", "hiddenBaseDestroyed", true);
			return str + " - " +  StringHelper.getString("exerelin_misc", "hiddenBaseAbandoned", true);
		}
		if (getListInfoParam() == DISCOVERED_PARAM) {
			return str + " - " +  StringHelper.getString("exerelin_misc", "hiddenBaseDiscovered", true);
		}
		if (entity.isDiscoverable()) {
			return str + " - " + StringHelper.getString("exerelin_misc", "hiddenBaseLocationUnknown");
		}
		return str + " - " +  name;
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return market.getFaction();
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color g = Misc.getGrayColor();
		float opad = 10f;

		//info.addPara(getName(), c, 0f);
		
		//info.addSectionHeading(getName(), Alignment.MID, 0f);
		
		FactionAPI faction = market.getFaction();
		
		info.addImage(faction.getLogo(), width, 128, opad);
		
		String str = StringHelper.getString("nex_respawn", "baseIntelDesc");
		str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
		str = StringHelper.substituteToken(str, "$faction", faction.getDisplayName(), true);
		str = StringHelper.substituteToken(str, "$location", system.getNameWithLowercaseType(), true);
		
		info.addPara(str, opad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
		
		if (!entity.isDiscoverable()) {
			
		} else {
			info.addPara(StringHelper.getString("nex_respawn", "baseIntelDescUnknown"), opad);
		}
		
		/*
		info.addSectionHeading("Recent events", 
							   faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
				
		if (bountyData != null) {
			info.addPara(Misc.ucFirst(bountyData.bountyFaction.getDisplayNameWithArticle()) + " " +
					bountyData.bountyFaction.getDisplayNameHasOrHave() + 
					" posted a bounty for the destruction of this base.",
					opad, bountyData.bountyFaction.getBaseUIColor(), 
					bountyData.bountyFaction.getDisplayNameWithArticleWithoutArticle());
			
			if (result != null && result.type == BountyResultType.END_PLAYER_BOUNTY) {
				info.addPara("You have successfully completed this bounty.", opad);
			}
			
			addBulletPoints(info, ListInfoMode.IN_DESC);
		}
		
		if (result != null) {
			if (result.type == BountyResultType.END_PLAYER_NO_REWARD) {
				info.addPara("You have destroyed this base.", opad);				
			} else if (result.type == BountyResultType.END_OTHER) {
				info.addPara("It is rumored that this base is no longer operational.", opad);				
			}
		}
		*/

	}
	
	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "respawn_base");
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		/*
		if (bountyData != null) {
			tags.add(Tags.INTEL_BOUNTY);
		}
		*/
		tags.add(Tags.INTEL_EXPLORATION);
		
		tags.add(market.getFactionId());
		/*
		if (bountyData != null) {
			tags.add(bountyData.bountyFaction.getId());
		}
		*/
		return tags;
	}

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		//return market.getPrimaryEntity();
		if (market.getPrimaryEntity().isDiscoverable()) {
			return system.getCenter();
		}
		return market.getPrimaryEntity();
	}
	
	
	protected String generateName() {
		MarkovNames.loadIfNeeded();
		
		MarkovNameResult gen = null;
		for (int i = 0; i < 10; i++) {
			gen = MarkovNames.generate(null);
			if (gen != null) {
				String test = gen.name;
				if (test.toLowerCase().startsWith("the ")) continue;
				String p = pickPostfix();
				if (p != null && !p.isEmpty()) {
					test += " " + p;
				}
				if (test.length() > 22) continue;
				
				return test;
			}
		}
		return null;
	}
	
	protected String pickPostfix() {
		WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
		post.add("Asylum");
		post.add("Astrome");
		post.add("Barrage");
		post.add("Briganderie");
		post.add("Camp");
		post.add("Cover");
		post.add("Citadel");
		post.add("Den");
		post.add("Donjon");
		post.add("Depot");
		post.add("Fort");
		post.add("Freehold");
		post.add("Freeport");
		post.add("Freehaven");
		post.add("Free Orbit");
		post.add("Galastat");
		post.add("Garrison");
		post.add("Harbor");
		post.add("Haven");
		post.add("Headquarters");
		post.add("Hideout");
		post.add("Hideaway");
		post.add("Hold");
		post.add("Lair");
		post.add("Locus");
		post.add("Main");
		post.add("Mine Depot");
		post.add("Nexus");
		post.add("Orbit");
		post.add("Port");
		post.add("Post");
		post.add("Presidio");
		post.add("Prison");
		post.add("Platform");
		post.add("Corsairie");
		post.add("Refuge");
		post.add("Retreat");
		post.add("Refinery");
		post.add("Shadow");
		post.add("Safehold");
		post.add("Starhold");
		post.add("Starport");
		post.add("Stardock");
		post.add("Sanctuary");
		post.add("Station");
		post.add("Spacedock");
		post.add("Tertiary");
		post.add("Terminus");
		post.add("Terminal");
		post.add("Tortuga");
		post.add("Ward");
		post.add("Warsat");
		return post.pick();
	}

	@Override
	public void commodityUpdated(String commodityId) {
		CommodityOnMarketAPI com = market.getCommodityData(commodityId);
		int curr = 0;
		String modId = market.getId();
		StatMod mod = com.getAvailableStat().getFlatStatMod(modId);
		if (mod != null) {
			curr = Math.round(mod.value);
		}
		
		int a = com.getAvailable() - curr;
		int d = com.getMaxDemand();
		if (d > a) {
			//int supply = Math.max(1, d - a - 1);
			int supply = Math.max(1, d - a);
			com.getAvailableStat().modifyFlat(modId, supply, 
					StringHelper.getString("exerelin_misc", "hiddenBaseSupply"));
		}
	}

	@Override
	public void economyUpdated() {

		float fleetSizeBonus = 0.5f;
		float qualityBonus = 0.4f;
		int light = 2;
		int medium = 2;
		int heavy = 1;
		
		String developmentLevel = StringHelper.getString("exerelin_misc", "hiddenBaseDevelopmentLevel");
		market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).
									modifyFlatAlways(market.getId(), qualityBonus,
									developmentLevel);
		
		market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlatAlways(market.getId(),
									fleetSizeBonus, 
		  							developmentLevel);
		
		
		String modId = market.getId();
		market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(modId, light);
		market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(modId, medium);
		market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).modifyFlat(modId, heavy);
	}

	@Override
	public boolean isEconomyListenerExpired() {
		return isEnded();
	}
	
	public MarketAPI getMarket() {
		return market;
	}

	/*
	protected void setBounty() {
		bountyData = new BaseBountyData();
		float base = 100000f;
		switch (tier) {
		case TIER_1_1MODULE:
			base = Global.getSettings().getFloat("pirateBaseBounty1");
			bountyData.repChange = 0.02f;
			break;
		case TIER_2_1MODULE:
			base = Global.getSettings().getFloat("pirateBaseBounty2");
			bountyData.repChange = 0.05f;
			break;
		case TIER_3_2MODULE:
			base = Global.getSettings().getFloat("pirateBaseBounty3");
			bountyData.repChange = 0.06f;
			break;
		case TIER_4_3MODULE:
			base = Global.getSettings().getFloat("pirateBaseBounty4");
			bountyData.repChange = 0.07f;
			break;
		case TIER_5_3MODULE:
			base = Global.getSettings().getFloat("pirateBaseBounty5");
			bountyData.repChange = 0.1f;
			break;
		}
		
		bountyData.baseBounty = base * (0.9f + (float) Math.random() * 0.2f);
		
		bountyData.baseBounty = (int)(bountyData.baseBounty / 10000) * 10000;
		
		
		WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<FactionAPI>();
		for (MarketAPI curr : Global.getSector().getEconomy().getMarkets(target)) {
			if (curr.getFaction().isPlayerFaction()) continue;
			if (affectsMarket(curr)) {
				picker.add(curr.getFaction(), (float) Math.pow(2f, curr.getSize()));
			}
		}
		
		FactionAPI faction = picker.pick();
		if (faction == null) {
			bountyData = null;
			return;
		}
		
		bountyData.bountyFaction = faction;
		bountyData.bountyDuration = 180f;
		bountyData.bountyElapsedDays = 0f;
		
		Misc.makeImportant(entity, "baseBounty");
		
		sentBountyUpdate = false;
//		makeKnown();
//		sendUpdateIfPlayerHasIntel(bountyData, false);
	}
	
	protected boolean sentBountyUpdate = false;
	protected void endBounty() {
		sendUpdateIfPlayerHasIntel(BOUNTY_EXPIRED_PARAM, false);
		bountyData = null;
		sentBountyUpdate = false;
		Misc.makeUnimportant(entity, "baseBounty");
	}
	*/
	
	public SectorEntityToken getEntity() {
		return entity;
	}
	
	public static RespawnBaseIntel generateBase(String factionId) {
		StarSystemAPI system = pickSystemForBase(new Random());
		if (system != null) {
			log.info("Spawning respawn base for faction " + factionId);
			RespawnBaseIntel base = new RespawnBaseIntel(system, factionId);
			if (!base.isEnding() && !base.isEnded())	// make sure spawn worked
				return base;
		}
		return null;
	}
	
	// basically same as PirateBaseManager.pickSystemForPirateBase()
	protected static StarSystemAPI pickSystemForBase(Random random) {
		WeightedRandomPicker<StarSystemAPI> far = new WeightedRandomPicker<StarSystemAPI>(random);
		WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<StarSystemAPI>(random);
		
		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			float days = Global.getSector().getClock().getElapsedDaysSince(system.getLastPlayerVisitTimestamp());
			if (days < 45f) continue;
			
			float weight = 0f;
			if (system.hasTag(Tags.THEME_MISC_SKIP)) {
				weight = 1f;
			} else if (system.hasTag(Tags.THEME_MISC)) {
				weight = 3f;
			} else if (system.hasTag(Tags.THEME_REMNANT_NO_FLEETS)) {
				weight = 3f;
			} else if (system.hasTag(Tags.THEME_RUINS)) {
				weight = 5f;
			} else if (system.hasTag(Tags.THEME_CORE_UNPOPULATED)) {
				weight = 1f;
			}
			if (weight <= 0f) continue;
			
			float usefulStuff = system.getCustomEntitiesWithTag(Tags.OBJECTIVE).size() +
								system.getCustomEntitiesWithTag(Tags.STABLE_LOCATION).size();
			if (usefulStuff <= 0) continue;
			
			if (Misc.hasPulsar(system)) continue;
			if (Misc.getMarketsInLocation(system).size() > 0) continue;
			
			if (getLocsPicker(system).isEmpty())
				continue;
			
			float dist = system.getLocation().length();
			
			
			
//			float distMult = 1f - dist / 20000f;
//			if (distMult > 1f) distMult = 1f;
//			if (distMult < 0.1f) distMult = 0.1f;
			
			float distMult = 1f;
			
			if (dist > 36000f) {
				far.add(system, weight * usefulStuff * distMult);
			} else {
				picker.add(system, weight * usefulStuff * distMult);
			}
		}
		
		if (picker.isEmpty()) {
			picker.addAll(far);
		}
		
		return picker.pick();
	}
	
	protected static WeightedRandomPicker<EntityLocation> getLocsPicker(StarSystemAPI system) 
	{
		LinkedHashMap<LocationType, Float> weights = new LinkedHashMap<LocationType, Float>();
		weights.put(LocationType.IN_ASTEROID_BELT, 10f);
		weights.put(LocationType.IN_ASTEROID_FIELD, 10f);
		weights.put(LocationType.IN_RING, 10f);
		weights.put(LocationType.IN_SMALL_NEBULA, 10f);
		weights.put(LocationType.GAS_GIANT_ORBIT, 10f);
		weights.put(LocationType.PLANET_ORBIT, 10f);
		
		return BaseThemeGenerator.getLocations(null, system, null, 100f, weights);
	}
}
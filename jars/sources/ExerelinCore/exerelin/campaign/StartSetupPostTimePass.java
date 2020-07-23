package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.tutorial.GalatianAcademyStipend;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.world.templars.TEM_Antioch;
import exerelin.world.ExerelinCorvusLocations;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinFactionConfig.SpecialItemSet;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import java.awt.Color;
import java.util.Random;
import org.lwjgl.util.vector.Vector2f;

public class StartSetupPostTimePass {
	
	public static void execute()
	{
		SectorAPI sector = Global.getSector();
		if (Global.getSector().isInNewGameAdvance()) return;
		
		SectorEntityToken entity = null;
		String factionId = PlayerFactionStore.getPlayerFactionIdNGC();
		final String selectedFactionId = factionId;	// can't be changed by config
		String factionIdForSpawnLoc = factionId;
		
		FactionAPI myFaction = sector.getFaction(factionId);
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		
		// assign officers
		int numOfficers = ExerelinSetupData.getInstance().numStartingOfficers;
		for (int i=0; i<numOfficers; i++)
		{
			int level = (numOfficers - i)*2 - 1;
			PersonAPI officer = OfficerManagerEvent.createOfficer(myFaction, level, true);
			playerFleet.getFleetData().addOfficer(officer);
			
			// assign officers to ships
			for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy())
			{
				PersonAPI cap = member.getCaptain();
				if ((cap == null || cap.isDefault()) && !member.isFighterWing())
				{
					member.setCaptain(officer);
					break;
				}
				else if (cap == officer)
					break;
			}
		}
		
		// rename ships
		for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy())
		{
			member.setShipName(myFaction.pickRandomShipName());
		}
		
		boolean freeStart = SectorManager.getManager().isFreeStart();
		
		// spawn as a different faction if config says we should
		// for Blade Breakers etc.
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (conf.spawnAsFactionId != null && !conf.spawnAsFactionId.isEmpty())
		{
			factionId = conf.spawnAsFactionId;
			if (freeStart) {	// Blade Breaker start: use BB start relations
				NexUtilsReputation.syncFactionRelationshipsToPlayer();
			}
			else {	// Lion's Guard start: use Diktat start relations
				NexUtilsReputation.syncPlayerRelationshipsToFaction(factionId);
				factionIdForSpawnLoc = factionId;
			}
		}
		
		handleStartLocation(sector, playerFleet, factionIdForSpawnLoc);
		
		// commission
		// now handled in ExerelinNewGameSetup
		if (!factionId.equals(Factions.PLAYER)) {
			//ExerelinUtilsFaction.grantCommission(factionId);
		}
		// make own faction start's first market player-owned
		else if (!SectorManager.getManager().isCorvusMode() && !freeStart && entity != null) {
			entity.getMarket().setPlayerOwned(true);
		}
		
		// Galatian stipend
		if (!SectorManager.getManager().isHardMode() && !Misc.isSpacerStart()) {
			new GalatianAcademyStipend();
		}
		
		// Starting blueprints
		if (selectedFactionId.equals(Factions.PLAYER) && freeStart) {
			// no free special items on free start
		}
		else {
			for (SpecialItemSet set : conf.startSpecialItems) {
				set.pickItemsAndAddToCargo(Global.getSector().getPlayerFleet().getCargo(), 
						StarSystemGenerator.random);
			}
		}
	}
	
	public static void handleStartLocation(SectorAPI sector, CampaignFleetAPI playerFleet, String factionId) 
	{
		SectorEntityToken entity = null;
				
		if (Global.getSector().getMemoryWithoutUpdate().contains("$nex_startLocation")) {
			entity = Global.getSector().getEntityById(Global.getSector().getMemoryWithoutUpdate().getString("$nex_startLocation"));
		}
		else if (ExerelinSetupData.getInstance().randomStartLocation) {
			WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>();
			picker.setRandom(new Random(ExerelinUtils.getStartingSeed()));
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
			{
				if (!market.getFaction().getId().equals(factionId))
					continue;
				if (market.getContainingLocation().hasTag("do_not_respawn_player_in"))
					continue;
				picker.add(market.getPrimaryEntity());
			}
			entity = picker.pick();
		}
		else if (SectorManager.getCorvusMode())
		{
			// moves player fleet to a suitable location; e.g. Avesta for Association
			String homeEntity = null;
			ExerelinCorvusLocations.SpawnPointEntry spawnPoint = ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
			if (spawnPoint != null)
			{
				homeEntity = spawnPoint.entityId;
				if (homeEntity != null)
					entity = sector.getEntityById(homeEntity);
			}
		}
		else {
			if (ExerelinConfig.enableAntioch && factionId.equals("templars"))
				entity = TEM_Antioch.getAscalon();
			else if (!SectorManager.getManager().isFreeStart())
				entity = SectorManager.getHomeworld();
		}
		
		// couldn't find an more suitable start location, pick any market that's not unfriendly to us
		if (entity == null) {
			WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>();
			picker.setRandom(new Random(ExerelinUtils.getStartingSeed()));
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
			{
				if (market.getFaction().isAtBest(Factions.PLAYER, RepLevel.INHOSPITABLE))
					continue;
				if (market.getContainingLocation().hasTag("do_not_respawn_player_in"))
					continue;
				picker.add(market.getPrimaryEntity());
			}
			entity = picker.pick();
		}
		
		if (entity != null)
		{
			sendPlayerFleetToLocation(playerFleet, entity);
		}
	}
	
	/**
	 * Sends the player fleet to the intended starting star system, orbiting the home market.
	 * Also grants the starting commission and unlocks storage.
	 * @param playerFleet
	 * @param entity Home planet/station
	 */
	public static void sendPlayerFleetToLocation(CampaignFleetAPI playerFleet, SectorEntityToken entity) 
	{
		playerFleet.getContainingLocation().removeEntity(playerFleet);
		entity.getContainingLocation().addEntity(playerFleet);
		Global.getSector().setCurrentLocation(entity.getContainingLocation());
		Vector2f loc = entity.getLocation();
		playerFleet.setLocation(loc.x, loc.y);
		
		if (!SectorManager.getManager().isFreeStart())
		{
			// unlock storage
			MarketAPI homeMarket = entity.getMarket();
			if (homeMarket != null)
			{
				SubmarketAPI storage = homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE);
				if (storage != null)
				{
					StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
					if (plugin != null)
						plugin.setPlayerPaidToUnlock(true);
				}
				if (homeMarket.isPlayerOwned())
					homeMarket.setAdmin(Global.getSector().getPlayerPerson());
			}
		}
	}
}

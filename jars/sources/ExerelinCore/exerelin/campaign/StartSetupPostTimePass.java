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
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.world.ExerelinCorvusLocations;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import java.awt.Color;
import java.util.Random;
import org.lwjgl.util.vector.Vector2f;

public class StartSetupPostTimePass {
	
	public static void execute()
	{
		SectorAPI sector = Global.getSector();
		if (Global.getSector().isInNewGameAdvance()) return;
		
		SectorEntityToken entity = null;
		String factionId = PlayerFactionStore.getPlayerFactionId();
		FactionAPI myFaction = sector.getFaction(factionId);
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		if (SectorManager.getCorvusMode())
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
			
			// check that all factions support Corvus mode; warn player if not
			int numIncompatibles = 0;
			for (FactionAPI faction : sector.getAllFactions())
			{
				if (!ExerelinUtilsFaction.isCorvusCompatible(faction.getId(), true))
				{
					Global.getLogger(StartSetupPostTimePass.class).warn("Faction " + faction.getDisplayName() + " does not support non-random sector mode!");
					numIncompatibles++;
				}
			}
			if (numIncompatibles > 0)
			{
				Color color = Misc.getHighlightColor();
				Color color2 = Color.RED;
				CampaignUIAPI ui = sector.getCampaignUI();
				ui.addMessage("You are using " + numIncompatibles + " mod faction(s) that do not support non-random sector mode!", color, numIncompatibles+"", color2);
				ui.addMessage("See starsector.log for details", color);
			}
		}
		else 
		{
			if (!SectorManager.getFreeStart())
				entity = SectorManager.getHomeworld();
			else
			{
				WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>();
				picker.setRandom(new Random(ExerelinUtils.getStartingSeed()));
				for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
				{
					if (market.getFaction().isAtBest(Factions.PLAYER, RepLevel.INHOSPITABLE))
						continue;
					picker.add(market.getPrimaryEntity());
				}
				entity = picker.pick();
			}
		}
		
		if (entity != null)
			sendPlayerFleetToLocation(playerFleet, entity);
		
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
	}
	
	/**
	 * Sends the player fleet to the intended starting star system, orbiting the home market
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
		}
		
		// commission
		String factionId = PlayerFactionStore.getPlayerFactionId();		
		if (!PlayerFactionStore.getPlayerFactionId().equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			if (!entity.getFaction().isNeutralFaction())
				ExerelinUtilsFaction.grantCommission(entity);
			else if (ExerelinUtilsFaction.isExiInCorvus(factionId))
				ExerelinUtilsFaction.grantCommission(Global.getSector().getStarSystem("Tasserus").getEntityById("exigency_anomaly"));
		}
	}
}

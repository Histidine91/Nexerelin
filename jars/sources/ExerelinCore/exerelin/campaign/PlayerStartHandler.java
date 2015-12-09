package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.world.ExerelinCorvusLocations;
import exerelin.utilities.ExerelinUtilsFaction;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class PlayerStartHandler {
	
	public static void execute()
	{
		SectorEntityToken entity = null;
		String factionId = PlayerFactionStore.getPlayerFactionId();
		if (SectorManager.getCorvusMode())
		{
			// moves player fleet to a suitable location; e.g. Avesta for Association
			ExerelinCorvusLocations.SpawnPointEntry spawnPoint = ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
			String homeEntity = null;
			if (Global.getSector().isInNewGameAdvance()) return;
			if (ExerelinUtilsFaction.isCorvusCompatible(factionId, false) && spawnPoint != null)
			{
				homeEntity = spawnPoint.entityId;
				if (homeEntity != null)
					entity = Global.getSector().getEntityById(homeEntity);
			}
			if (entity != null)
			{
				Vector2f loc = entity.getLocation();
				Global.getSector().getPlayerFleet().setLocation(loc.x, loc.y);
				MarketAPI homeMarket = entity.getMarket();
				if (homeMarket != null)
				{
					// unlock storage
					SubmarketAPI storage = homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE);
					if (storage != null)
					{
						StoragePlugin plugin = (StoragePlugin)homeMarket.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
						if (plugin != null)
							plugin.setPlayerPaidToUnlock(true);
					}
				}
			}
			// check that all factions support Corvus mode; warn player if not
			int numIncompatibles = 0;
			for (FactionAPI faction : Global.getSector().getAllFactions())
			{
				if (!ExerelinUtilsFaction.isCorvusCompatible(faction.getId(), true))
				{
					Global.getLogger(PlayerStartHandler.class).warn("Faction " + faction.getDisplayName() + " does not support Corvus mode!");
					numIncompatibles++;
				}
			}
			if (numIncompatibles > 0)
			{
				Color color = Misc.getHighlightColor();
				Color color2 = Color.RED;
				CampaignUIAPI ui = Global.getSector().getCampaignUI();
				ui.addMessage("You are using " + numIncompatibles + " mod faction(s) that do not support Corvus mode!", color, numIncompatibles+"", color2);
				ui.addMessage("See starsector.log for details", color);
			}
			
		}
		else if (!SectorManager.getFreeStart())
		{
			entity = SectorManager.getHomeworld();
			Vector2f loc = entity.getLocation();
			Global.getSector().getPlayerFleet().setLocation(loc.x, loc.y);
		}
		
		if (!factionId.equals("player_npc"))
		{
			if (entity != null && !entity.getFaction().isNeutralFaction())
				ExerelinUtilsFaction.grantCommission(entity);
		}
	}
}

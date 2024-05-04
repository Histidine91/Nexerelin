package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import exerelin.ExerelinConstants;
import exerelin.utilities.InvasionListener;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles changing of market descriptions when a market is captured.
 */
public class MarketDescChanger implements InvasionListener, ColonyDecivListener, PlayerColonizationListener {
	
	public static final List<DescUpdateEntry> DESCRIPTIONS = new ArrayList<>();
	public static final Map<String, List<DescUpdateEntry>> DESCRIPTIONS_BY_ENTITY_ID = new HashMap<>();
	public static final String CSV_PATH = "data/config/exerelin/captured_planet_descs.csv";
	
	public static Logger log = Global.getLogger(MarketDescChanger.class);
	public static MarketDescChanger currInstance;
	public static boolean loadedDefs = false;
		
	public static void loadDefs() {
		if (loadedDefs) return;
		loadedDefs = true;
		String lastId = null;
		try {
			JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("id", CSV_PATH, ExerelinConstants.MOD_ID);
			for (int i = 0; i < csv.length(); i++) {
				JSONObject row = csv.getJSONObject(i);
				String id = row.getString("id");
				if (id == null || id.isEmpty()) continue;
				lastId = id;
				//log.info("Adding captured planet desc row " + id);
				
				String entityId = row.getString("entity");
				String factionId = row.getString("faction");
				String descId = row.getString("desc id");
				if (descId.isEmpty()) descId = null;
				boolean isDefault = row.optBoolean("default", false);
				
				DescUpdateEntry desc = new DescUpdateEntry(entityId, factionId, descId, isDefault);
				DESCRIPTIONS.add(desc);
				if (!DESCRIPTIONS_BY_ENTITY_ID.containsKey(entityId)) {
					DESCRIPTIONS_BY_ENTITY_ID.put(entityId, new ArrayList<DescUpdateEntry>());
				}
				DESCRIPTIONS_BY_ENTITY_ID.get(entityId).add(desc);
			}
		} catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load captured planet desc, last ID " + lastId, ex);
		}
	}
	
	public MarketDescChanger() {
		loadDefs();
	}
	
	public MarketDescChanger registerInstance() {
		currInstance = this;
		return this;
	}
	
	public static MarketDescChanger getInstance() {
		return currInstance;
	}
	
	public static void setEntityDescId(SectorEntityToken entity, String factionId) 
	{
		String entityId = entity.getId();
		if (DESCRIPTIONS_BY_ENTITY_ID.containsKey(entityId))
		{
			List<DescUpdateEntry> descs = DESCRIPTIONS_BY_ENTITY_ID.get(entityId);
			for (DescUpdateEntry desc : descs) {
				if (!desc.factionId.equals(factionId)) continue;
				entity.setCustomDescriptionId(desc.descId);
				log.info("Updating entity " + entityId + " description to " + desc.descId);
				return;
			}
			
			for (DescUpdateEntry desc : descs) {
				if (desc.isDefault) {
					entity.setCustomDescriptionId(desc.descId);
					return;
				}
			}
		}
	}

	public static void setEntityDescsForMarket(MarketAPI market, String factionId) {
		for (SectorEntityToken linked : market.getConnectedEntities()) {
			setEntityDescId(linked, factionId);
		}
	}

	@Override
	public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, 
			Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {
		
	}
	
	@Override
	public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, 
			MarketAPI defender, float atkStr, float defStr) {
		
	}
	
	@Override
	public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, 
			MarketAPI market, float numRounds, boolean success) {
		
	}
	
	@Override
	public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, 
			boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) 
	{
		String factionId = newOwner.getId();
		setEntityDescsForMarket(market, factionId);
	}

	@Override
	public void reportPlayerColonizedPlanet(PlanetAPI planet) {
		setEntityDescsForMarket(planet.getMarket(), Factions.PLAYER);
	}

	@Override
	public void reportPlayerAbandonedColony(MarketAPI colony) {
		setEntityDescsForMarket(colony, Factions.NEUTRAL);
	}

	@Override
	public void reportColonyAboutToBeDecivilized(MarketAPI market, boolean fullyDestroyed) {

	}

	@Override
	public void reportColonyDecivilized(MarketAPI market, boolean fullyDestroyed) {
		reportMarketTransfered(market, Global.getSector().getFaction(Factions.NEUTRAL), null, false,
				false, null, 0f);
	}

	public static class DescUpdateEntry {
		public final String entityId;
		public final String factionId;
		public final String descId;
		public final boolean isDefault;
		
		public DescUpdateEntry(String entityId, String factionId, String descId, boolean isDefault) 
		{
			this.entityId = entityId;
			this.factionId = factionId;
			this.descId = descId;
			this.isDefault = isDefault;
		}
	}
}

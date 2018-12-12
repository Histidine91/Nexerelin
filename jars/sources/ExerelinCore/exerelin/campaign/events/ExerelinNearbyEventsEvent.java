package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.impl.campaign.events.nearby.NearbyEventsEvent;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.FleetLog;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinUtilsFaction;

// same as vanilla except checks to make sure there are pirate markets before spawning pirate calls
// see http://fractalsoftworks.com/forum/index.php?topic=12680.0
@Deprecated
public class ExerelinNearbyEventsEvent extends NearbyEventsEvent {
	
	@Override
	protected void maybeSpawnDistressCall() {
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		if (!playerFleet.isInHyperspace()) return;

		
		WeightedRandomPicker<StarSystemAPI> systems = new WeightedRandomPicker<StarSystemAPI>();
		OUTER: for (StarSystemAPI system : Misc.getNearbyStarSystems(playerFleet, Global.getSettings().getFloat("distressCallEventRangeLY"))) {
			
			if (skipForDistressCalls.contains(system.getId())) continue;
			
			if (system.hasPulsar()) continue;
			
			float sincePlayerVisit = system.getDaysSinceLastPlayerVisit();
			if (sincePlayerVisit < DISTRESS_MIN_SINCE_PLAYER_IN_SYSTEM) {
				continue;
			}
			
			boolean validTheme = false;
			for (String tag : system.getTags()) {
				if (distressCallAllowedThemes.contains(tag)) {
					validTheme = true;
					break;
				}
			}
			if (!validTheme) continue;
			
			for (CampaignFleetAPI fleet : system.getFleets()) {
				if (!fleet.getFaction().isHostileTo(Factions.INDEPENDENT)) continue OUTER;
			}
			
			skipForDistressCalls.add(system.getId(), DISTRESS_ALREADY_WAS_NEARBY_TIMEOUT);
			systems.add(system);
		}
		
		float p = systems.getItems().size() * DISTRESS_PROB_PER_SYSTEM;
		if (p > DISTRESS_MAX_PROB) p = DISTRESS_MAX_PROB;
		if ((float) Math.random() >= p && !TEST_MODE) return;
		
		
		StarSystemAPI system = systems.pick();
		if (system == null) return;
		
		skipForDistressCalls.set(system.getId(), DISTRESS_REPEAT_TIMEOUT);
		
	
		WeightedRandomPicker<DistressEventType> picker = new WeightedRandomPicker<DistressEventType>();
		picker.add(DistressEventType.NORMAL, 10f);
		if (ExerelinUtilsFaction.hasAnyMarkets(Factions.PIRATES))
		{
			picker.add(DistressEventType.PIRATE_AMBUSH, 10f);
			picker.add(DistressEventType.PIRATE_AMBUSH_TRAP, 10f);
		}
		picker.add(DistressEventType.DERELICT_SHIP, 10f);

		DistressEventType type = picker.pick();
		
		if (type == DistressEventType.NORMAL || TEST_MODE) {
			generateDistressCallNormal(system);
		} else if (type == DistressEventType.PIRATE_AMBUSH) {
			generateDistressCallAmbush(system);
		} else if (type == DistressEventType.PIRATE_AMBUSH_TRAP) {
			generateDistressCallAmbushTrap(system);
		} else if (type == DistressEventType.DERELICT_SHIP) {
			generateDistressDerelictShip(system);
		}
		
		CommMessageAPI message = FleetLog.beginEntry("Distress Call", system.getCenter());
		message.setSmallIcon(Global.getSettings().getSpriteName("intel_categories", "events"));
		message.getSection1().addPara("You receive a distress call from the nearby " + system.getNameWithLowercaseType());
		message.getSection1().addPara("There's no additional information, but that's not surprising - a typical fleet doesn't carry the equipment to broadcast a full-fledged data stream into hyperspace.");
		FleetLog.addToLog(message, null);
	}
	
}

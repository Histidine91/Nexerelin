package exerelin.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.MiscEventsManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j;

/**
 * Workaround for the bug where allied fleets can get their commanders booted off their ships.
 * See https://fractalsoftworks.com/forum/index.php?topic=5061.msg368948#msg368948
 */
@Log4j
public class RestoreCommanderPlugin extends BaseEveryFrameCombatPlugin {
	
	public void init(CombatEngineAPI engine) {
		try {
			Map<CampaignFleetAPI, Object[]> fleetsToCommanders = new HashMap<>();

			CombatFleetManagerAPI man = engine.getFleetManager(FleetSide.PLAYER);
			List<FleetMemberAPI> members = man.getDeployedCopy();
			members.addAll(engine.getFleetManager(FleetSide.PLAYER).getReservesCopy());

			for (FleetMemberAPI member : members) {
				if (member.getFleetData() == null) {
					//log.info("Fleet data is null, skipping");
					continue;
				}
				CampaignFleetAPI fleet = member.getFleetData().getFleet();
				if (fleet == null) continue;
				if (fleetsToCommanders.containsKey(fleet)) continue;

				MemoryAPI mem = fleet.getMemoryWithoutUpdate();
				if (!mem.contains(MiscEventsManager.MEMORY_KEY_FLAGSHIP_WORKAROUND)) {
					fleetsToCommanders.put(fleet, null);
					continue;
				}


				Object[] data = (Object[])mem.get(MiscEventsManager.MEMORY_KEY_FLAGSHIP_WORKAROUND);
				fleetsToCommanders.put(fleet, data);
				//log.info(String.format("Preparing restoration data for %s", fleet.getName()));
			}

			for (CampaignFleetAPI fleet : fleetsToCommanders.keySet()) {
				Object[] obj = fleetsToCommanders.get(fleet);
				if (obj == null) continue;

				FleetMemberAPI flag = (FleetMemberAPI)obj[0];
				PersonAPI captain = (PersonAPI)obj[1];

				log.info(String.format("Restoring captain %s on %s", captain.getNameString(), flag.getShipName()));
				ShipAPI ship = man.getShipFor(flag);
				if (ship != null && ship.getCaptain() != captain) {
					ship.setCaptain(captain);
				}
				if (flag.getCaptain() != captain) {
					flag.setCaptain(captain);
				}

				//fleet.getMemoryWithoutUpdate().unset(MiscEventsManager.MEMORY_KEY_FLAGSHIP_WORKAROUND);
			}
		} catch (Exception ex) {
			String warn = "[Nex] Error in ally commander restore plugin";
			Global.getCombatEngine().getCombatUI().addMessage(1, Misc.getNegativeHighlightColor(), warn);
			log.error(warn, ex);
		}
	}	
}

package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;

import java.util.List;

/**
 * Custom plugin for unique behavior in the Salvation boss battle.
 */
public class SalvationBattleCreationPlugin extends BattleCreationPluginImpl {

	@Override
	public void initBattle(BattleCreationContext context, MissionDefinitionAPI loader) {
		Global.getLogger(this.getClass()).info("Applying Salvation BCP");
		context.objectivesAllowed = true;
		super.initBattle(context, loader);
	}

	@Override
	public void afterDefinitionLoad(CombatEngineAPI engine) {
		super.afterDefinitionLoad(engine);
		
		FleetGoal playerGoal = context.getPlayerGoal();
		FleetGoal enemyGoal = context.getOtherGoal();
		if (playerGoal == FleetGoal.ESCAPE || enemyGoal == FleetGoal.ESCAPE) return;
		
		engine.addPlugin(new SabotageStation());
	}	
	
	public static class SabotageStation extends BaseEveryFrameCombatPlugin {
		protected CombatEngineAPI engine;
		protected boolean ran = false;

		protected void ruinCR(ShipAPI ship) {
			float cr = ship.getCurrentCR();
			cr *= 0.2f;
			ship.setCRAtDeployment(cr);
			ship.setCurrentCR(cr);
			CombatReadinessPlugin plugin = Global.getSettings().getCRPlugin();
			plugin.applyCRToStats(cr, ship.getMutableStats(), ship.getHullSize());
			plugin.applyCRToShip(cr, ship);
		}
		
		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			if (engine == null) return;
			
			final CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
			List<DeployedFleetMemberAPI> deployed = cfm.getStations();
			for (DeployedFleetMemberAPI dfm : deployed) {
				ShipAPI ship = dfm.getShip();
				if (ship == null) continue;
				if (ship.getChildModulesCopy().isEmpty()) continue;
				ruinCR(ship);
				for (ShipAPI module : dfm.getShip().getChildModulesCopy()) {
					ruinCR(module);
				}
				ran = true;
			}
			if (ran) {
				engine.removePlugin(this);
			}
		}
		
		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
		}
	}
}

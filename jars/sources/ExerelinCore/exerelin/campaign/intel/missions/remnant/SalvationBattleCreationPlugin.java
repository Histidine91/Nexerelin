package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;

/**
 * Custom plugin for unique behavior in the Salvation boss battle.
 */
public class SalvationBattleCreationPlugin extends BattleCreationPluginImpl {

	@Override
	public void initBattle(BattleCreationContext context, MissionDefinitionAPI loader) {
		//Global.getLogger(this.getClass()).info("Applying Salvation BCP");
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
		engine.addPlugin(new ToweringChatterPlugin());
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

	public static class ToweringChatterPlugin extends BaseEveryFrameCombatPlugin {
		public static final int NUM_LINES = 4;
		public static final Color MAUVE = new Color(212, 115, 212, 255);

		protected CombatEngineAPI engine;
		protected int linesRead = 0;
		protected IntervalUtil interval = new IntervalUtil(27, 33);

		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			if (engine == null) return;
			if (engine.isPaused()) return;

			ShipAPI flag = getToweringShip();
			if (flag == null) return;
			if (!flag.isAlive()) {
				engine.removePlugin(this);
				return;
			}

			interval.advance(amount);
			if (interval.intervalElapsed()) {
				linesRead++;

				String line = RemnantQuestUtils.getString("salvation_toweringChatter" + linesRead);
				printMessage(flag.getFleetMember(), line, MAUVE, true);

				if (linesRead >= NUM_LINES) {
					engine.removePlugin(this);
				}
			}
		}

		protected ShipAPI getToweringShip() {
			return engine.getFleetManager(FleetSide.ENEMY).getShipFor(RemnantQuestUtils.getOrCreateTowering());
		}

		public void printMessage(FleetMemberAPI member, String message, Color textColor, boolean withColon) {
			String name = getShipName(member, true, false);
			if (withColon) {
				engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, Misc.getTextColor(), ": ", textColor, message);
			} else {
				engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, textColor, message);
			}
		}

		protected Color getShipNameColor(FleetMemberAPI member) {
			if (member.isAlly()) return Misc.getHighlightColor();
			ShipAPI ship = getShipForMember(member);
			if (ship != null && ship.isAlly()) return Misc.getHighlightColor();

			if (ship.getOwner() == 1) return Global.getSettings().getColor("textEnemyColor");
			return Global.getSettings().getColor("textFriendColor");
		}

		protected ShipAPI getShipForMember(FleetMemberAPI member)
		{
			ShipAPI ship = engine.getFleetManager(FleetSide.PLAYER).getShipFor(member);
			if (ship == null) ship = engine.getFleetManager(FleetSide.ENEMY).getShipFor(member);
			return ship;
		}

		protected String getShipName(FleetMemberAPI member, boolean includeClass, boolean useOfficerName)
		{
			String shipName = "";
			if (member.isFighterWing()) {
				shipName = member.getHullSpec().getHullName();
				// not proper way for some languages but good enough in that we're never actually using it
				if (includeClass) shipName += " " + StringHelper.getString("fighterWingShort");
			}
			else {
				if (useOfficerName && !member.getCaptain().isDefault()) {
					return member.getCaptain().getNameString();
				}
				shipName = member.getShipName();
				if (includeClass) shipName += " (" + member.getHullSpec().getHullNameWithDashClass() + ")";
			}

			return shipName;
		}

		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
		}
	}
}

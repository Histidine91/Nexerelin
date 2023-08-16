package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
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
		public static final int NUM_LINES_DEATH = 2;
		public static final Color MAUVE = new Color(212, 115, 212, 255);

		protected CombatEngineAPI engine;
		protected IntervalUtil interval = new IntervalUtil(42, 48);
		protected IntervalUtil intervalDeath = new IntervalUtil(6f, 7f);
		protected int linesRead = 0;
		protected int deathLinesRead = 0;
		protected boolean tauntRead = Global.getSector().getMemoryWithoutUpdate().getBoolean("$nex_remSalvation_mauveCombatTauntPlayed");
		protected ShipAPI flag;


		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			if (engine == null) return;
			if (engine.isPaused()) return;

			if (flag == null) flag = getToweringShip();
			if (flag == null) return;
			if (flag.isAlive()) {
				if (engine.getFleetManager(FleetSide.PLAYER).getTaskManager(false).isInFullRetreat()) {
					processPlayerRetreatChatter(flag);
				}
				else if (flag.areAnyEnemiesInRange()) {
					processLiveChatter(flag, amount);
				}
			} else {
				processDeathChatter(flag, amount);
			}
		}

		protected void processPlayerRetreatChatter(ShipAPI ship) {
			if (tauntRead) return;
			String line = RemnantQuestUtils.getString("salvation_toweringChatter_taunt");
			printMessage(ship, line, MAUVE, true);
			tauntRead = true;
			Global.getSector().getMemoryWithoutUpdate().set("$nex_remSalvation_mauveCombatTauntPlayed", true, 0.5f);
		}

		protected void processDeathChatter(ShipAPI ship, float amount) {
			if (deathLinesRead >= NUM_LINES_DEATH) {
				return;
			}
			intervalDeath.advance(amount);
			if (intervalDeath.intervalElapsed()) {
				deathLinesRead++;

				String line = RemnantQuestUtils.getString("salvation_toweringChatter_death" + deathLinesRead);
				printMessage(ship, line, MAUVE, true);
			}
		}

		protected void processLiveChatter(ShipAPI ship, float amount) {
			if (linesRead >= NUM_LINES) {
				return;
			}
			//Global.getLogger(this.getClass()).info(String.format("Interval at %s, needed %s", interval.getElapsed(), interval.getIntervalDuration()));
			interval.advance(amount);
			if (interval.intervalElapsed()) {
				linesRead++;

				String line = RemnantQuestUtils.getString("salvation_toweringChatter" + linesRead);
				printMessage(ship, line, MAUVE, true);
			}
		}

		protected ShipAPI getToweringShip() {
			return engine.getFleetManager(FleetSide.ENEMY).getShipFor(RemnantQuestUtils.getOrCreateTowering());
		}

		public void printMessage(ShipAPI ship, String message, Color textColor, boolean withColon) {
			String name = getShipName(ship, true, false);
			if (withColon) {
				engine.getCombatUI().addMessage(1, ship, getShipNameColor(ship), name, Misc.getTextColor(), ": ", textColor, message);
			} else {
				engine.getCombatUI().addMessage(1, ship, getShipNameColor(ship), name, textColor, message);
			}
		}

		protected Color getShipNameColor(ShipAPI ship) {
			if (ship.isAlly()) return Misc.getHighlightColor();
			if (ship != null && ship.isAlly()) return Misc.getHighlightColor();

			if (ship.getOwner() == 1 || ship.getFleetMember().getOwner() == 1)
				return Global.getSettings().getColor("textEnemyColor");
			return Global.getSettings().getColor("textFriendColor");
		}

		protected String getShipName(ShipAPI ship, boolean includeClass, boolean useOfficerName)
		{
			String shipName = "";
			if (ship.isFighter()) {
				shipName = ship.getHullSpec().getHullName();
				// not proper way for some languages but good enough in that we're never actually using it
				if (includeClass) shipName += " " + StringHelper.getString("fighterWingShort");
			}
			else {
				if (useOfficerName && !ship.getCaptain().isDefault()) {
					return ship.getCaptain().getNameString();
				}
				shipName = ship.getName();
				if (includeClass) shipName += " (" + ship.getHullSpec().getHullNameWithDashClass() + ")";
			}

			return shipName;
		}

		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
			interval.forceCurrInterval(2);
			intervalDeath.forceCurrInterval(0.1f);
		}
	}
}

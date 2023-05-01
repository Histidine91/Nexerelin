package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom plugin to add the force-deploy shards plugin to the Fragments combat encounter.
 */
public class FragmentsBattleCreationPlugin extends BattleCreationPluginImpl {
	
	@Override
	public void afterDefinitionLoad(CombatEngineAPI engine) {
		super.afterDefinitionLoad(engine);
		
		FleetGoal playerGoal = context.getPlayerGoal();
		FleetGoal enemyGoal = context.getOtherGoal();
		if (playerGoal == FleetGoal.ESCAPE || enemyGoal == FleetGoal.ESCAPE) return;
		
		engine.addPlugin(new ForceDeployRemnantShards());
	}	
	
	public static class ForceDeployRemnantShards extends BaseEveryFrameCombatPlugin {
		//boolean ran = false;
		protected CombatEngineAPI engine;
		
		private static final float MIN_OFFSET = 300f;
		
		// From Console Commands' ForceDeployAll
		// FIXME: Second line never forms (luckily we don't need it)
		public void moveToSpawnLocations(List<ShipAPI> toMove)
		{
			float startingOffset = toMove.size()/2f * MIN_OFFSET;
			
			final Vector2f spawnLoc = new Vector2f(
					-startingOffset, -engine.getMapHeight() * 0.4f);

			final List<ShipAPI> ships = engine.getShips();
			for (ShipAPI ship : toMove)
			{
				final float radius = ship.getCollisionRadius() + MIN_OFFSET;
				for (int i = 0; i < ships.size(); i++)
				{
					final ShipAPI other = ships.get(i);
					if (MathUtils.isWithinRange(other, spawnLoc, radius))
					{
						spawnLoc.x += radius;
						if (spawnLoc.x >= engine.getMapWidth() / 2f)
						{
							spawnLoc.x = -engine.getMapWidth();
							spawnLoc.y -= radius;
						}

						// We need to recheck for collisions in our new position
						i = 0;
					}
				}
				ship.getLocation().set(spawnLoc.x, spawnLoc.y);
				spawnLoc.x += radius;
			}
		}
		
		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			if (engine == null) return;
			
			final CombatFleetManagerAPI cfm = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER);
			final List<ShipAPI> deployed = new ArrayList<>();
			for (FleetMemberAPI member : cfm.getReservesCopy())
			{
				if (!member.isAlly()) continue;
				if (member.getHullId().equals("shard_left") || member.getHullId().equals("shard_right")) {
					deployed.add(cfm.spawnFleetMember(member, new Vector2f(0f, 0f), 90f, 3f));
				}
			}

			moveToSpawnLocations(deployed);
			
			engine.removePlugin(this);
		}
		
		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
		}
	}
}

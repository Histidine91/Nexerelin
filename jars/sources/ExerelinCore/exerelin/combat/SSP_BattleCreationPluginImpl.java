package exerelin.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.impl.combat.BattleCreationPluginImpl;
import com.fs.starfarer.api.impl.combat.EscapeRevealPlugin;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.world.SSP_AsteroidTracker;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SSP_BattleCreationPluginImpl extends BattleCreationPluginImpl {

    protected static final float ASTEROID_MAX_DIST = 750f;
    protected static final String COMM = "comm_relay";
    protected static final String NAV = "nav_buoy";
    protected static final float PLANET_MAX_DIST = 3000f;
    protected static final String SENSOR = "sensor_array";
    protected static final float SINGLE_PLANET_MAX_DIST = 1500f;
    protected static final float STAR_MAX_DIST = 7500f;

    public static boolean isHyperspace(CampaignFleetAPI playerFleet) {
        // Hyperspace causes longer engagement area, fewer objectives, and no nebulae or asteroids
        return playerFleet.getContainingLocation() == Global.getSector().getHyperspace();
    }

    public boolean isNearbyAsteroids(CampaignFleetAPI playerFleet) {
        // More rocks cause more rocks, duh
        float numAsteroidsWithinRange = countNearbyAsteroids(playerFleet);
        if (playerFleet.getContainingLocation() == Global.getSector().getHyperspace()) {
            if (numAsteroidsWithinRange > 0) {
                int numAsteroids = Math.min(100, (int) ((numAsteroidsWithinRange + 1f) * 20f));
                return numAsteroids >= 200;
            } else {
                return false;
            }
        } else {
            if (numAsteroidsWithinRange > 0) {
                int numAsteroids = Math.min(200, (int) ((numAsteroidsWithinRange + 1f) * 20f));
                return numAsteroids >= 300;
            } else {
                return false;
            }
        }
    }

    public static boolean isNearbyAsteroidsFast(CampaignFleetAPI playerFleet) {
        // Faster rocks cause faster rocks, duh
        Vector2f asteroidVelocity = getNearbyAsteroidsVelocity(playerFleet);
        if (asteroidVelocity.x != 0f || asteroidVelocity.y != 0f) {
            float asteroidSpeed = Math.min(50f, Math.max(10f, asteroidVelocity.length() * 1.5f));
            return asteroidSpeed >= 30f;
        } else {
            return false;
        }
    }

    public static boolean isNearbyPlanet(CampaignFleetAPI playerFleet) {
        // Gravity well causes flatter engagement area
        float amt =
              Math.min(1f, Math.max(0.6f, (float) Math.cbrt(getNearbyPlanetFactor(playerFleet) / PLANET_MAX_DIST)));
        return amt <= 0.85f;
    }

    public static boolean isNearbyStar(CampaignFleetAPI playerFleet) {
        // Proximity to solar interference causes nebulae
        float amt = getStarFactor(playerFleet);
        return amt >= 0.6f;
    }
	
	// calculation differs from vanilla, unsure of implications
	@Override
    protected float countNearbyAsteroids(CampaignFleetAPI playerFleet) {
        float numAsteroidsWithinRange = 0;
        float closest = Float.MAX_VALUE;
        LocationAPI loc = playerFleet.getContainingLocation();
        if (loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            List<SectorEntityToken> asteroids = system.getAsteroids();
            for (SectorEntityToken asteroid : asteroids) {
                float range = Vector2f.sub(playerFleet.getLocation(), asteroid.getLocation(), new Vector2f()).length();
                if (range <= ASTEROID_MAX_DIST) {
                    numAsteroidsWithinRange++;
                    if (range < closest) {
                        closest = range;
                    }
                }
            }
        }
        return numAsteroidsWithinRange * (1f - closest / ASTEROID_MAX_DIST);
    }
    
    /*
    public static PlanetAPI getClosestPlanet(CampaignFleetAPI playerFleet) {
        LocationAPI loc = playerFleet.getContainingLocation();
        PlanetAPI closest = null;
        float minDist = Float.MAX_VALUE;
        if (loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            List<PlanetAPI> planets = system.getPlanets();
            for (PlanetAPI planet : planets) {
                if (planet.isStar()) {
                    continue;
                }
                float dist = Vector2f.sub(playerFleet.getLocation(), planet.getLocation(), new Vector2f()).length();
                if (dist < minDist && dist < SINGLE_PLANET_MAX_DIST) {
                    closest = planet;
                    minDist = dist;
                }
            }
        }
        return closest;
    }
    */
    
    public static Vector2f getNearbyAsteroidsVelocity(CampaignFleetAPI playerFleet) {
        Vector2f sumVec = new Vector2f();
        LocationAPI loc = playerFleet.getContainingLocation();
        int count = 0;
        if (loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            List<SectorEntityToken> asteroids = system.getAsteroids();
            for (SectorEntityToken asteroid : asteroids) {
                float range = Vector2f.sub(playerFleet.getLocation(), asteroid.getLocation(), new Vector2f()).length();
                if (range <= ASTEROID_MAX_DIST) {
                    Vector2f velocity = SSP_AsteroidTracker.getVelocity(asteroid);
                    Vector2f.add(sumVec, velocity, sumVec);
                    count++;
                }
            }
        }
        if (count > 0) {
            sumVec.scale(1f / count);
        }
        return sumVec;
    }

    protected static List<NearbyJumpPointData> getNearbyJumpPoints(CampaignFleetAPI playerFleet) {
        LocationAPI loc = playerFleet.getContainingLocation();
        List<NearbyJumpPointData> result = new ArrayList<>(10);
        List<SectorEntityToken> jumpPoints = loc.getEntitiesWithTag(Tags.JUMP_POINT);
        for (SectorEntityToken token : jumpPoints) {
            JumpPointAPI jumpPoint = (JumpPointAPI) token;
            Vector2f vector = Vector2f.sub(jumpPoint.getLocation(), playerFleet.getLocation(), new Vector2f());
            float range = vector.length();
            if (range <= PLANET_MAX_DIST) {
                result.add(new NearbyJumpPointData(vector, jumpPoint));
            }
        }
        return result;
    }

    protected static float getNearbyPlanetFactor(CampaignFleetAPI playerFleet) {
        LocationAPI loc = playerFleet.getContainingLocation();
        float minDist = Float.MAX_VALUE;
        if (loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            List<PlanetAPI> planets = system.getPlanets();
            for (PlanetAPI planet : planets) {
                if (planet.isStar()) {
                    continue;
                }
                float dist = Vector2f.sub(playerFleet.getLocation(), planet.getLocation(), new Vector2f()).length();
                float adjustedDist = (dist + 150f + planet.getRadius()) * (150f / Math.max(50f, planet.getRadius()));
                if (adjustedDist < minDist && dist < SINGLE_PLANET_MAX_DIST) {
                    minDist = adjustedDist;
                }
            }
        }
        return minDist;
    }

    protected List<NearbyPlanetData> getNearbyPlanets(CampaignFleetAPI playerFleet) {
        LocationAPI loc = playerFleet.getContainingLocation();
        List<NearbyPlanetData> result = new ArrayList<>(10);
        if (loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            List<PlanetAPI> planets = system.getPlanets();
            for (PlanetAPI planet : planets) {
                if (planet.isStar()) {
                    continue;
                }
                Vector2f vector = Vector2f.sub(planet.getLocation(), playerFleet.getLocation(), new Vector2f());
                float range = vector.length();
                if (range <= PLANET_MAX_DIST) {
                    result.add(new NearbyPlanetData(vector, planet));
                }
            }
        }
        return result;
    }

    protected static List<NearbyPlanetData> getNearbyStars(CampaignFleetAPI playerFleet) {
        LocationAPI loc = playerFleet.getContainingLocation();
        List<NearbyPlanetData> result = new ArrayList<>(2);
        if (loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            List<PlanetAPI> planets = system.getPlanets();
            for (PlanetAPI planet : planets) {
                if (!planet.isStar()) {
                    continue;
                }
                Vector2f vector = Vector2f.sub(playerFleet.getLocation(), planet.getLocation(), new Vector2f());
                result.add(new NearbyPlanetData(vector, planet));
            }
        }
        return result;
    }

    protected static float getStarFactor(CampaignFleetAPI playerFleet) {
        LocationAPI loc = playerFleet.getContainingLocation();
        float factor = 0f;
        if (loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            List<PlanetAPI> planets = system.getPlanets();
            for (PlanetAPI planet : planets) {
                if (!planet.isStar()) {
                    continue;
                }
                Vector2f vector = Vector2f.sub(playerFleet.getLocation(), planet.getLocation(), new Vector2f());
                float range = vector.length() * (500f / Math.max(100f, planet.getRadius()));
                if (range <= STAR_MAX_DIST) {
                    factor += 1f - (range / STAR_MAX_DIST);
                }
            }
        }
        return factor;
    }

    protected int nebulaLevel;
    protected ArrayList<BattleObjective> objectives;
    protected float sizeMod;

    @Override
    public void initBattle(final BattleCreationContext context, MissionDefinitionAPI loader) {
		
		super.initBattle(context, loader);
        this.context = context;
        this.loader = loader;
		Random random = Misc.random;
		
        objectives = new ArrayList<>(5);
        CampaignFleetAPI playerFleet = context.getPlayerFleet();
        CampaignFleetAPI otherFleet = context.getOtherFleet();
        FleetGoal playerGoal = context.getPlayerGoal();
        FleetGoal enemyGoal = context.getOtherGoal();

        escape = playerGoal == FleetGoal.ESCAPE || enemyGoal == FleetGoal.ESCAPE;

        int maxFP = (int) Global.getSettings().getFloat("maxNoObjectiveBattleSize");
        int fpOne = 0;
        int fpTwo = 0;
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (member.canBeDeployedForCombat() || playerGoal == FleetGoal.ESCAPE) {
                fpOne += member.getDeploymentPointsCost();
            }
        }
        for (FleetMemberAPI member : otherFleet.getFleetData().getMembersListCopy()) {
            if (member.canBeDeployedForCombat() || playerGoal == FleetGoal.ESCAPE) {
                fpTwo += member.getDeploymentPointsCost();
            }
        }

        int smaller = Math.min(fpOne, fpTwo);

        boolean withObjectives = smaller > maxFP;
        if (!context.objectivesAllowed) {
            withObjectives = false;
        }
        
        // Diff. from vanilla: up to 7 objectives (instead of 4)
        int numObjectives = 0;
        if (withObjectives) {
            if (fpOne + fpTwo > maxFP + 480) {
                numObjectives = 5 + (int) (Math.random() * 3.0);
            } else if (fpOne + fpTwo > maxFP + 320) {
                numObjectives = 4 + (int) (Math.random() * 2.0);
            } else if (fpOne + fpTwo > maxFP + 180) {
                numObjectives = 3 + (int) (Math.random() * 2.0);
            } else if (fpOne + fpTwo > maxFP + 80) {
                numObjectives = 2 + (int) (Math.random() * 2.0);
            } else {
                numObjectives = 1 + (int) (Math.random() * 2.0);
            }
        }
        
        // Diff. from vanilla: 1 objective fewer in hyperpsace
        if (context.getPlayerFleet().getContainingLocation() == Global.getSector().getHyperspace()) {
            numObjectives--;
        }
        
        // Diff. from vanilla: cap objectives at 6 (4 during escape) instead of 4
        if (numObjectives > 6) {
            numObjectives = 6;
        }
        if (numObjectives > 4 && escape) {
            numObjectives = 4;
        }
        // Diff. from vanilla: Some stuff to ensure at least 2 objectives during escape
        // and avoid having only 1 objective in normal battle
        if (numObjectives == 1) {
            numObjectives = 2;
        }
        if (numObjectives < 2 && escape) {
            numObjectives = 2;
        }

        int baseCommandPoints = (int) Global.getSettings().getFloat("startingCommandPoints");

        loader.initFleet(FleetSide.PLAYER, "ISS", playerGoal, false,
                         context.getPlayerCommandPoints() - baseCommandPoints,
                         (int) playerFleet.getCommanderStats().getCommandPoints().getModifiedValue() - baseCommandPoints);
        loader.initFleet(FleetSide.ENEMY, "", enemyGoal, true,
                         (int) otherFleet.getCommanderStats().getCommandPoints().getModifiedValue() - baseCommandPoints);

        List<FleetMemberAPI> playerShips = playerFleet.getFleetData().getCombatReadyMembersListCopy();
        if (playerGoal == FleetGoal.ESCAPE) {
            playerShips = playerFleet.getFleetData().getMembersListCopy();
        }
        for (FleetMemberAPI member : playerShips) {
            loader.addFleetMember(FleetSide.PLAYER, member);
        }

        List<FleetMemberAPI> enemyShips = otherFleet.getFleetData().getCombatReadyMembersListCopy();
        if (enemyGoal == FleetGoal.ESCAPE) {
            enemyShips = otherFleet.getFleetData().getMembersListCopy();
        }
        for (FleetMemberAPI member : enemyShips) {
            loader.addFleetMember(FleetSide.ENEMY, member);
        }
        
        // diff. from vanilla: squish map if near planet, stretch if in hyperspace
        /*
        float heightMod;
        if (context.getPlayerFleet().getContainingLocation() == Global.getSector().getHyperspace()) {
            heightMod = 1f + 0.5f * (float) (Math.random() * Math.random());
        } else {
            heightMod = Math.min(1.2f, Math.min(1f, Math.max(0.6f, (float) Math.cbrt(getNearbyPlanetFactor(
                                                             context.getPlayerFleet()) / PLANET_MAX_DIST))) *
                                 (1.25f + 0.25f * (float) Math.random()));
        }
        float widthMod = 1f / (float) Math.sqrt(heightMod);
        */
        float heightMod = 1, widthMod = 1;

        width = 18000f * widthMod;
        height = 18000f * heightMod;
        float baseHeightForEscape = height;
        
        // diff. from vanilla: more variable map sizes
        if (escape) {
            if (numObjectives <= 2) {
                width = 12000f;
                height = 14000f;
            } else if (numObjectives <= 4) {
                width = 14000f;
                height = 18000f;
            } else {
                width = 16000f;
                height = 22000f;
            }
            xPad = 2000f;
            yPad = 4000f;
        } else if (withObjectives) {
            if (numObjectives == 2) {
                width = 20000f * widthMod;
                height = 16000f * heightMod;
            } else if (numObjectives <= 4) {
                width = 22000f * widthMod;
                height = 18000f * heightMod;
            } else {
                width = 24000f * widthMod;
                height = 20000f * heightMod;
            }
            xPad = 2000f;
            yPad = 3000f;
        }
        
        float heightModForEscape = height/baseHeightForEscape;
        
        // diff. from vanilla: Some manipulations of map height during escape scenarios
        /*
        float distanceMod = 1f;
        if (escape) {
            int escapingFP = (playerGoal == FleetGoal.ESCAPE) ? context.getPlayerFleet().getFleetPoints() :
                             context.getOtherFleet().getFleetPoints();
            int pursuingFP = (playerGoal == FleetGoal.ESCAPE) ? context.getOtherFleet().getFleetPoints() :
                             context.getPlayerFleet().getFleetPoints();
            distanceMod = (float) Math.sqrt(pursuingFP / (Math.max(escapingFP, 10f) * 1.25f));
            if (playerGoal == FleetGoal.ESCAPE) {
                distanceMod *= 1.25f;
            }
            distanceMod = Math.max(Math.min(distanceMod, 3f), 1f);
            height += 4000 * (distanceMod - 1f);
        }
        */

        sizeMod = (width / 18000f) * (height / 18000f);

        createMap(random);

        context.setInitialDeploymentBurnDuration(1.5f);
        context.setNormalDeploymentBurnDuration(6f);
        context.setEscapeDeploymentBurnDuration(1.5f);

        if (escape) {
            addEscapeObjectives(loader, numObjectives, random);
            //context.setInitialEscapeRange(4000f * distanceMod);
            //context.setFlankDeploymentDistance(8000f);
            context.setInitialEscapeRange(Global.getSettings().getFloat("escapeStartDistance") * heightModForEscape);
            context.setFlankDeploymentDistance(Global.getSettings().getFloat("escapeFlankDistance") * heightModForEscape);

            loader.addPlugin(new EscapeRevealPlugin(context));
        } else {
            if (withObjectives) {
                addObjectives(loader, numObjectives, random);
                context.setStandoffRange(height - 4500f);
            } else {
                context.setStandoffRange(6000f);
            }
        }
    }
	
	// Only difference from vanilla is asteroid handling I think?
	@Override
    protected void createMap(Random random) {
        loader.initMap((float)-width/2f, (float)width/2f, (float)-height/2f, (float)height/2f);

        CampaignFleetAPI playerFleet = context.getPlayerFleet();
        String nebulaTex = null;
        String nebulaMapTex = null;
        boolean inNebula = false;

        boolean protectedFromCorona = false;
        for (CustomCampaignEntityAPI curr : playerFleet.getContainingLocation().getCustomEntitiesWithTag(Tags.PROTECTS_FROM_CORONA_IN_BATTLE)) {
            if (Misc.getDistance(curr.getLocation(), playerFleet.getLocation()) <= curr.getRadius() + Global.getSector().getPlayerFleet().getRadius() + 10f) {
                protectedFromCorona = true;
                break;
            }
        }

        abyssalDepth = Misc.getAbyssalDepth(playerFleet);

        float numRings = 0;

        Color coronaColor = null;
        // this assumes that all nebula in a system are of the same color
        for (CampaignTerrainAPI terrain : playerFleet.getContainingLocation().getTerrainCopy()) {
            //if (terrain.getType().equals(Terrain.NEBULA)) {
            if (terrain.getPlugin() instanceof NebulaTextureProvider) {
                if (terrain.getPlugin().containsEntity(playerFleet)) {
                    inNebula = true;
                    if (terrain.getPlugin() instanceof NebulaTextureProvider) {
                        NebulaTextureProvider provider = (NebulaTextureProvider) terrain.getPlugin();
                        nebulaTex = provider.getNebulaTex();
                        nebulaMapTex = provider.getNebulaMapTex();
                    }
                } else {
                    if (nebulaTex == null) {
                        if (terrain.getPlugin() instanceof NebulaTextureProvider) {
                            NebulaTextureProvider provider = (NebulaTextureProvider) terrain.getPlugin();
                            nebulaTex = provider.getNebulaTex();
                            nebulaMapTex = provider.getNebulaMapTex();
                        }
                    }
                }
            } else if (terrain.getPlugin() instanceof StarCoronaTerrainPlugin && pulsar == null && !protectedFromCorona) {
                StarCoronaTerrainPlugin plugin = (StarCoronaTerrainPlugin) terrain.getPlugin();
                if (plugin.containsEntity(playerFleet)) {
                    float angle = Misc.getAngleInDegrees(terrain.getLocation(), playerFleet.getLocation());
                    Color color = plugin.getAuroraColorForAngle(angle);
                    float intensity = plugin.getIntensityAtPoint(playerFleet.getLocation());
                    intensity = 0.4f + 0.6f * intensity;
                    int alpha = (int)(80f * intensity);
                    color = Misc.setAlpha(color, alpha);
                    if (coronaColor == null || coronaColor.getAlpha() < alpha) {
                        coronaColor = color;
                        coronaIntensity = intensity;
                        corona = plugin;
                    }
                }
            } else if (terrain.getPlugin() instanceof PulsarBeamTerrainPlugin && !protectedFromCorona) {
                PulsarBeamTerrainPlugin plugin = (PulsarBeamTerrainPlugin) terrain.getPlugin();
                if (plugin.containsEntity(playerFleet)) {
                    float angle = Misc.getAngleInDegreesStrict(terrain.getLocation(), playerFleet.getLocation());
                    Color color = plugin.getPulsarColorForAngle(angle);
                    float intensity = plugin.getIntensityAtPoint(playerFleet.getLocation());
                    intensity = 0.4f + 0.6f * intensity;
                    int alpha = (int)(80f * intensity);
                    color = Misc.setAlpha(color, alpha);
                    if (coronaColor == null || coronaColor.getAlpha() < alpha) {
                        coronaColor = color;
                        coronaIntensity = intensity;
                        pulsar = plugin;
                        corona = null;
                    }
                }
            } else if (terrain.getType().equals(Terrain.RING)) {
                if (terrain.getPlugin().containsEntity(playerFleet)) {
                    numRings++;
                }
            }
        }
        if (nebulaTex != null) {
            loader.setNebulaTex(nebulaTex);
            loader.setNebulaMapTex(nebulaMapTex);
        }

        if (coronaColor != null) {
            loader.setBackgroundGlowColor(coronaColor);
        }

        int numNebula = 15;
        if (inNebula) {
            numNebula = 100;
        }
        if (!inNebula && playerFleet.isInHyperspace()) {
            numNebula = 0;
        }

        for (int i = 0; i < numNebula; i++) {
            float x = random.nextFloat() * width - width/2;
            float y = random.nextFloat() * height - height/2;
            float radius = 100f + random.nextFloat() * 400f;
            if (inNebula) {
                radius += 100f + 500f * random.nextFloat();
            }
            loader.addNebula(x, y, radius);
        }
        
        // Diff. from vanilla: More complex asteroid handling
        float numAsteroidsWithinRange = countNearbyAsteroids(playerFleet);

        int numAsteroids;
        if (context.getPlayerFleet().getContainingLocation() == Global.getSector().getHyperspace()) {
            if (numAsteroidsWithinRange > 0) {
                numAsteroids = Math.min(100, (int) ((numAsteroidsWithinRange + 1f) * 20f));
            } else {
                numAsteroids = 0;
            }
        } else {
            if (numAsteroidsWithinRange > 0) {
                numAsteroids = Math.min(200, (int) ((numAsteroidsWithinRange + 1f) * 20f));
            } else {
                if (Math.random() > 0.5) {
                    numAsteroids = (int) (Math.random() * 200.0);
                } else {
                    numAsteroids = 0;
                }
            }
        }
        numAsteroids *= sizeMod;

        Vector2f asteroidVelocity = getNearbyAsteroidsVelocity(context.getPlayerFleet());
        float asteroidDirection;
        float asteroidSpeed;
        if (asteroidVelocity.x != 0f || asteroidVelocity.y != 0f) {
            asteroidDirection = VectorUtils.getFacing(asteroidVelocity);
            asteroidSpeed = Math.min(50f, Math.max(10f, asteroidVelocity.length() * 1.5f));
        } else {
            asteroidDirection = (float) Math.random() * 360f;
            asteroidSpeed = (float) Math.random() * 5f + 5f;
        }

        if (numAsteroids > 0) {
            loader.addAsteroidField(0, 0, asteroidDirection, Math.max(width, height), asteroidSpeed * 1.5f,
                                    asteroidSpeed * 3f + 40f, numAsteroids);
        }

        if (numRings > 0) {
            int numRingAsteroids = (int) (numRings * 300 + (numRings * 600f) * Math.random());
            if (numRingAsteroids > 1500) {
                numRingAsteroids = 1500;
            }
            loader.addRingAsteroids(0, 0, (float)Math.random() * 360f, width, 100f, 200f, numRingAsteroids);
        }

        //setRandomBackground(loader);
        loader.setBackgroundSpriteName(playerFleet.getContainingLocation().getBackgroundTextureFilename());
//		loader.setBackgroundSpriteName("graphics/backgrounds/hyperspace_bg_cool.jpg");
//		loader.setBackgroundSpriteName("graphics/ships/onslaught/onslaught_base.png");

        if (playerFleet.getContainingLocation() == Global.getSector().getHyperspace()) {
            loader.setHyperspaceMode(true);
        } else {
            loader.setHyperspaceMode(false);
        }

        //addClosestPlanet();    // not found in our class
        addMultiplePlanets();
    }
    
    // Different from vanilla
	/*
	@Override
    protected void addMultiplePlanets() {
        float bgWidth = width / 17.5f;
        float bgHeight = height / 17.5f;

        List<NearbyPlanetData> planets = getNearbyPlanets(context.getPlayerFleet());
        List<NearbyJumpPointData> jumpPoints = getNearbyJumpPoints(context.getPlayerFleet());
        float closestSquared = Float.MAX_VALUE;
        SectorEntityToken closest = null;
        for (NearbyPlanetData data : planets) {
            float distanceSquared = data.offset.lengthSquared();
            if (distanceSquared < closestSquared) {
                closestSquared = distanceSquared;
                closest = data.planet;
            }
        }
        for (NearbyJumpPointData data : jumpPoints) {
            float distanceSquared = data.offset.lengthSquared();
            if (distanceSquared < closestSquared) {
                closestSquared = distanceSquared;
                closest = data.jumpPoint;
            }
        }

        JumpPointAPI biggestJumpPoint = null;
        float biggest = 0f;
        for (NearbyJumpPointData data : jumpPoints) {
            float size = data.jumpPoint.getRadius();
            if (size > biggest) {
                biggest = size;
                biggestJumpPoint = data.jumpPoint;
            }
        }

        if ((!planets.isEmpty() || !jumpPoints.isEmpty()) && closest != null) {
            loader.setPlanetBgSize(bgWidth, bgHeight);

            float maxDist = PLANET_MAX_DIST;
            for (NearbyPlanetData data : planets) {
                float dist = Vector2f.sub(context.getPlayerFleet().getLocation(), data.planet.getLocation(),
                                          new Vector2f()).length();
                float baseRadius = data.planet.getRadius();
                float scaleFactor = 1.5f;
                float distanceFactor = 1f;
                float maxRadius = 500f;

                float f = (maxDist - dist) / maxDist * 0.65f + 0.35f;
                float radius = baseRadius * f * scaleFactor;
                if (data.planet != closest) {
                    float otherDist =
                          Vector2f.sub(closest.getLocation(), data.planet.getLocation(), new Vector2f()).length();
                    radius *= (maxDist - otherDist) / maxDist * 0.65f + 0.35f;
                }
                if (radius > maxRadius) {
                    radius = maxRadius;
                }

                float locX = data.offset.x * distanceFactor;
                float locY = data.offset.y * distanceFactor;
                loader.addPlanet(locX, locY, radius, data.planet.getTypeId(), 0f, true);
            }
            for (NearbyJumpPointData data : jumpPoints) {
                float dist = Vector2f.sub(context.getPlayerFleet().getLocation(), data.jumpPoint.getLocation(),
                                          new Vector2f()).length();
                float baseRadius = data.jumpPoint.getRadius();
                float scaleFactor = 1.5f;
                float distanceFactor = 1f;
                float maxRadius = 500f;

                float f = (maxDist - dist) / maxDist * 0.65f + 0.35f;
                float radius = baseRadius * f * scaleFactor;
                if (data.jumpPoint != closest) {
                    float otherDist =
                          Vector2f.sub(closest.getLocation(), data.jumpPoint.getLocation(), new Vector2f()).length();
                    radius *= (maxDist - otherDist) / maxDist * 0.65f + 0.35f;
                }
                if (radius > maxRadius) {
                    radius = maxRadius;
                }

                float locX = data.offset.x * distanceFactor;
                float locY = data.offset.y * distanceFactor;
                loader.addPlanet(locX, locY, radius, "wormholeUnder", 0f, true);
                loader.addPlanet(locX, locY, radius, "wormholeA", 0f, true);
                loader.addPlanet(locX, locY, radius, "wormholeB", 0f, true);
                loader.addPlanet(locX, locY, radius, "wormholeC", 0f, true);
            }
        }
    }
    
    // diff. from vanilla: handles 5 and 6 objective cases
    protected void addObjectives(int num) {
        objs = new ArrayList<>(Arrays.asList(SENSOR, SENSOR, NAV, NAV, COMM, COMM));

		switch (num) {
			case 2:
				objs = new ArrayList<>(Arrays.asList(SENSOR, SENSOR, NAV, NAV, COMM));
				addObjectiveAt(0.25f, 0.5f, 0f, 0f);
				addObjectiveAt(0.75f, 0.5f, 0f, 0f);
				break;
			case 3:
				{
					float r = (float) Math.random();
					if (r < 0.33f) {
						addObjectiveAt(0.25f, 0.7f, 1f, 1f);
						addObjectiveAt(0.25f, 0.3f, 1f, 1f);
						addObjectiveAt(0.75f, 0.5f, 1f, 1f);
					} else if (r < 0.67f) {
						addObjectiveAt(0.25f, 0.7f, 1f, 1f);
						addObjectiveAt(0.25f, 0.3f, 1f, 1f);
						addObjectiveAt(0.75f, 0.7f, 1f, 1f);
					} else {
						addObjectiveAt(0.25f, 0.5f, 1f, 1f);
						addObjectiveAt(0.5f, 0.5f, 1f, 1f);
						addObjectiveAt(0.75f, 0.5f, 1f, 1f);
					}		break;
				}
			case 4:
				{
					float r = (float) Math.random();
					if (r < 0.33f) {
						addObjectiveAt(0.25f, 0.25f, 2f, 1f);
						addObjectiveAt(0.25f, 0.75f, 2f, 1f);
						addObjectiveAt(0.75f, 0.25f, 2f, 1f);
						addObjectiveAt(0.75f, 0.75f, 2f, 1f);
					} else if (r < 0.67f) {
						addObjectiveAt(0.25f, 0.5f, 1f, 1f);
						addObjectiveAt(0.5f, 0.75f, 1f, 1f);
						addObjectiveAt(0.75f, 0.5f, 1f, 1f);
						addObjectiveAt(0.5f, 0.25f, 1f, 1f);
					} else {
						addObjectiveAt(0.2f, 0.5f, 1f, 2f);
						addObjectiveAt(0.4f, 0.5f, 0f, 3f);
						addObjectiveAt(0.6f, 0.5f, 0f, 3f);
						addObjectiveAt(0.8f, 0.5f, 1f, 2f);
					}		break;
				}
			case 5:
				{
					float r = (float) Math.random();
					if (r < 0.33f) {
						addObjectiveAt(0.25f, 0.25f, 2f, 1f);
						addObjectiveAt(0.25f, 0.75f, 2f, 1f);
						addObjectiveAt(0.75f, 0.25f, 2f, 1f);
						addObjectiveAt(0.75f, 0.75f, 2f, 1f);
						addObjectiveAt(0.5f, 0.5f, 2f, 1f);
					} else if (r < 0.67f) {
						addObjectiveAt(0.25f, 0.5f, 1f, 1f);
						addObjectiveAt(0.75f, 0.5f, 1f, 1f);
						addObjectiveAt(0.5f, 0.25f, 2f, 0f);
						addObjectiveAt(0.5f, 0.75f, 2f, 0f);
						addObjectiveAt(0.5f, 0.5f, 1f, 1f);
					} else {
						addObjectiveAt(0.2f, 0.5f, 1f, 2f);
						addObjectiveAt(0.35f, 0.5f, 0f, 3f);
						addObjectiveAt(0.65f, 0.5f, 0f, 3f);
						addObjectiveAt(0.8f, 0.5f, 1f, 2f);
						addObjectiveAt(0.5f, 0.5f, 0f, 5f);
					}		break;
				}
			case 6:
				{
					float r = (float) Math.random();
					if (r < 0.33f) {
						addObjectiveAt(0.25f, 0.25f, 2f, 1f);
						addObjectiveAt(0.75f, 0.25f, 2f, 1f);
						addObjectiveAt(0.25f, 0.5f, 2f, 1f);
						addObjectiveAt(0.75f, 0.5f, 2f, 1f);
						addObjectiveAt(0.25f, 0.75f, 2f, 1f);
						addObjectiveAt(0.75f, 0.75f, 2f, 1f);
					} else if (r < 0.67f) {
						addObjectiveAt(0.25f, 0.5f, 1f, 1f);
						addObjectiveAt(0.75f, 0.5f, 1f, 1f);
						addObjectiveAt(0.5f, 0.2f, 1f, 1f);
						addObjectiveAt(0.5f, 0.4f, 1f, 1f);
						addObjectiveAt(0.5f, 0.6f, 1f, 1f);
						addObjectiveAt(0.5f, 0.8f, 1f, 1f);
					} else {
						addObjectiveAt(0.25f, 0.25f, 1f, 2f);
						addObjectiveAt(0.25f, 0.75f, 1f, 2f);
						addObjectiveAt(0.4f, 0.5f, 0f, 3f);
						addObjectiveAt(0.6f, 0.5f, 0f, 3f);
						addObjectiveAt(0.75f, 0.25f, 1f, 2f);
						addObjectiveAt(0.75f, 0.75f, 1f, 2f);
					}		break;
				}
			default:
				break;
		}
    }
    
    // Difference from vanilla: add case for 5-objective escape
    protected void addEscapeObjectives(int num) {
        objs = new ArrayList<>(Arrays.asList(SENSOR, SENSOR, NAV, NAV, COMM));

        switch (num) {
            case 2:
                {
                    float r = (float) Math.random();
                    if (r < 0.33f) {
                        addObjectiveAt(0.25f, 0.25f, 1f, 1f);
                        addObjectiveAt(0.75f, 0.75f, 1f, 1f);
                    } else if (r < 0.67f) {
                        addObjectiveAt(0.75f, 0.25f, 1f, 1f);
                        addObjectiveAt(0.25f, 0.75f, 1f, 1f);
                    } else {
                        addObjectiveAt(0.5f, 0.25f, 4f, 2f);
                        addObjectiveAt(0.5f, 0.75f, 4f, 2f);
                    }        break;
                }
            case 3:
                {
                    float r = (float) Math.random();
                    if (r < 0.33f) {
                        addObjectiveAt(0.25f, 0.75f, 1f, 1f);
                        addObjectiveAt(0.25f, 0.25f, 1f, 1f);
                        addObjectiveAt(0.75f, 0.5f, 1f, 6f);
                    } else if (r < 0.67f) {
                        addObjectiveAt(0.25f, 0.5f, 1f, 6f);
                        addObjectiveAt(0.75f, 0.75f, 1f, 1f);
                        addObjectiveAt(0.75f, 0.25f, 1f, 1f);
                    } else {
                        addObjectiveAt(0.5f, 0.25f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.5f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.75f, 4f, 1f);
                    }        break;
                }
            case 4:
                {
                    float r = (float) Math.random();
                    if (r < 0.33f) {
                        addObjectiveAt(0.25f, 0.25f, 1f, 1f);
                        addObjectiveAt(0.25f, 0.75f, 1f, 1f);
                        addObjectiveAt(0.75f, 0.25f, 1f, 1f);
                        addObjectiveAt(0.75f, 0.75f, 1f, 1f);
                    } else if (r < 0.67f) {
                        addObjectiveAt(0.35f, 0.25f, 2f, 0f);
                        addObjectiveAt(0.65f, 0.35f, 2f, 0f);
                        addObjectiveAt(0.5f, 0.6f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.8f, 4f, 1f);
                    } else {
                        addObjectiveAt(0.65f, 0.25f, 2f, 0f);
                        addObjectiveAt(0.35f, 0.35f, 2f, 0f);
                        addObjectiveAt(0.5f, 0.6f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.8f, 4f, 1f);
                    }        break;
                }
            case 5:
                {
                    float r = (float) Math.random();
                    if (r < 0.33f) {
                        addObjectiveAt(0.25f, 0.25f, 1f, 1f);
                        addObjectiveAt(0.25f, 0.75f, 1f, 1f);
                        addObjectiveAt(0.75f, 0.25f, 1f, 1f);
                        addObjectiveAt(0.75f, 0.75f, 1f, 1f);
                        addObjectiveAt(0.5f, 0.5f, 1f, 1f);
                    } else if (r < 0.67f) {
                        addObjectiveAt(0.25f, 0.45f, 2f, 0f);
                        addObjectiveAt(0.75f, 0.35f, 2f, 0f);
                        addObjectiveAt(0.5f, 0.6f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.8f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.25f, 2f, 0f);
                    } else {
                        addObjectiveAt(0.75f, 0.45f, 2f, 0f);
                        addObjectiveAt(0.25f, 0.35f, 2f, 0f);
                        addObjectiveAt(0.5f, 0.6f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.8f, 4f, 1f);
                        addObjectiveAt(0.5f, 0.25f, 2f, 0f);
                    }        break;
                }
            default:
                break;
        }
    }
	*/
	
    protected void addObjectiveAt(float xMult, float yMult, float xOff, float yOff) {
        String type = pickAny();
        if (objs != null && objs.size() > 0) {
            int index = (int) (Math.random() * objs.size());
            type = objs.remove(index);
        }

        float minX = -width / 2 + xPad;
        float minY = -height / 2 + yPad;

        float x = (width - xPad * 2f) * xMult + minX;
        float y = (height - yPad * 2f) * yMult + minY;

        x = ((int) x / 1000) * 1000f;
        y = ((int) y / 1000) * 1000f;

        float offsetX = Math.round((Math.random() - 0.5f) * xOff * 2f) * 1000f;
        float offsetY = Math.round((Math.random() - 0.5f) * yOff * 2f) * 1000f;

        float xDir = Math.signum(offsetX);
        float yDir = Math.signum(offsetY);

        if (xDir == prevXDir && xOff > 0) {
            xDir = -xDir;
            offsetX = Math.abs(offsetX) * -prevXDir;
        }

        if (yDir == prevYDir && yOff > 0) {
            yDir = -yDir;
            offsetY = Math.abs(offsetY) * -prevYDir;
        }

        prevXDir = xDir;
        prevYDir = yDir;

        x += offsetX;
        y += offsetY;

        // Filter objectives that are too nearby (not in vanilla)
        for (BattleObjective objective : objectives) {
            if (MathUtils.getDistance(new Vector2f(x, y), new Vector2f(objective.x, objective.y)) <= 1000f) {
                return;
            }
        }

        loader.addObjective(x, y, type);
        objectives.add(new BattleObjective(x, y, type));

        if (Math.random() * Math.sqrt(nebulaLevel / 100f) > 0.6) {
            float nebulaSize = ((float) Math.random() * 1500f + 500f);
            loader.addNebula(x, y, nebulaSize);
        }
    }
    
    protected static String pickAny() {
        float r = (float) Math.random();
        if (r < 0.33f) {
            return "nav_buoy";
        } else if (r < 0.67f) {
            return "sensor_array";
        } else {
            return "comm_relay";
        }
    }
    
    protected static class BattleObjective {

        String type;
        float x;
        float y;

        BattleObjective(float x, float y, String type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }
    }

    protected static class NearbyJumpPointData {

        final JumpPointAPI jumpPoint;
        final Vector2f offset;

        NearbyJumpPointData(Vector2f offset, JumpPointAPI jumpPoint) {
            this.offset = offset;
            this.jumpPoint = jumpPoint;
        }
    }
}

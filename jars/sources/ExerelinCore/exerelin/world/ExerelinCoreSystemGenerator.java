package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.procgen.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.ThemeGenContext;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.NexConfig;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

// same as vanilla except with moar planets + always custom names, and adds more gates + objectives
public class ExerelinCoreSystemGenerator extends StarSystemGenerator {
	
	public static final float SKIP_GIANT_OR_DWARF_CHANCE = 0.45f;

	public ExerelinCoreSystemGenerator(CustomConstellationParams params) {
		super(params);
	}
	
	protected void regenerate()
	{
		this.constellationAge = params.age;
		
		if (this.constellationAge == StarAge.ANY) {
			WeightedRandomPicker<StarAge> picker = new WeightedRandomPicker<StarAge>(random);
			picker.add(StarAge.AVERAGE);
			picker.add(StarAge.OLD);
			picker.add(StarAge.YOUNG);
			this.constellationAge = picker.pick();
		}
		
		constellationAgeData = (AgeGenDataSpec) Global.getSettings().getSpec(AgeGenDataSpec.class, constellationAge.name(), true);
		
		pickNebulaAndBackground();
	}
	
	@Override
	public Constellation generate() {
		lagrangeParentMap = new LinkedHashMap<SectorEntityToken, PlanetAPI>();
		allNameableEntitiesAdded = new LinkedHashMap<SectorEntityToken, List<SectorEntityToken>>();
		
		SectorAPI sector = Global.getSector();
		Vector2f loc = new Vector2f();
		if (params != null && params.location != null) {
			loc = new Vector2f(params.location);
		} else {
			loc = new Vector2f(sector.getPlayerFleet().getLocation());
			loc.x = (int) loc.x;
			loc.y = (int) loc.y;
		}
		
		
		pickNebulaAndBackground();
		
		List<StarSystemAPI> systems = new ArrayList<StarSystemAPI>();
		int stars = (int) Math.round(getNormalRandom(1, 7));
		if (params != null && params.numStars > 0) {
			stars = params.numStars;
		} else if (params != null && params.minStars > 0 && params.maxStars > 0) {
			stars = (int) Math.round(getNormalRandom(params.minStars, params.maxStars));
		}
		
//		constellationName = ProcgenUsedNames.pickName(NameGenData.TAG_CONSTELLATION, null);
//		ProcgenUsedNames.notifyUsed(constellationName.nameWithRomanSuffixIfAny);
//		Global.getSettings().greekLetterReset();
		
		// CHANGED //
		// run pickNebulaAndBackground() after every star
		for (int i = 0; i < stars; i++) {
			generateSystem(new Vector2f(0, 0));
			if (system != null) {
				systems.add(system);
				if (nebulaType != NEBULA_NONE)
				{
					system.setAge(starAge);
					system.setHasSystemwideNebula(true);
				}
			}
			regenerate();
		}
		
		
		ConstellationGen.SpringSystem springs = ConstellationGen.doConstellationLayout(systems, random, loc);
		Global.getSector().getHyperspace().updateAllOrbits();
		
		HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
		NebulaEditor editor = new NebulaEditor(plugin);
		
		float minRadius = plugin.getTileSize() * 2f;
		for (StarSystemAPI curr : systems) {
			float radius = curr.getMaxRadiusInHyperspace();
			editor.clearArc(curr.getLocation().x, curr.getLocation().y, 0, radius + minRadius * 0.5f, 0, 360f);
			editor.clearArc(curr.getLocation().x, curr.getLocation().y, 0, radius + minRadius, 0, 360f, 0.25f);
		}
		
		for (ConstellationGen.SpringConnection conn : springs.connections) {
			if (!conn.pull) continue;
			float r1 = ((StarSystemAPI)conn.from.custom).getMaxRadiusInHyperspace();
			float r2 = ((StarSystemAPI)conn.to.custom).getMaxRadiusInHyperspace();
			float dist = Misc.getDistance(conn.from.loc, conn.to.loc);
			
			float radius = Math.max(0, dist * 0.67f - r1 - r2);
			
//			float x = (conn.from.loc.x + conn.to.loc.x) * 0.5f;
//			float y = (conn.from.loc.y + conn.to.loc.y) * 0.5f;
//			editor.clearArc(x, y, 0, radius + minRadius * 0.5f, 0, 360f);
//			editor.clearArc(x, y, 0, radius + minRadius, 0, 360f, 0.25f);
			
			Vector2f diff = Vector2f.sub(conn.to.loc, conn.from.loc, new Vector2f());
			float x = conn.from.loc.x + diff.x * 0.33f;
			float y = conn.from.loc.y + diff.y * 0.33f;
			editor.clearArc(x, y, 0, radius + minRadius * 1f, 0, 360f);
			editor.clearArc(x, y, 0, radius + minRadius * 2f, 0, 360f, 0.25f);
			
			x = conn.from.loc.x + diff.x * 0.67f;
			y = conn.from.loc.y + diff.y * 0.67f;
			editor.clearArc(x, y, 0, radius + minRadius * 1f, 0, 360f);
			editor.clearArc(x, y, 0, radius + minRadius * 2f, 0, 360f, 0.25f);
		}
		
		// CHANGED //
		// fixed constellation params
		Constellation c = new Constellation(Constellation.ConstellationType.NORMAL, StarAge.ANY);
		c.getSystems().addAll(systems);
		c.setLagrangeParentMap(lagrangeParentMap);
		c.setAllEntitiesAdded(allNameableEntitiesAdded);

		
//		SalvageEntityGeneratorOld seg = new SalvageEntityGeneratorOld(c);
//		seg.addSalvageableEntities();
		
		// CHANGED //
		// 100% special name chance
		NameAssigner namer = new NameAssigner(c);
		namer.setSpecialNamesProbability(1f);
		namer.assignNames(params.name, params.secondaryName);
		
		for (SectorEntityToken entity : allNameableEntitiesAdded.keySet()) {
			if (entity instanceof PlanetAPI && entity.getMarket() != null) {
				entity.getMarket().setName(entity.getName());
			}
		}
		
		//if (systems.size() > 1) {
			for (StarSystemAPI system : systems) {
				system.setConstellation(c);
			}
		//}
		ThemeGenContext context = new ThemeGenContext();
		context.constellations.add(c);
		new RandomCoreThemePsuedoGenerator().generateForSector(context, -1f);
		
		return c;
	}

	// don't want black holes, pulsars or nebulae
	// and hopefuly not giants or dwarfs
	@Override
	public PlanetSpecAPI pickStar(StarAge age) {
		PlanetSpecAPI newStar = null;
		int tries = 0;
		while (tries < 12)
		{
			tries++;
			newStar = super.pickStar(age);
			
			if (newStar.getPlanetType().toLowerCase().contains("giant") || newStar.getPlanetType().toLowerCase().contains("dwarf"))
			{
				if (random.nextFloat() < SKIP_GIANT_OR_DWARF_CHANCE)
					continue;
			}
			
			if (!newStar.isBlackHole() && !newStar.isPulsar() && !newStar.isNebulaCenter())
				break;	// valid star
		}
		return newStar;
	}
	
	// no nebula-type systems allowed
	@Override
	protected StarSystemType pickSystemType(StarAge constellationAge) {
		
		if (params != null && !params.systemTypes.isEmpty()) {
			return params.systemTypes.remove(0);
		}
		
		
		WeightedRandomPicker<StarSystemType> picker = new WeightedRandomPicker<StarSystemType>(random);
		for (StarSystemType type : EnumSet.allOf(StarSystemType.class)) {
			Object test = Global.getSettings().getSpec(LocationGenDataSpec.class, type.name(), true);
			if (test == null) continue;
			LocationGenDataSpec data = (LocationGenDataSpec) test;
			
			// CHANGED //
			if (type == StarSystemType.NEBULA) continue;
			
			boolean nebulaStatusOk = NEBULA_NONE.equals(nebulaType) || !data.hasTag(TAG_NOT_IN_NEBULA);
			nebulaStatusOk &= !NEBULA_NONE.equals(nebulaType) || !data.hasTag(TAG_REQUIRES_NEBULA);

			if (!nebulaStatusOk) continue;
			
			float freq = 0f;
			switch (constellationAge) {
			case AVERAGE:
				freq = data.getFreqAVERAGE();
				break;
			case OLD:
				freq = data.getFreqOLD();
				break;
			case YOUNG:
				freq = data.getFreqYOUNG();
				break;
			}
			picker.add(type, freq);
		}
		
		return picker.pick();
	}
	
	// weighted towards habitable worlds
	@Override
	public CategoryGenDataSpec pickCategory(GenContext context, String extraMult, boolean nothingOk) {
//		int orbitIndex = context.orbitIndex;
//		if (context.parentOrbitIndex >= 0) {
//			orbitIndex = context.parentOrbitIndex;
//		}
//		int fromParentOrbitIndex = context.orbitIndex;
		String age = context.age;
		//String starType = context.star.getTypeId();
		String starType = star.getTypeId();
		if (context.center instanceof PlanetAPI) {
			PlanetAPI star = (PlanetAPI) context.center;
			if (star.isStar()) starType = star.getTypeId();
		}
		
		String parentCategory = context.parentCategory;
		
		WeightedRandomPicker<CategoryGenDataSpec> picker = new WeightedRandomPicker<CategoryGenDataSpec>(random);
		Collection<CategoryGenDataSpec> categoryDataSpecs = Global.getSettings().getAllSpecs(CategoryGenDataSpec.class);
		for (CategoryGenDataSpec categoryData : categoryDataSpecs) {
			boolean catNothing = categoryData.getCategory().equals(CAT_NOTHING);
			if (!nothingOk && catNothing) continue;
//			if (categoryData.getCategory().equals("cat_terrain_rings")) {
//				System.out.println("sdfkwefewfe");
//			}
			float weight = categoryData.getFrequency();
			if (age != null) weight *= categoryData.getMultiplier(age);
			if (starType != null) weight *= categoryData.getMultiplier(starType);
			if (parentCategory != null) weight *= categoryData.getMultiplier(parentCategory);
			for (String col : context.multipliers) {
				weight *= categoryData.getMultiplier(col);
			}
			if (extraMult != null) weight *= categoryData.getMultiplier(extraMult);
			
			// CHANGED //
			// prefer habitable worlds
			// also gas giants for their volatiles
			switch (categoryData.getCategory())
			{
				case "cat_hab5":	// none
					weight *= 1.5f;
					break;
				case "cat_hab4":	// terran
					weight *= 2f;
					break;
				case "cat_hab3":	// terran-eccentric, jungle, arid, water, tundra
					weight *= 3f;
					break;
				case "cat_hab2":	// desert
					weight *= 2f;
					break;
				case "cat_hab1":	// barren-desert
					weight *= 1.5f;
					break;
				case "cat_giant":
					weight *= 2f;
					break;
			}
			
			//if (weight > 0 && (catNothing || !isCategoryEmpty(categoryData, orbitIndex, fromParentOrbitIndex, age, starType, parentCategory, extraMult))) {
			if (weight > 0 && (catNothing || !isCategoryEmpty(categoryData, context, extraMult, nothingOk))) {
				picker.add(categoryData, weight); 
			}
		}
		
		if (DEBUG) {
			boolean withParent = context.parent != null;
			int orbitIndex = context.orbitIndex;
			String parentType = "";
			if (withParent) {
				parentType = context.parent.getSpec().getPlanetType();
				orbitIndex = context.parentOrbitIndex;
			}
			
//			float offset = orbitIndex;
//			float minIndex = context.starData.getHabZoneStart() + planetData.getHabOffsetMin() + offset;
//			float maxIndex = context.starData.getHabZoneStart() + planetData.getHabOffsetMax() + offset;
			//boolean inRightRange = orbitIndex >= minIndex && orbitIndex <= maxIndex;
			int habDiff = orbitIndex - (int) context.starData.getHabZoneStart();
			if (withParent) {
				picker.print("  Picking category for moon of " + parentType + 
							 ", orbit from star: " + orbitIndex + " (" + habDiff + ")" +  ", extra: " + extraMult);
			} else {
				picker.print("  Picking category for entity orbiting star " + starType + 
							", orbit from star: " + orbitIndex + " (" + habDiff + ")" +  ", extra: " + extraMult);
			}
		}
		
		CategoryGenDataSpec pick = picker.pick();
		if (DEBUG) {
			System.out.println("  Picked: " + pick.getCategory());
			System.out.println();
		}
		
		return pick;
	}
	
	@Override
	protected GenResult addPlanetsAndTerrain(float maxOrbitRadius) {
		boolean hasOrbits = random.nextFloat() < starData.getProbOrbits();
		if (!hasOrbits) return null;
		
		// CHANGED //
		// arbitrary min/max values
		float min = 4;	//starData.getMinOrbits() + starAgeData.getMinExtraOrbits() + 1;
		float max = 9;	//starData.getMaxOrbits() + starAgeData.getMaxExtraOrbits() + 3;
		min = Math.max(min, NexConfig.minimumPlanets);
		max = Math.max(min, max);
		
		int numOrbits = (int) Math.round(getNormalRandom(min, max));
		
		if (numOrbits <= 0) return null;

		
		
		float currentRadius = centerRadius + STARTING_RADIUS_STAR_BASE + STARTING_RADIUS_STAR_RANGE * random.nextFloat();

		//GenContext context = new GenContext(this, system, star, starData, 
		GenContext context = new GenContext(this, system, systemCenter, starData, 
							null, 0, starAge.name(), currentRadius, maxOrbitRadius, null, -1);
		
		if (systemType == StarSystemType.BINARY_CLOSE || systemType == StarSystemType.TRINARY_1CLOSE_1FAR) {
			context.multipliers.add(COL_BINARY);
		}
		if (systemType == StarSystemType.TRINARY_2CLOSE) {
			context.multipliers.add(COL_TRINARY);
		}
		
		// GenContext context, int numOrbits, boolean addingMoons, boolean addMoons, boolean parentIsMoon, boolean nothingOk
		GenResult result = addOrbitingEntities(context, numOrbits, false, true, false, false);
		result.context = context;
		return result;
	}
	
	@Override
	protected boolean addStars(String id) {
		boolean result = super.addStars(id);
		
		// avoid putting stars and jump points close enough to be cooked
		if (system.getStar() != null && centerRadius == system.getStar().getRadius()) {
			//centerRadius += system.getStar().getSpec().getCoronaSize();
		}
				
		return result;
	}
}

package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.procgen.CategoryGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.LocationGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.NameAssigner;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import static com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.NEBULA_NONE;
import static com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.TAG_NOT_IN_NEBULA;
import static com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.TAG_REQUIRES_NEBULA;
import static com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator.random;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinConfig;
import java.util.Collection;
import java.util.EnumSet;

// same as vanilla except with moar planets + always custom names
public class ExerelinCoreSystemGenerator extends StarSystemGenerator {
	
	public static final float SKIP_GIANT_OR_DWARF_CHANCE = 0.45f;

	public ExerelinCoreSystemGenerator(CustomConstellationParams params) {
		super(params);
	}
	
	@Override
	public Constellation generate() {
		Constellation c = super.generate();
		
		// force rename
		NameAssigner namer = new NameAssigner(c);
		namer.setSpecialNamesProbability(1f);
		namer.assignNames(params.name, params.secondaryName);
		
		for (SectorEntityToken entity : allNameableEntitiesAdded.keySet()) {
			if (entity instanceof PlanetAPI && entity.getMarket() != null) {
				entity.getMarket().setName(entity.getName());
			}
		}
		
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
		Collection<Object> categoryDataSpecs = Global.getSettings().getAllSpecs(CategoryGenDataSpec.class);
		for (Object obj : categoryDataSpecs) {
			CategoryGenDataSpec categoryData = (CategoryGenDataSpec) obj;
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
			
			// prefer habitable worlds
			if (categoryData.getCategory().contains("cat_hab"))
			{
				switch (categoryData.getCategory())
				{
					case "cat_hab5":	// none
						weight *= 1.5f;
						break;
					case "cat_hab4":	// terran
						weight *= 3f;
						break;
					case "cat_hab3":	// terran-eccentric, jungle, arid, water, tundra
						weight *= 4f;
						break;
					case "cat_hab2":	// desert
						weight *= 2f;
						break;
					case "cat_hab1":	// barren-desert
						weight *= 1.5f;
						break;
				}
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
		
		float min = 4;	//starData.getMinOrbits() + starAgeData.getMinExtraOrbits() + 1;
		float max = 9;	//starData.getMaxOrbits() + starAgeData.getMaxExtraOrbits() + 3;
		min = Math.max(min, ExerelinConfig.minimumPlanets);
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
	
	
}

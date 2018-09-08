package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
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
import java.util.EnumSet;

// same as vanilla except with moar planets + always custom names
public class ExerelinCoreSystemGenerator extends StarSystemGenerator {
	
	public static final float SKIP_GIANT_OR_DWARF_CHANCE = 0.5f;

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

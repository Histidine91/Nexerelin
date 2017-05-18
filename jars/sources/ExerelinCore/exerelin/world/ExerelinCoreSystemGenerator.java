package exerelin.world;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.NameAssigner;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import exerelin.utilities.ExerelinConfig;

// same as vanilla except with moar planets + always custom names
public class ExerelinCoreSystemGenerator extends StarSystemGenerator {

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
		
		
		GenResult result = addOrbitingEntities(context, numOrbits, false, true, false, true);
		result.context = context;
		return result;
	}
	
	
}

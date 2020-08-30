package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.ThemeGenContext;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.ArrayList;
import java.util.List;

public class RandomCoreThemePsuedoGenerator extends BaseThemeGenerator {
	
	public static final float PROB_TO_ADD_SOMETHING = 0.6f;
	
	@Override
	public int getOrder() {
		return 2500;	// something
	}

	@Override
	public String getThemeId() {
		return "nex_randomCore";
	}

	@Override
	public void generateForSector(ThemeGenContext context, float allowedUnusedFraction) {
		
		for (Constellation c : context.constellations) {
			
			List<StarSystemData> systems = new ArrayList<StarSystemData>();
			for (StarSystemAPI system : c.getSystems()) {
				StarSystemData data = computeSystemData(system);
				systems.add(data);
			}
			
			for (StarSystemData data  : systems) {
				if (random.nextFloat() > PROB_TO_ADD_SOMETHING) {
					continue;
				}

				populate(data);
			}
		}
	}
	
	public void populate(StarSystemData data) 
	{
		AddedEntity entity = addInactiveGate(data, 0.7f, 0, 0, new WeightedRandomPicker<String>());
		if (entity != null) Global.getLogger(this.getClass()).info("Added gate to " + data.system.getName());
		
		List<AddedEntity> objectives = addObjectives(data, 0.75f);
		if (!objectives.isEmpty())
			Global.getLogger(this.getClass()).info("Added " + objectives.size() + " objectives to " + data.system.getName());
	}
}

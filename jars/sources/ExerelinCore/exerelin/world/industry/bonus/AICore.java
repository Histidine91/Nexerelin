package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public abstract class AICore extends BonusGen {
	
	public AICore(String... industryIds) {
		super(industryIds);
	}
	
	@Override
	public boolean canApply(Industry ind, ProcGenEntity entity) {
		if (ind.getAICoreId() != null && ind.getAICoreId().equals(Commodities.ALPHA_CORE))
			return false;
		return true;
	}
	
	@Override
	public void apply(Industry ind, ProcGenEntity entity) {
		String currentAI = ind.getAICoreId();
		if (ind.getSpec().hasTag(Industries.TAG_STATION))
			ind.setAICoreId(Commodities.ALPHA_CORE);
		else if (currentAI == null || currentAI.equals(Commodities.GAMMA_CORE))
			ind.setAICoreId(Commodities.BETA_CORE);
		else
			ind.setAICoreId(Commodities.ALPHA_CORE);
		super.apply(ind, entity);
	}
}

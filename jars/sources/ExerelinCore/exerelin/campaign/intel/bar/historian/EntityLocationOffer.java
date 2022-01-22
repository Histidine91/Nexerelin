package exerelin.campaign.intel.bar.historian;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.historian.BaseHistorianOfferWithLocation;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.historian.HistorianData;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class EntityLocationOffer extends BaseHistorianOfferWithLocation implements DiscoverEntityListener {
	
	protected SectorEntityToken target;
	
	public EntityLocationOffer(SectorEntityToken target) {
		super(target);
		this.target = target;
	}
	
	@Override
	protected void addItemToCargo(CargoAPI cargo) {
		
	}

	public String getSortString() {
		return target.getName();
	}
	
	public SectorEntityToken getTarget() {
		return target;
	}
		
	public String getName() {
		String shuntName = target.getName();
		
		if (isEnding()) {
			return String.format(getString("intelTitleEntityLocComplete"), shuntName);
		} else {
			return String.format(getString("intelTitleEntityLoc"), shuntName);
		}
	}
	
	// FIXME: only works for shunts
	public void addLocationText(TooltipMakerAPI info, float width, float height, float opad) 
	{
		PlanetAPI star = target.getStarSystem().getStar();
		String str = getString("intelParaEntityLocCorona");
		info.addPara(str, opad, star.getLightColor(), star.getName());
	}
	
	@Override
	public void addPromptAndOption(InteractionDialogAPI dialog) {
		
		dialog.getOptionPanel().addOption(String.format(getString("promptEntityLoc"), target.getName()),
										this);
		SetStoryOption.set(dialog, 1, this, "historianBP", Sounds.STORY_POINT_SPEND_TECHNOLOGY,
				String.format(getString("spLogEntityLoc"), target.getName()));
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color g = Misc.getGrayColor();
		float opad = 10f;
		
		HistorianData hd = HistorianData.getInstance();
		
		String str = getString("intelParaEntityLoc1");
		str = StringHelper.substituteToken(str, "$historianName", hd.getPerson().getNameString());
		str = StringHelper.substituteToken(str, "$aOrAn", target.getCustomEntitySpec().getAOrAn());
		str = StringHelper.substituteToken(str, "$entity", target.getName());
		
		info.addPara(str, opad, Misc.getHighlightColor(), target.getName());
		addLocationText(info, width, height, opad);
	}
	
	@Override
	public void reportEntityDiscovered(SectorEntityToken entity) {
		if (entity == target) {
			Misc.makeUnimportant(target, getClass().getSimpleName());
			endIntel();
		}
	}
	
	@Override
	public String getIcon() {
		return target.getCustomEntitySpec().getSpriteName();
	}
	
	public String getString(String id) {
		return StringHelper.getString("nex_historian", id);
	}
}

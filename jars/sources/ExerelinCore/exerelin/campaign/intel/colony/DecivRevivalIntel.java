package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_DecivEvent;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Set;

public class DecivRevivalIntel extends FleetLogIntel {
	
	protected SectorEntityToken planet;
	
	public DecivRevivalIntel(SectorEntityToken planet) {
		this.planet = planet;
		setIcon("graphics/icons/markets/frontier.png");
	}
	
	public SectorEntityToken getPlanet() {
		return planet;
	}
	
	@Override
	public boolean shouldRemoveIntel() {
		if (planet.getMarket() != null && !planet.getMarket().isPlanetConditionMarketOnly()) return true;
		return super.shouldRemoveIntel();
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		info.addPara(planet.getName(), planet.getMarket().getTextColorForFactionOrPlanet(), initPad);
	}
	
	@Override
	protected String getName() {
		return StringHelper.getString("nex_decivEvent", "colonyIntelTitle");
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return planet;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color h = Misc.getHighlightColor();
		Color tc = Misc.getTextColor();
		float opad = 10f;
		
		String text = String.format(StringHelper.getString("nex_decivEvent", "colonyIntelDesc"),
				planet.getName(), planet.getContainingLocation().getNameWithLowercaseTypeShort(),
				Nex_DecivEvent.SUPPLIES_TO_COLONIZE, Nex_DecivEvent.MACHINERY_TO_COLONIZE);
		
		LabelAPI label = info.addPara(text, opad);
		label.setHighlight(planet.getName(), Nex_DecivEvent.SUPPLIES_TO_COLONIZE + "", Nex_DecivEvent.MACHINERY_TO_COLONIZE + "");
		label.setHighlightColors(planet.getMarket().getTextColorForFactionOrPlanet(), h, h);
				
		float days = getDaysSincePlayerVisible();
		if (days >= 1) {
			addDays(info, StringHelper.getString("ago") + ".", days, tc, opad);
		}
		
		addDeleteButton(info, width);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("colonies", true));
		return tags;
	}
	
	public static DecivRevivalIntel getActiveIntel(SectorEntityToken planet) {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(DecivRevivalIntel.class))
		{
			if (intel.isEnding() || intel.isEnded()) continue;
			DecivRevivalIntel dri = (DecivRevivalIntel)intel;
			if (dri.planet == planet) return dri;
		}
		return null;
	}
}

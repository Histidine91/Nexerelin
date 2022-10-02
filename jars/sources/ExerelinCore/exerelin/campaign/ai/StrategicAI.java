package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.util.*;

@Log4j
public class StrategicAI extends BaseIntelPlugin {

	public static final String MEMORY_KEY = "$nex_strategicAI";
	public static final float MARGIN = 40;
	//public static final Object BUTTON_GENERATE_REPORT = new Object();
	//public static final Long lastReportTimestamp;

	@Getter	protected final FactionAPI faction;
	//protected transient TooltipMakerAPI savedReport;

	@Getter EconomicAIModule econModule;
	@Getter DiplomaticAIModule diploModule;
	@Getter MilitaryAIModule milModule;

	@Getter public List<StrategicConcern> existingConcerns = new ArrayList<>();

	public StrategicAI(FactionAPI faction) {
		this.faction = faction;
	}
	
	public StrategicAI init() {
		Global.getSector().getIntelManager().addIntel(this);
		faction.getMemoryWithoutUpdate().set(MEMORY_KEY, this);

		econModule = new EconomicAIModule(this, StrategicDefManager.ModuleType.ECONOMIC);
		diploModule = new DiplomaticAIModule(this, StrategicDefManager.ModuleType.DIPLOMATIC);
		milModule = new MilitaryAIModule(this, StrategicDefManager.ModuleType.MILITARY);

		econModule.findConcerns();
		diploModule.findConcerns();
		milModule.findConcerns();

		return this;
	}
	
	public static StrategicAI getAI(String factionId) {
		return (StrategicAI)Global.getSector().getFaction(factionId).getMemoryWithoutUpdate().get(MEMORY_KEY);
	}

	public String getFactionId() {
		return faction.getId();
	}
	
	/*
	============================================================================
	// start of GUI stuff
	============================================================================
	*/

	public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI panel) {
		float pad = 3;
		float opad = 10;
		float sectionPad = 32;

		tooltip.setParaInsigniaLarge();
		tooltip.addPara("[temp] Economic report", sectionPad);
		tooltip.setParaFontDefault();
		econModule.generateReport(tooltip, panel);

		tooltip.setParaInsigniaLarge();
		tooltip.addPara("[temp] Military report", sectionPad);
		tooltip.setParaFontDefault();
		milModule.generateReport(tooltip, panel);

		tooltip.setParaInsigniaLarge();
		tooltip.addPara("[temp] Diplomacy report", sectionPad);
		tooltip.setParaFontDefault();
		diploModule.generateReport(tooltip, panel);
	}
	
	public void displayReport(TooltipMakerAPI tooltip, CustomPanelAPI panel, float width, float pad)
	{
		width -= MARGIN;
		generateReport(tooltip, panel);
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		TooltipMakerAPI superheaderHolder = panel.createUIElement(width/3, 40, false);
		TooltipMakerAPI superheader = superheaderHolder.beginImageWithText(getIcon(), 40);
		superheader.setParaOrbitronVeryLarge();
		superheader.addPara(getName(), 3);
		superheaderHolder.addImageWithText(3);
		
		panel.addUIElement(superheaderHolder).inTL(width*0.35f, 0);
		
		TooltipMakerAPI tableHolder = panel.createUIElement(width, 600, true);
		
		displayReport(tableHolder, panel, width, 10);
		panel.addUIElement(tableHolder).inTL(3, 48);
	}

	@Override
	public boolean hasSmallDescription() {
		return false;
	}

	@Override
	public boolean hasLargeDescription() {
		return true;
	}
	
	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("exerelin_misc", "intelTagDebug"));
		tags.add(DiplomacyProfileIntel.getString("intelTag"));
		return tags;
	}	

	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_1;
	}
	
	@Override
	protected String getName() {
		return getFactionForUIColors().getDisplayName() + " Strategic AI";
	}

	@Override
	public FactionAPI getFactionForUIColors() {
		return faction;
	}

	@Override
	public boolean isHidden() {
		return !ExerelinModPlugin.isNexDev;
	}

	public static String getString(String id) {
		return getString(id, false);
	}

	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_strategicAI", id, ucFirst);
	}

	// runcode exerelin.campaign.ai.StrategicAI.addIntel("tritachyon");
	public static StrategicAI addIntel(String factionId) {
		StrategicAI ai = new StrategicAI(Global.getSector().getFaction(factionId));
		ai.init();
		return ai;
	}
}

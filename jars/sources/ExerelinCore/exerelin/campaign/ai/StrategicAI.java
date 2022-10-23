package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.*;
import java.util.List;

@Log4j
public class StrategicAI extends BaseIntelPlugin {

	public static final String MEMORY_KEY = "$nex_strategicAI";
	public static final String UPDATE_NEW_CONCERNS = "new_concerns";
	public static final float MARGIN = 40;
	//public static final Object BUTTON_GENERATE_REPORT = new Object();
	//public static final Long lastReportTimestamp;

	@Getter	protected final FactionAPI faction;
	//protected transient TooltipMakerAPI savedReport;

	@Getter protected EconomicAIModule econModule;
	@Getter protected DiplomaticAIModule diploModule;
	@Getter protected MilitaryAIModule milModule;

	@Getter protected List<StrategicConcern> existingConcerns = new ArrayList<>();
	protected transient List<StrategicConcern> lastAddedConcerns = new ArrayList<>();
	protected IntervalUtil interval = new IntervalUtil(29, 31);


	public StrategicAI(FactionAPI faction) {
		this.faction = faction;
	}
	
	public StrategicAI init() {
		Global.getSector().getIntelManager().addIntel(this, true);
		faction.getMemoryWithoutUpdate().set(MEMORY_KEY, this);

		econModule = new EconomicAIModule(this, StrategicDefManager.ModuleType.ECONOMIC);
		diploModule = new DiplomaticAIModule(this, StrategicDefManager.ModuleType.DIPLOMATIC);
		milModule = new MilitaryAIModule(this, StrategicDefManager.ModuleType.MILITARY);
		econModule.init();
		diploModule.init();
		milModule.init();

		econModule.findConcerns();
		diploModule.findConcerns();
		milModule.findConcerns();

		return this;
	}

	protected Object readResolve() {
		lastAddedConcerns = new ArrayList<>();
		return this;
	}
	
	public static StrategicAI getAI(String factionId) {
		return (StrategicAI)Global.getSector().getFaction(factionId).getMemoryWithoutUpdate().get(MEMORY_KEY);
	}

	public String getFactionId() {
		return faction.getId();
	}

	public void addConcerns(Collection<StrategicConcern> concerns) {
		existingConcerns.addAll(concerns);
		lastAddedConcerns.addAll(concerns);
	}

	@Override
	protected void advanceImpl(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);

		econModule.advance(days);
		milModule.advance(days);
		diploModule.advance(days);

		interval.advance(days);
		if (!interval.intervalElapsed()) return;

		// update existing concerns, remove any if needed
		Iterator concernIter = existingConcerns.listIterator();
		while (concernIter.hasNext()) {
			StrategicConcern concern = (StrategicConcern)concernIter.next();
			concern.update();
			if (!concern.isValid()) concernIter.remove();
		}

		// find new concerns
		findConcerns(econModule);
		findConcerns(milModule);
		findConcerns(diploModule);

		// TODO: tell executive module to take action

		if (!lastAddedConcerns.isEmpty()) {
			sendUpdateIfPlayerHasIntel(UPDATE_NEW_CONCERNS, true, false);
			lastAddedConcerns.clear();
		}
	}

	protected void findConcerns(StrategicAIModule module) {
		List<StrategicConcern> newConcerns = module.findConcerns();
		if (!newConcerns.isEmpty()) {
			addConcerns(newConcerns);
		}
	}

	@Override
	protected void notifyEnding() {
		// only the military module is a listener rn
		ListenerManagerAPI listenerMan = Global.getSector().getListenerManager();
		listenerMan.removeListener(this);
		listenerMan.removeListener(econModule);
		listenerMan.removeListener(milModule);
		listenerMan.removeListener(diploModule);
	}

	/**
	 * This could be used to make the interval longer/shorter based on how many factions there are, but I cba to do anything like that rn
	 */
	@Deprecated
	protected void updateInterval() {

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
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		Object param = getListInfoParam();
		if (param == UPDATE_NEW_CONCERNS) {
			int numNewConcerns = lastAddedConcerns.size();
			info.addPara(getString("intelBulletUpdate"), initPad, tc, numNewConcerns + "");
		}
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

	public static void addAIIfNeeded(String factionId) {
		if (getAI(factionId) == null) {
			addIntel(factionId);
		}
	}

	public static void removeAI(String factionId) {
		StrategicAI ai = getAI(factionId);
		if (ai != null) {
			ai.endImmediately();
			Global.getSector().getFaction(factionId).getMemoryWithoutUpdate().unset(MEMORY_KEY);
		}
	}

	public static void addAIsIfNeeded() {
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			if (factionId.equals(Factions.PLAYER)) continue;
			addAIIfNeeded(factionId);
		}
	}
}

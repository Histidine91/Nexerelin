package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
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

	@Deprecated protected List<StrategicConcern> existingConcerns = new ArrayList<>();
	protected transient List<StrategicConcern> lastAddedConcerns = new ArrayList<>();
	protected transient List<StrategicConcern> lastRemovedConcerns = new ArrayList<>();
	protected IntervalUtil interval = new IntervalUtil(29, 31);


	public StrategicAI(FactionAPI faction) {
		this.faction = faction;
	}
	
	public StrategicAI init() {
		Global.getSector().getIntelManager().addIntel(this, true);
		Global.getSector().addScript(this);
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
		lastRemovedConcerns = new ArrayList<>();
		return this;
	}
	
	public static StrategicAI getAI(String factionId) {
		return (StrategicAI)Global.getSector().getFaction(factionId).getMemoryWithoutUpdate().get(MEMORY_KEY);
	}

	public String getFactionId() {
		return faction.getId();
	}

	public void addConcerns(Collection<StrategicConcern> concerns) {
		//existingConcerns.addAll(concerns);
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
		updateConcerns(econModule);
		updateConcerns(milModule);
		updateConcerns(diploModule);

		// find new concerns
		findConcerns(econModule);
		findConcerns(milModule);
		findConcerns(diploModule);

		// TODO: tell executive module to take action

		if (!lastAddedConcerns.isEmpty() || !lastRemovedConcerns.isEmpty()) {
			sendUpdateIfPlayerHasIntel(UPDATE_NEW_CONCERNS, true, false);
			lastAddedConcerns.clear();
			lastRemovedConcerns.clear();
		}
	}

	protected void findConcerns(StrategicAIModule module) {
		List<StrategicConcern> newConcerns = module.findConcerns();
		if (!newConcerns.isEmpty()) {
			addConcerns(newConcerns);
		}
	}

	protected void updateConcerns(StrategicAIModule module) {
		List<StrategicConcern> removedConcerns = module.updateConcerns();
		if (!removedConcerns.isEmpty()) {
			lastRemovedConcerns.addAll(removedConcerns);
		}
	}

	public List<StrategicConcern> getExistingConcerns() {
		List<StrategicConcern> concerns = new ArrayList<>();
		concerns.addAll(econModule.getCurrentConcerns());
		concerns.addAll(milModule.getCurrentConcerns());
		concerns.addAll(diploModule.getCurrentConcerns());
		return concerns;
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

	public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI panel, float width) {
		float pad = 3;
		float opad = 10;
		float sectionPad = 16;

		String str = getString("intelPara_daysToNextMeeting");
		tooltip.addPara(str, opad, Misc.getHighlightColor(), String.format("%.1f", interval.getIntervalDuration() - interval.getElapsed()));

		tooltip.addSectionHeading(getString("intelHeader_economy"), faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, sectionPad);
		try {
			econModule.generateReport(tooltip, panel, width);
		} catch (Exception ex) {
			log.error("Failed to generate economy report", ex);
		}

		tooltip.setParaInsigniaLarge();
		tooltip.addSectionHeading(getString("intelHeader_military"), faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, sectionPad);
		tooltip.setParaFontDefault();
		try {
			milModule.generateReport(tooltip, panel, width);
		} catch (Exception ex) {
			log.error("Failed to generate military report", ex);
		}

		tooltip.addSectionHeading(getString("intelHeader_diplomacy"), faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, sectionPad);
		try {
			diploModule.generateReport(tooltip, panel, width);
		} catch (Exception ex) {
			log.error("Failed to generate diplomacy report", ex);
		}

	}
	
	public void displayReport(TooltipMakerAPI tooltip, CustomPanelAPI panel, float width, float pad)
	{
		width -= MARGIN;
		generateReport(tooltip, panel, width);
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		TooltipMakerAPI superheaderHolder = panel.createUIElement(width/2, 40, false);
		TooltipMakerAPI superheader = superheaderHolder.beginImageWithText(getIcon(), 40);
		superheader.setParaOrbitronVeryLarge();
		superheader.addPara(getName(), 3);
		superheaderHolder.addImageWithText(3);
		
		panel.addUIElement(superheaderHolder).inTL(width*0.3f, 0);
		
		TooltipMakerAPI tableHolder = panel.createUIElement(width, 600, true);
		
		displayReport(tableHolder, panel, width, 10);
		panel.addUIElement(tableHolder).inTL(3, 48);
	}

	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		Object param = getListInfoParam();
		if (param == UPDATE_NEW_CONCERNS) {
			info.addPara(getString("intelBulletUpdate"), initPad);
			int numNewConcerns = lastAddedConcerns.size();
			if (numNewConcerns > 0) info.addPara(getString("intelBulletUpdateAdd"), 0, tc,
					numNewConcerns + "", lastAddedConcerns.toString());
			int numRemovedConcerns = lastRemovedConcerns.size();
			if (numRemovedConcerns > 0) info.addPara(getString("intelBulletUpdateRemove"), 0, tc,
					numRemovedConcerns + "", lastRemovedConcerns.toString());
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
		//tags.add(DiplomacyProfileIntel.getString("intelTag"));
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
	
	// runcode exerelin.campaign.ai.StrategicAI.purgeConcerns();
	public static void purgeConcerns() {
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			StrategicAI ai = getAI(factionId);
			if (ai != null) {
				for (StrategicConcern concern : ai.getExistingConcerns()) {
					concern.end();
				}
				ai.econModule.currentConcerns.clear();
				ai.milModule.currentConcerns.clear();
				ai.diploModule.currentConcerns.clear();
				ai.existingConcerns.clear();
			}
		}
	}
}

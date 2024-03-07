package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectory;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_StabilizePackage;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitDef;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.*;
import exerelin.utilities.NexFactionConfig.Morality;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.List;
import java.util.*;

@Log4j
public class DiplomacyProfileIntel extends BaseIntelPlugin {
	
	public static final float WEARINESS_MAX_FOR_COLOR = 10000;
	public static final float BADBOY_MAX_FOR_COLOR = 100;
	public static final float MARGIN = 40;
	public static final float ALIGNMENT_BUTTON_WIDTH = 40;
	public static final float ALIGNMENT_BUTTON_HEIGHT = 20;
	public static final Object BUTTON_DISPOSITION_DIR = new Object();
	
	public static final Set<String> NO_PROFILE_FACTIONS = new HashSet<>(Arrays.asList(new String[] {
		Factions.DERELICT, "nex_derelict"
	}));
	public static final List<String> DISPOSITION_SOURCE_KEYS = new ArrayList<>(Arrays.asList(new String[] {
		"overall", "base", "relationship", "alignments", /*"morality",*/ "events", "commonEnemies", "dominance", "revanchism", "traits"
	}));
	
	public static boolean showInwardDisposition = false;
	
	protected FactionAPI faction;
	protected transient Map<Alignment, Float> alignmentTemp;
	
	public static final Comparator ALIGNMENT_COMPARATOR = new NexUtils.PairWithFloatComparator(true);
	
	public DiplomacyProfileIntel(String factionId) {
		this.faction = Global.getSector().getFaction(factionId);
	}
	
	@Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() { 
		return true; 
	}
	
	public static List<MarketAPI> getClaimedMarkets(FactionAPI faction) {
		List<MarketAPI> claimed = new ArrayList<>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (market.getFaction() != faction 
					&& faction.getId().equals(NexUtilsMarket.getOriginalOwner(market))) 
			{
				claimed.add(market);
			}
		}
		return claimed;
	}
	
	protected LabelAPI generateClaimedMarketsPara(TooltipMakerAPI tooltip, float pad) {
		List<MarketAPI> claimed = getClaimedMarkets(faction);
		String str = null;
		if (claimed.isEmpty()) {
			str = getString("claimedMarketsNone");
			str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
			
			return tooltip.addPara(str, pad);
		}
		
		Collections.sort(claimed, Nex_FactionDirectory.MARKET_COMPARATOR_SIZE);
		
		List<String> highlights = new ArrayList<>();
		List<Color> highlightColors = new ArrayList<>();
		List<String> marketStrings = new ArrayList<>();
		
		str = getString("claimedMarkets");
		str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
		
		for (MarketAPI market : claimed) {
			int size = market.getSize();
			String entry = getString("claimedMarketsEntry");
			entry = StringHelper.substituteToken(entry, "$market", market.getName());
			entry = StringHelper.substituteToken(entry, "$size", size + "");			
			highlights.add(market.getName());
			highlights.add(size + "");
			highlightColors.add(market.getFaction().getBaseUIColor());
			highlightColors.add(Nex_FactionDirectory.getSizeColor(size));
			
			marketStrings.add(entry);
		}
		str = str + StringHelper.writeStringCollection(marketStrings, false, true);
		
		LabelAPI claimedMarketsLabel = tooltip.addPara(str, pad);
		claimedMarketsLabel.setHighlight(highlights.toArray(new String[]{}));
		claimedMarketsLabel.setHighlightColors(highlightColors.toArray(new Color[]{}));
		
		return claimedMarketsLabel;
	}
	
	protected LabelAPI generateAlignmentPara(TooltipMakerAPI tooltip, float pad) {
		List<Pair<Alignment, Float>> alignments = new ArrayList<>();
		NexFactionConfig conf = NexConfig.getFactionConfig(faction.getId());
		
		for (Map.Entry<Alignment, Float> tmp : conf.getAlignmentValues().entrySet())
		{
			if (tmp.getValue() == 0) continue;
			alignments.add(new Pair<>(tmp.getKey(), tmp.getValue()));			
		}
		Collections.sort(alignments, ALIGNMENT_COMPARATOR);
		
		List<String> highlights = new ArrayList<>();
		List<Color> highlightColors = new ArrayList<>();
		List<String> alignmentStrings = new ArrayList<>();
		
		for (Pair<Alignment, Float> alignEntry : alignments) {
			Alignment align = alignEntry.one;
			float strength = alignEntry.two;
			String strengthStr = (strength > 0? "+": "") + strength;
			
			String alignmentName = align.getName();
			alignmentStrings.add(alignmentName + " " + strengthStr);
			highlights.add(alignmentName);
			highlights.add(strengthStr);
			highlightColors.add(align.color);
			highlightColors.add(Misc.getRelColor(strength));
		}
		if (alignments.isEmpty()) {
			alignmentStrings.add(" none");
		}
		
		String str = getString("alignments", true) + ": " + StringHelper.writeStringCollection(alignmentStrings, false, true);
		
		LabelAPI alignmentLabel = tooltip.addPara(str, pad);
		alignmentLabel.setHighlight(highlights.toArray(new String[]{}));
		alignmentLabel.setHighlightColors(highlightColors.toArray(new Color[]{}));
		
		return alignmentLabel;
	}

	protected void addFleetPoolAndInvasionPoints(TooltipMakerAPI tooltip, float pad) {
		Color hl = Misc.getHighlightColor();
		String factionId = faction.getId();

		float nextPad = pad;
		if (FleetPoolManager.USE_POOL) {
			float pool = FleetPoolManager.getManager().getCurrentPool(factionId);
			float poolMax = FleetPoolManager.getManager().getMaxPool(factionId);
			float poolIncr = FleetPoolManager.getManager().getPointsLastTick(faction);
			String poolIncrStr = String.format("%.1f", poolIncr);
			tooltip.addPara(StrategicAI.getString("intelPara_fleetPool"), nextPad, hl, (int)pool + "", (int)poolMax + "", poolIncrStr);
			nextPad = 3;
		}
		{
			float points = InvasionFleetManager.getManager().getSpawnCounter(factionId);
			float pointsMax = InvasionFleetManager.getMaxInvasionPoints(faction);
			float pointsIncr = InvasionFleetManager.getPointsLastTick(faction);
			String pointsStr = Misc.getWithDGS(points);
			String pointsMaxStr = Misc.getWithDGS(pointsMax);
			String pointsIncrStr = "" + Math.round(pointsIncr);  //String.format("%.1f", pointsIncr);
			tooltip.addPara(StrategicAI.getString("intelPara_invasionPoints"), nextPad, hl, pointsStr, pointsMaxStr, pointsIncrStr);
		}
	}
	
	protected void addDispositionInfo(TooltipMakerAPI tooltip, float pad) {
		FactionAPI playerFaction = PlayerFactionStore.getPlayerFaction();
		if (playerFaction == faction || faction.isPlayerFaction()) return;
		
		String playerFactionId = playerFaction.getId();
		
		if (SectorManager.isFactionAlive(playerFactionId)) {
			DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(faction.getId());
			DiplomacyBrain.DispositionEntry disp = brain.getDisposition(playerFactionId);
			float dispVal = disp.disposition.getModifiedValue();
			String dispValStr = String.format("%.1f", dispVal);
			boolean good = dispVal >= DiplomacyBrain.LIKE_THRESHOLD;
			boolean bad = dispVal <= DiplomacyBrain.DISLIKE_THRESHOLD;
			
			Color dispColor = Misc.getGrayColor();
			if (good) dispColor = Misc.getPositiveHighlightColor();
			else if (bad) dispColor = Misc.getNegativeHighlightColor();
			
			String str = getString("disposition", true);
			str = StringHelper.substituteToken(str, "$playerFaction", playerFaction.getDisplayName(), true);
			str = StringHelper.substituteToken(str, "$disp", dispValStr);
			LabelAPI label = tooltip.addPara(str, pad);
			label.setHighlight(playerFaction.getDisplayName(), dispValStr);
			label.setHighlightColors(playerFaction.getBaseUIColor(), dispColor);
			
			// explain what the disposition means
			if (good) {
				str = getString("dispositionGood");
			} else if (bad) {
				str = getString("dispositionBad");
			} else {
				str = getString("dispositionNeutral");
			}
			str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
			str = StringHelper.substituteToken(str, "$isOrAre", faction.getDisplayNameIsOrAre());
			tooltip.setBulletedListMode(BULLET);
			tooltip.addPara(str, 3);
			
			if (!faction.isHostileTo(playerFaction) && dispVal < DiplomacyBrain.MAX_DISPOSITION_FOR_WAR && 
					faction.isAtBest(playerFaction, brain.getMaxRepForOpportunisticWar())) {
				str = getString("dispositionWarRisk");
				str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
				tooltip.addPara(str, 3);
			}
			
			unindent(tooltip);
		}
	}
	
	protected void addWarWearinessAndBadboy(TooltipMakerAPI tooltip, float pad) {
		float weariness = DiplomacyManager.getWarWeariness(faction.getId(), true);
		String wearinessStr = String.format("%.0f", weariness);
		
		String str = getString("warWeariness", true) + ": " + wearinessStr;
		float colorProgress = Math.min(weariness, WEARINESS_MAX_FOR_COLOR)/WEARINESS_MAX_FOR_COLOR;
		if (colorProgress > 1) colorProgress = 1;
		if (colorProgress < 0) colorProgress = 0;
		
		Color wearinessColor = Misc.interpolateColor(Color.WHITE, Misc.getNegativeHighlightColor(), colorProgress);
		tooltip.addPara(str, pad, wearinessColor, wearinessStr);
		
		tooltip.setBulletedListMode(BULLET);
		if (weariness >= NexConfig.minWarWearinessForPeace) {
			str = getString("wearinessEnoughForPeace");
			str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
			tooltip.addPara(str, 3);
		}
		if (weariness > DiplomacyBrain.MAX_WEARINESS_FOR_WAR) {
			str = getString("wearinessTooHighForWar");
			str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
			tooltip.addPara(str, 3);
		}
		unindent(tooltip);

		float badboy = DiplomacyManager.getBadboy(faction);
		String badboyStr = String.format("%.0f", weariness);
		str = getString("badboy", true) + ": " + badboyStr;
		colorProgress = Math.min(badboy, BADBOY_MAX_FOR_COLOR)/BADBOY_MAX_FOR_COLOR;
		if (colorProgress > 1) colorProgress = 1;
		if (colorProgress < 0) colorProgress = 0;

		Color badboyColor = Misc.interpolateColor(Color.WHITE, Misc.getNegativeHighlightColor(), colorProgress);
		tooltip.addPara(str, pad, badboyColor, badboyStr);
	}
	
	/**
	 * Sets up the buttons for player to set their faction's alignments.
	 * @param outer
	 * @param tooltip
	 * @param width
	 * @param pad
	 */
	public static void createAlignmentButtons(FactionAPI faction, CustomPanelAPI outer, TooltipMakerAPI tooltip, float width, float pad) {
		float opad = 10;
		boolean alreadySetAlignments = Global.getSector().getFaction(faction.getId()).getMemoryWithoutUpdate().contains(Alliance.MEMORY_KEY_ALIGNMENTS);
		float[] values = new float[] {-1f, -0.5f, 0f, 0.5f, 1f};
		int numAlignments = Alignment.getAlignments().size();
		
		float buttonPad = 3;
		float alignmentPanelWidth = (ALIGNMENT_BUTTON_WIDTH + buttonPad) * values.length;
		int panelsPerRow = (int)(width/alignmentPanelWidth);
		// avoid 5-1 rows
		if (panelsPerRow > 3 && numAlignments % panelsPerRow == 1)
			panelsPerRow--;
		
		int numRows = (int)Math.ceil(numAlignments/(float)panelsPerRow);
		float alignmentPanelHeight = (20 + ALIGNMENT_BUTTON_HEIGHT + 2);
		float height = (alignmentPanelHeight + buttonPad) * numRows;
		
		Color base = faction.getBaseUIColor(), bright = faction.getBrightUIColor(), dark = faction.getDarkUIColor();
		
		tooltip.addSectionHeading(getString("alignmentConfigHeader"), com.fs.starfarer.api.ui.Alignment.MID, opad);
		
		try {
			CustomPanelAPI panelAllAlignments = outer.createCustomPanel(width, height, null);
			List<CustomPanelAPI> panels = new ArrayList<>();
			
			Map<Alignment, Float> currAlign = NexConfig.getFactionConfig(faction.getId()).getAlignmentValues();

			for (Alignment align : Alignment.getAlignments()) {
				CustomPanelAPI panelAlignment = panelAllAlignments.createCustomPanel(alignmentPanelWidth, alignmentPanelHeight, null);
				TooltipMakerAPI alignmentNameHolder = panelAlignment.createUIElement(alignmentPanelWidth, 10, false);
				alignmentNameHolder.addPara(Misc.ucFirst(align.getName()), align.color, pad);
				panelAlignment.addUIElement(alignmentNameHolder).inTL(0, 0);
				
				TooltipMakerAPI last = null;
				for (float fv : values) {
					TooltipMakerAPI holder = panelAlignment.createUIElement(ALIGNMENT_BUTTON_WIDTH, ALIGNMENT_BUTTON_HEIGHT, false);
					ButtonAPI button = holder.addAreaCheckbox(fv + "", new Pair<>(align, fv), 
							base, dark, bright, ALIGNMENT_BUTTON_WIDTH, ALIGNMENT_BUTTON_HEIGHT, 0);
					button.setChecked(fv == currAlign.get(align));
					if (last == null)
						panelAlignment.addUIElement(holder).inBL(0, 0);
					else
						panelAlignment.addUIElement(holder).rightOfTop(last, buttonPad);
					last = holder;
				}
				NexUtilsGUI.placeElementInRows(panelAllAlignments, panelAlignment, panels, panelsPerRow, buttonPad);
				panels.add(panelAlignment);
			}
			tooltip.addCustom(panelAllAlignments, 3);
			
		} catch (Exception ex) {
			log.error(ex);
		}
	}
	
	/**
	 * Creates the button for toggling whether to show inwards disposition direction.
	 * @param mainPanel
	 * @param tooltip
	 * @param width
	 * @param pad
	 */
	protected void createDispositionDirButton(CustomPanelAPI mainPanel, TooltipMakerAPI tooltip, float width, float pad) {
		CustomPanelAPI row = mainPanel.createCustomPanel(width, 32, null);
		
		TooltipMakerAPI btnHolder = row.createUIElement(width, 24, false);
		Color base = faction.getBaseUIColor(), bright = faction.getBrightUIColor(), dark = faction.getDarkUIColor();
		ButtonAPI button = btnHolder.addAreaCheckbox(getString("dispButtonDir"), BUTTON_DISPOSITION_DIR, 
							base, dark, bright, 240, 24, 0);
		button.setChecked(showInwardDisposition);
		row.addUIElement(btnHolder).inTL(0, 0);
		
		TooltipMakerAPI iconHolder = row.createUIElement(32, 24, false);
		iconHolder.addImage(Global.getSettings().getSpriteName("ui", "nex_arrow_" + (showInwardDisposition ? "left" : "right")), 32, 0);
		row.addUIElement(iconHolder).inTL(240 + 3, 0);
		
		tooltip.addCustom(row, pad);
	}
	
	protected void createDispositionTable(boolean inwards, CustomPanelAPI mainPanel, TooltipMakerAPI tooltip, float width, float pad)
	{
		width -= MARGIN;
		tooltip.addSectionHeading(getString("dispTableHeader" + (inwards ? "Inwards" : "")), com.fs.starfarer.api.ui.Alignment.MID, pad);
		if (true || !faction.isPlayerFaction()) createDispositionDirButton(mainPanel, tooltip, width, 3);
		
		float cellWidth = 0.09f * width;
		tooltip.beginTable(faction, 20, StringHelper.getString("faction", true), 0.19f * width,
				getString("dispTableOverall"), cellWidth,
				getString("dispTableBase"), cellWidth,
				getString("dispTableRelationship"), cellWidth,
				getString("dispTableAlignments"), cellWidth,
				//getString("dispTableMorality"), cellWidth,
				getString("dispTableRecentEvents"), cellWidth,
				getString("dispTableCommonEnemies"), cellWidth,
				getString("dispTableDominance"), cellWidth,
				getString("dispTableRevanchism"), cellWidth,
				getString("dispTableTraits"), cellWidth
		);
		
		DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(faction.getId());
		boolean pirate = NexConfig.getFactionConfig(faction.getId()).pirateFaction;
		List<FactionAPI> factions = NexUtilsFaction.factionIdsToFactions(SectorManager.getLiveFactionIdsCopy());
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);
				
		for (FactionAPI otherFaction : factions) {
			if (otherFaction == faction) continue;
			NexFactionConfig conf = NexConfig.getFactionConfig(otherFaction.getId());
			if (!conf.playableFaction || conf.disableDiplomacy) continue;
			
			// normally pirates can't have diplomacy with non-pirates
			// so don't show cross-piracy disposition
			if ((pirate != conf.pirateFaction) && !NexConfig.allowPirateInvasions) 
			{
				continue;
			}
			
			List<Object> rowContents = new ArrayList<>();
			if (inwards) {
				brain = DiplomacyManager.getManager().getDiplomacyBrain(otherFaction.getId());
				//brain.updateAllDispositions(0);
			}
			DiplomacyBrain.DispositionEntry entry = brain.getDisposition(inwards ? faction.getId() : otherFaction.getId());
			
			MutableStat dispositionCopy = new MutableStat(0);
			dispositionCopy.applyMods(entry.disposition);
			
			// handling for preview of alignment changes
			boolean preview = alignmentTemp != null;
			// TODO: update alignment disposition using preview table when in preview mode
			if (preview) {
				float dfa = brain.getDispositionFromAlignments(alignmentTemp, conf.getAlignmentValues());
				dispositionCopy.modifyFlat("alignments", dfa, "Alignments");
			}
			
			// add faction
			//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
			rowContents.add(otherFaction.getBaseUIColor());
			rowContents.add(Misc.ucFirst(otherFaction.getDisplayName()));
			
			
			// add values
			for (String source : DISPOSITION_SOURCE_KEYS) {
				//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
				boolean overall = source.equals("overall");
				
				float subval = 0;
				float subvalOriginal = 0;	// used only in preview mode
				if (overall) {
					subval = dispositionCopy.getModifiedValue();
					subvalOriginal = entry.disposition.getModifiedValue();
				}
				else if (dispositionCopy.getFlatMods().containsKey(source)) {
					subval = dispositionCopy.getFlatStatMod(source).getValue();
					subvalOriginal = dispositionCopy.getFlatStatMod(source).getValue();
				}
				
				Color color = Misc.getTextColor();
				float compareValueHigh = 0;
				float compareValueLow = 0;
				
				if (preview && source.equals("alignments")) {
					compareValueHigh = subvalOriginal;
					compareValueLow = subvalOriginal;
				} else {
					if (overall) {
						compareValueHigh = DiplomacyBrain.LIKE_THRESHOLD;
						compareValueLow =  DiplomacyBrain.DISLIKE_THRESHOLD;
					} else {
						compareValueHigh = 1.5f;
						compareValueLow = -1.5f;
					}
				}
				
				if (preview && !source.equals("alignments")) {}		// do nothing
				else if (subval > compareValueHigh) color = Misc.getPositiveHighlightColor();
				else if (subval < compareValueLow) color = Misc.getNegativeHighlightColor();
								
				rowContents.add(color);
				
				if (subval != 0)
					rowContents.add(String.format("%.1f", subval));
				else
					rowContents.add("-");
			}
			tooltip.addRow(rowContents.toArray());
		}
		
		tooltip.addTable("", 0, 3);
		
		if (SectorManager.getManager().isHardMode()) {
			FactionAPI playerFaction = PlayerFactionStore.getPlayerFaction();
			if (faction != playerFaction && SectorManager.isFactionAlive(playerFaction.getId())) {
				String str = getString("dispFootnoteHardMode");
				String penalty = DiplomacyManager.getHardModeDispositionMod() + "";
				str = StringHelper.substituteToken(str, "$penalty", penalty);
				str = StringHelper.substituteToken(str, "$faction", playerFaction.getDisplayName());
				tooltip.addPara(str, 10, Misc.getNegativeHighlightColor(), penalty);
			}
		}
	}
	
	public void listTraits(TooltipMakerAPI tooltip, float pad) 
	{
		tooltip.addSectionHeading(getString("traitsHeader"), com.fs.starfarer.api.ui.Alignment.MID, pad);
				
		List<String> traits = DiplomacyTraits.getFactionTraits(faction.getId()); 
		// display all traits (for screenshots or such)
		/*
		traits = //new ArrayList<>();
		for (TraitDef trait : DiplomacyTraits.getTraits()) {
			traits.add(trait.id);
		}
		*/
		
		for (String traitId : traits) {
			TraitDef trait = DiplomacyTraits.getTrait(traitId);
			if (trait == null) continue;
			
			TooltipMakerAPI entry = tooltip.beginImageWithText(trait.icon, 40);
			entry.addPara(trait.name, trait.color, 0);
			entry.addPara(trait.desc, 3);
			tooltip.addImageWithText(pad);
		}
	}
	
	public void generateStabilizeCommodityPara(TooltipMakerAPI tooltip, Morality moral, float pad) {
		List<String> commodities = new ArrayList<>();
		NexFactionConfig conf = NexConfig.getFactionConfig(faction.getId());
		if (conf.stabilizeCommodities != null) {
			commodities.addAll(conf.stabilizeCommodities);
		}
		else if (moral == Morality.GOOD || moral == Morality.NEUTRAL) {
			commodities.addAll(Nex_StabilizePackage.COMMODITIES_RELIEF);
		} else {
			commodities.addAll(Nex_StabilizePackage.COMMODITIES_REPRESSION);
		}
		List<String> commoditiesStrings = StringHelper.commodityIdListToCommodityNameList(commodities);
		
		String str = String.format(getString("stabilizationCommodity"), 
				StringHelper.writeStringCollection(commoditiesStrings));
		
		tooltip.addPara(str, pad, Misc.getHighlightColor(), commoditiesStrings.toArray(new String[0]));
		tooltip.beginIconGroup();
		try {
			MarketAPI temp = Global.getSector().getEconomy().getMarketsCopy().get(0);
			for (String commodity : commodities) {
				CommodityOnMarketAPI com = temp.getCommodityData(commodity);
				tooltip.addIcons(com, 1, IconRenderMode.NORMAL);
			}
		} catch (Exception ex) {}
		tooltip.addIconGroup(32, 0);
	}

	// adapted from Starship Legends' BattleReport
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float pad = 3;
		float opad = 10;
		
		// make sure intel is up-to-date
		DiplomacyManager.getManager().getDiplomacyBrain(faction.getId()).updateAllDispositions(0);
		
		// holder for all other elements
		TooltipMakerAPI outer = panel.createUIElement(width, height, true);
		//CustomPanelAPI inner = panel.createCustomPanel(width, 1024, null);
		//outer.addCustom(inner, 0);
		
		//TooltipMakerAPI firstSection = inner.createUIElement(width, 360, false);
		//inner.addUIElement(firstSection);
		outer.addSectionHeading(getSmallDescriptionTitle(), faction.getBaseUIColor(), 
				faction.getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
		
		// flag and basic info (alignments, morality, revanchist claims)
		TooltipMakerAPI flagAndBasicInfo = outer.beginImageWithText(faction.getLogo(), 128);
		generateAlignmentPara(flagAndBasicInfo, 0);
		
		// morality
		Morality moral = NexConfig.getFactionConfig(faction.getId()).morality;
		
		//String moralId = moral.toString();
		//String moralStr = getString("morality_" + moralId.toLowerCase(), true);
		//String str = getString("morality", true) + ": " + moralStr;
		//flagAndBasicInfo.addPara(str, opad, moral.color, moralStr);
		
		generateClaimedMarketsPara(flagAndBasicInfo, opad);
		generateStabilizeCommodityPara(flagAndBasicInfo, moral, opad);
		
		// end flag and basic info
		outer.addImageWithText(pad);
		
		// important notes for player
		addDispositionInfo(outer, opad);
		addFleetPoolAndInvasionPoints(outer, opad);
		addWarWearinessAndBadboy(outer, opad);
		
		// disposition table
		// player: add alignment buttons
		if (faction.isPlayerFaction()) {
			createAlignmentButtons(faction, panel, outer, width, pad);
			createDispositionTable(showInwardDisposition, panel, outer, width, opad);
		}
		else {
			createDispositionTable(showInwardDisposition, panel, outer, width, opad);
		}		
		
		// list traits
		listTraits(outer, opad);
		
		panel.addUIElement(outer).inTL(0, 0);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_DISPOSITION_DIR) {
			showInwardDisposition = !showInwardDisposition;
			ui.updateUIForItem(this);
			return;
		}
		
		if (buttonId instanceof Pair) {
			updateDisposition(buttonId, faction);
			
			ui.updateUIForItem(this);
			return;
		}
	}

	public static void updateDisposition(Object buttonId, FactionAPI faction) {
		Pair<Alignment, Float> pair = (Pair<Alignment, Float>)buttonId;
		MutableStat align = NexConfig.getFactionConfig(faction.getId()).getAlignments().get(pair.one);
		align.modifyFlat("playerSet", pair.two, StringHelper.getString("exerelin_alliances", "alignmentModifierPlayerSet"));

		// update alignments of other factions so they're reflected in the intel display
		List<String> factions = SectorManager.getLiveFactionIdsCopy();
		for (DiplomacyBrain brain : DiplomacyManager.getManager().getDiplomacyBrains().values()) {
			for (String factionId : factions) {
				brain.updateDispositionFromAlignment(brain.getDisposition(factionId).disposition, factionId);
			}
		}
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_diplomacyProfile", id, ucFirst);
	}
	
	@Override
	public String getName() {
		return Misc.ucFirst(faction.getDisplayName()) + " " + getString("title");
	}
	
	@Override
	public String getSortString() {
		return faction.getDisplayName();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return faction;
	}
	
	@Override
	public String getIcon() {
		return faction.getCrest();
	}
	
	@Override
	public boolean isHidden() {
		// Hide pirate diplomacy profiles of pirate factions, unless player is also a pirate?
		// nah, it does show some important things like revanchist claims
		/*
		boolean isPirate = ExerelinUtilsFaction.isPirateFaction(PlayerFactionStore.getPlayerFactionId());
		if (!isPirate && ExerelinUtilsFaction.isPirateFaction(faction.getId()))
			return true;
		*/
		return false;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(getString("intelTag"));
		return tags;
	}
	
	@Override
	public IntelSortTier getSortTier() {
		if (faction.isPlayerFaction()) return IntelSortTier.TIER_2;
		return super.getSortTier();
	}
	
	public static DiplomacyProfileIntel createEvent(String factionId) {
		if (NO_PROFILE_FACTIONS.contains(factionId))
			return null;
		
		DiplomacyProfileIntel profile = new DiplomacyProfileIntel(factionId);
		Global.getSector().getIntelManager().addIntel(profile, true);
		profile.setNew(false);
		return profile;
	}
}

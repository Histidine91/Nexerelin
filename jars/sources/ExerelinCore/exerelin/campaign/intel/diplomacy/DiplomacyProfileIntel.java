package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectory;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitDef;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.Morality;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DiplomacyProfileIntel extends BaseIntelPlugin {
	
	public static final float WEARINESS_MAX_FOR_COLOR = 10000;
	public static final float MARGIN = 40;
	public static final Set<String> NO_PROFILE_FACTIONS = new HashSet<>(Arrays.asList(new String[] {
		Factions.PLAYER, Factions.DERELICT
	}));
	public static final List<String> DISPOSITION_SOURCE_KEYS = new ArrayList<>(Arrays.asList(new String[] {
		"overall", "base", "relationship", "alignments", /*"morality",*/ "events", "commonEnemies", "dominance", "revanchism", "traits"
	}));
	
	protected FactionAPI faction;
	
	public static final Comparator<Pair<Alignment, Float>> ALIGNMENT_COMPARATOR = new Comparator<Pair<Alignment, Float>>()
	{
		@Override
		public int compare(Pair<Alignment, Float> align1, Pair<Alignment, Float> align2)
		{
			return align2.two.compareTo(align1.two);
		}
	};
	
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
	
	protected List<MarketAPI> getClaimedMarkets() {
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
		List<MarketAPI> claimed = getClaimedMarkets();
		String str = null;
		if (claimed.isEmpty()) {
			str = getString("claimedMarketsNone");
			str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
			
			return tooltip.addPara(str, pad);
		}
		
		Color h = Misc.getHighlightColor();
		
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
		
		for (Map.Entry<Alignment, Float> tmp : conf.alignments.entrySet())
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
			
			String alignmentName = StringHelper.getString("exerelin_alliances", "alignment_" 
				+ align.toString().toLowerCase(Locale.ROOT), true);
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
	
	protected void addDispositionInfo(TooltipMakerAPI tooltip, float pad) {
		FactionAPI playerFaction = PlayerFactionStore.getPlayerFaction();
		if (playerFaction == faction) return;
		
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
	
	public void addWarWearinessInfo(TooltipMakerAPI tooltip, float pad) {
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
	}
	
	public void createDispositionTable(TooltipMakerAPI tooltip, float width, float pad) 
	{
		width -= MARGIN;
		tooltip.addSectionHeading(getString("dispTableHeader"), com.fs.starfarer.api.ui.Alignment.MID, pad);
		
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
			DiplomacyBrain.DispositionEntry entry = brain.getDisposition(otherFaction.getId());
			
			// add faction
			//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
			rowContents.add(otherFaction.getBaseUIColor());
			rowContents.add(Misc.ucFirst(otherFaction.getDisplayName()));
			
			// add values
			for (String source : DISPOSITION_SOURCE_KEYS) {
				//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
				boolean overall = source.equals("overall");
				
				float subval = 0;
				if (overall) {
					subval = entry.disposition.getModifiedValue();
				}
				else if (entry.disposition.getFlatMods().containsKey(source)) {
					subval = entry.disposition.getFlatStatMod(source).getValue();
				}
				
				Color color = Misc.getTextColor();
				if (overall) {
					if (subval > DiplomacyBrain.LIKE_THRESHOLD)
						color = Misc.getPositiveHighlightColor();
					else if (subval < DiplomacyBrain.DISLIKE_THRESHOLD)
						color = Misc.getNegativeHighlightColor();
				} else {
					if (subval > 1.5f)
						color = Misc.getPositiveHighlightColor();
					else if (subval < -1.5f)
						color = Misc.getNegativeHighlightColor();
				}
				
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
		CommoditySpecAPI commodity;
		if (moral == Morality.GOOD || moral == Morality.NEUTRAL) {
			commodity = Global.getSettings().getCommoditySpec(Commodities.FOOD);
		} else {
			commodity = Global.getSettings().getCommoditySpec(Commodities.HAND_WEAPONS);
		}
		TooltipMakerAPI tooltip2 = tooltip.beginImageWithText(commodity.getIconName(), 24);
		tooltip2.addPara(getString("stabilizationCommodity"), 0, Misc.getHighlightColor(), commodity.getLowerCaseName());
		tooltip.addImageWithText(pad);
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
		String moralId = moral.toString();
		String moralStr = getString("morality_" + moralId.toLowerCase(), true);
		
		String str = getString("morality", true) + ": " + moralStr;
		//flagAndBasicInfo.addPara(str, opad, moral.color, moralStr);
		
		generateClaimedMarketsPara(flagAndBasicInfo, opad);
		generateStabilizeCommodityPara(flagAndBasicInfo, moral, opad);
		
		// end flag and basic info
		outer.addImageWithText(pad);
		
		// important notes for player
		addDispositionInfo(outer, opad);
		addWarWearinessInfo(outer, opad);
		
		// disposition table
		createDispositionTable(outer, width, opad);
		
		// list traits
		listTraits(outer, opad);
		
		panel.addUIElement(outer).inTL(0, 0);
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        String title = getSmallDescriptionTitle();

        info.addPara(title, Misc.getBasePlayerColor(), 0f);
    }
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_diplomacyProfile", id, ucFirst);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
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
	
	public static DiplomacyProfileIntel createEvent(String factionId) {
		if (NO_PROFILE_FACTIONS.contains(factionId))
			return null;
		
		DiplomacyProfileIntel profile = new DiplomacyProfileIntel(factionId);
		Global.getSector().getIntelManager().addIntel(profile, true);
		profile.setNew(false);
		return profile;
	}
}

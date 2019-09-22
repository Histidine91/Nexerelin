package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AllianceIntel extends BaseIntelPlugin {
	private static Logger log = Global.getLogger(AllianceIntel.class);
	
	// everything protected out of habit
	protected FactionAPI faction1;
	protected FactionAPI faction2;
	protected String allianceId;
	// more permanent store, since we may decide to no longer have alliance be accessible by ID once dissolved
	protected String allianceName;	
	protected boolean isDissolved = false;

	public AllianceIntel(FactionAPI faction1, FactionAPI faction2, String allianceId, String allianceName) {
		log.info("Creating Alliance Intel");
		this.faction1 = faction1;
		this.faction2 = faction2;
		this.allianceId = allianceId;
		this.allianceName = allianceName;
		Global.getSector().getIntelManager().addIntel(this, true);
		Map<String, Object> params = new HashMap<>();
		params.put("type", UpdateType.FORMED);
		this.sendUpdateIfPlayerHasIntel(params, false);
	}
	
	@Override
	public String getSortString() {
		//what does this string do?
		return "Alliances";
	}
	
	@Override
	//This is the message info that shows on the left
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		UpdateType updateType = null;
		if (listInfoParam != null)
		{
			Map<String, Object> params = (Map<String, Object>)listInfoParam;
			updateType = (UpdateType)params.get("type");
			String factionId1 = (String)params.get("faction1");
			String factionId2 = (String)params.get("faction2");
			
			if (factionId1 != null)
				faction1 = Global.getSector().getFaction(factionId1);
			if (factionId2 != null)
				faction2 = Global.getSector().getFaction(factionId2);
		}
		
		Color c = getTitleColor(mode);
		float pad = 0f;
		float initPad = 3;

		info.addPara(getName(), c, pad);

		Color tc = getBulletColorForMode(mode);
		Color hl = Misc.getHighlightColor();
		bullet(info);
		String str, sub1, sub2;
		
		if (updateType != null) {
			switch (updateType) {
				case FORMED:
					ExerelinUtilsFaction.addFactionNamePara(info, initPad, tc, faction1);
					ExerelinUtilsFaction.addFactionNamePara(info, pad, tc, faction2);

					sub1 = AllianceManager.getAllianceByUUID(allianceId).getAllianceMarketSizeSum() + "";
					str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "intelStrengthPointShort", 
							"$size", sub1);
					info.addPara(str, pad, tc, hl, sub1);

					break;
				case JOINED:
				case LEFT:
					sub1 = Misc.ucFirst(faction1.getDisplayName());
					str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", 
							updateType == UpdateType.JOINED ? "intelJoinedPoint" : "intelLeftPoint", 
							"$Faction", sub1);
					info.addPara(str, initPad, tc, faction1.getBaseUIColor(), sub1);

					sub1 = AllianceManager.getAllianceByUUID(allianceId).getMembersCopy().size() + "";
					sub2 = AllianceManager.getAllianceByUUID(allianceId).getAllianceMarketSizeSum() + "";
					str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "intelStrengthPointUpdate", 
							"$num", sub1);
					str = StringHelper.substituteToken(str, "$size", sub2);
					info.addPara(str, pad, tc, hl, sub1, sub2);
					break;
				case DISSOLVED:
					//str = StringHelper.getString(Misc.ucFirst("dissolved"));
					//info.addPara(str, pad);
					break;
			}
		} else if (!isDissolved) {
			sub1 = AllianceManager.getAllianceByUUID(allianceId).getMembersCopy().size() + "";
			sub2 = AllianceManager.getAllianceByUUID(allianceId).getAllianceMarketSizeSum() + "";
			str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "intelStrengthPoint", 
					"$num", sub1);
			str = StringHelper.substituteToken(str, "$size", sub2);
			info.addPara(str, initPad, tc, hl, sub1, sub2);
		}
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;

		String str = "";
		Color h = Misc.getHighlightColor();
		
		if (isDissolved)
		{
			info.addImages(width, 128, opad, opad, faction1.getCrest(), faction2.getCrest());
			str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", 
					"intelDissolvedDesc", "$alliance", allianceName);
			info.addPara(str, opad);
			return;
		}
		
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		if (alliance == null)
		{
			info.addPara("ERROR: Unable to find alliance " + allianceName + "!", opad);
			return;
		}
		
		printMemberCrests(info, alliance, width, opad);
		
		// alliance info paragraph
		String numMembers = alliance.getMembersCopy().size() + "";
		String numMarkets = alliance.getNumAllianceMarkets() + "";
		String size = alliance.getAllianceMarketSizeSum() + "";
		Map<String, String> replace = new HashMap<>();
		replace.put("$alliance", alliance.getName());
		replace.put("$numMembers", numMembers);
		replace.put("$numMarkets", numMarkets);
		replace.put("$marketSizeSum", size);
		str = StringHelper.getStringAndSubstituteTokens("exerelin_alliances", "intelDesc", replace);
		info.addPara(str, opad, h, alliance.getName(), numMembers, numMarkets, size);
		
		String alignmentName = getString("alignment_" 
				+ alliance.getAlignment().toString().toLowerCase(Locale.ROOT), true);
		str = getString("alignment", true) + ": " + alignmentName;
		info.addPara(str, opad, alliance.getAlignment().color, alignmentName);
		
		str = getString("intelMembersHeader");
		info.addSectionHeading(str, Alignment.MID, opad);
		
		// print members
		List<String> members = new ArrayList<>(alliance.getMembersCopy());
		Collections.sort(members);
		for (String factionId : members)
		{
			printMemberInfo(info, factionId);
		}
		
		printFactionJoinEligibility(info, alliance, opad);
	}
	
	public void printFactionJoinEligibility(TooltipMakerAPI info, Alliance alliance, float pad) 
	{
		String str = getString("intelJoinabilityHeader");
		info.addSectionHeading(str, Alignment.MID, pad);
		
		List<String> canJoin = new ArrayList<>();
		List<String> tooLowRelationship = new ArrayList<>();
		List<String> wrongAlignment = new ArrayList<>();
		boolean pirateDiplomacy = ExerelinConfig.allowPirateInvasions;
		Set<String> members = alliance.getMembersCopy();
		
		for (String factionId : SectorManager.getLiveFactionIdsCopy())
		{
			if (members.contains(factionId)) continue;
			if (!pirateDiplomacy && ExerelinUtilsFaction.isPirateFaction(factionId))
				continue;
			
			boolean compatible = ExerelinConfig.ignoreAlignmentForAlliances || 
					AllianceManager.getAlignmentCompatibilityWithAlliance(factionId, alliance) 
					> AllianceManager.MIN_ALIGNMENT_TO_JOIN_ALLIANCE;
			if (!compatible) {
				wrongAlignment.add(factionId);
				continue;
			}
			FactionAPI faction = Global.getSector().getFaction(factionId);
			
			boolean hostile = false;
			for (String member : alliance.getMembersCopy()) {
				if (faction.isHostileTo(member)) {
					hostile = true;
					break;
				}
			}
			if (hostile) {
				tooLowRelationship.add(factionId);
				continue;
			}
			
			boolean enoughAvgRep = alliance.getAverageRelationshipWithFaction(factionId) 
					>= AllianceManager.MIN_RELATIONSHIP_TO_JOIN;
			if (!enoughAvgRep) {
				tooLowRelationship.add(factionId);
				continue;
			}
			canJoin.add(factionId);
		}
		
		printFactionList(info, canJoin, getString("intelCanJoinList"), pad);
		printFactionList(info, tooLowRelationship, getString("intelTooLowRelationshipList"), pad);
		printFactionList(info, wrongAlignment, getString("intelWrongAlignmentList"), pad);
	}
	
	public void printFactionList(TooltipMakerAPI info, List<String> factions, String initial, float pad)
	{
		if (factions.isEmpty()) return;
		
		String str = initial + ": ";
		List<String> names = new ArrayList<>();
		List<Color> colors = new ArrayList<>();
		for (String factionId : factions) {
			FactionAPI faction = Global.getSector().getFaction(factionId);
			names.add(faction.getDisplayName());
			colors.add(faction.getBaseUIColor());
		}
		str = str + StringHelper.writeStringCollection(names);
		
		LabelAPI label = info.addPara(str, pad);
		label.setHighlight(names.toArray(new String[]{}));
		label.setHighlightColors(colors.toArray(new Color[]{}));
	}
	
	public static void printMemberCrests(TooltipMakerAPI info, Alliance alliance, float width, float padding)
	{
		List<String> members = new ArrayList<>(alliance.getMembersCopy());
		Collections.sort(members);
		List<String> crests = new ArrayList<>();
		int count = 0;
		for (String factionId : members)
		{
			crests.add(Global.getSector().getFaction(factionId).getCrest());
			count++;
			if (count >= 8) break;
		}
		
		if (count <= 0) return;
		
		// use two rows for crests if alliance has > 4 members
		int rows = 1;
		int row1Num = count, row2Num = 0; 
		if (count > 4) {
			rows = 2;
			row2Num = count/2;
			row1Num = count - row2Num;
		}
		String[] crestsArray = crests.toArray(new String[0]);
		
		info.addImages(width, (int)(256/row1Num), padding, padding, Arrays.copyOfRange(crestsArray, 0, row1Num));
		if (rows == 2)
			info.addImages(width, (int)(256/row1Num), padding, padding, Arrays.copyOfRange(crestsArray, row1Num, crestsArray.length));
	}
	
	protected void printMemberInfo(TooltipMakerAPI info, String factionId)
	{
		float opad = 10f;
		Color hl = Misc.getHighlightColor();
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		String str = getString("intelMemberEntry");
		String name = Misc.ucFirst(faction.getDisplayName());
		String num = ExerelinUtilsFaction.getFactionMarkets(factionId).size() + "";
		String sizeSum = ExerelinUtilsFaction.getFactionMarketSizeSum(factionId) + "";
		Map<String, String> sub = new HashMap<>();
		sub.put("$faction", name);
		sub.put("$num", num);
		sub.put("$size", sizeSum);
		
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI para = info.addPara(str, opad);
		para.setHighlight(name, num, sizeSum);
		para.setHighlightColors(faction.getBaseUIColor(), hl, hl);
	}

	protected String getName() {
		String str = StringHelper.getStringAndSubstituteToken("exerelin_alliances", "intelTitle", "$name", allianceName);
		if (isDissolved)
		{
			str += " - " + StringHelper.getString("dissolved", true);
		}
		else if (listInfoParam != null)
		{
			Map<String, Object> params = (Map<String, Object>)listInfoParam;
			if ((UpdateType)params.get("type") == UpdateType.FORMED)
				str += " - " + StringHelper.getString("formed", true);
		}
		
		return str;
	}
	
	@Override
	public void sendUpdateIfPlayerHasIntel(Object listInfoParam, boolean onlyIfImportant, boolean sendIfHidden) {
		Map<String, Object> params = (Map<String, Object>)listInfoParam;
		if ((UpdateType)params.get("type") == UpdateType.DISSOLVED)
			isDissolved = true;
		super.sendUpdateIfPlayerHasIntel(listInfoParam, onlyIfImportant, sendIfHidden);
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("alliances", true));
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		if (alliance != null)
		{
			for (String factionId : alliance.getMembersCopy())
			{
				tags.add(factionId);
			}
		}
		return tags;
	}

	@Override
	public String getIcon() {
		return Global.getSettings().getSpriteName("intel", "alliance");
	}

	@Override
	protected float getBaseDaysAfterEnd() {
		return 30;
	}
	
	public String getString(String id) {
		return getString(id, false);
	}
	
	public String getString(String id, boolean ucFirst) {
		return StringHelper.getString("exerelin_alliances", id, ucFirst);
	}
	
	public enum UpdateType {
		DISSOLVED,
		FORMED,
		JOINED,
		LEFT
	}
}

package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceTypeEnum;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AllianceChangedIntel extends BaseIntelPlugin {
	public static Logger log = Global.getLogger(AllianceChangedIntel.class);

	FactionAPI faction1;
	FactionAPI faction2;
	String allianceId;
	private AllianceTypeEnum allianceTypeEnum;

	public AllianceChangedIntel(FactionAPI faction1, FactionAPI faction2, String allianceId, AllianceTypeEnum allianceTypeEnum) {
		log.info("Creating Alliance Intel");
		this.faction1 = faction1;
		this.faction2 = faction2;
		this.allianceId = allianceId;
		this.allianceTypeEnum = allianceTypeEnum;
		Global.getSector().getIntelManager().addIntel(this);
	}

	@Override
	public String getSortString() {
		//TODO what does this string do?
		return "Alliances";
	}

	@Override
	//This is the message info that shows on the left
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		float pad = 0f;

		info.addPara(getName(), c, pad);

		Color tc = getBulletColorForMode(mode);
		bullet(info);

		addParaForFaction(info, pad, tc, faction1);
		if(faction2 != null) addParaForFaction(info, pad, tc, faction2);

		//Maybe something to show how many members are currently in alliance?
//		if(AllianceTypeEnum.DISSOLVED != allianceTypeEnum) {
//			info.addPara("Members: ", pad);
//			bullet(info);
//			Set<String> factions = AllianceManager.getAllianceByUUID(allianceId).getMembersCopy();
//			SectorAPI sector = Global.getSector();
//			for(String factionName : factions) {
//				FactionAPI faction = sector.getFaction(factionName);
//				addParaForFaction(info, pad, tc, faction);
//			}
//		}
//
//
//		if(AllianceTypeEnum.LEFT== allianceTypeEnum) {
//			unindent(info);
//			info.addPara("Left: ", pad);
//			bullet(info);
//			addParaForFaction(info, pad, tc, faction1);
//		}

	}

	private static void addParaForFaction(TooltipMakerAPI info, float pad, Color color, FactionAPI faction) {
		String name = faction.getDisplayName();
		info.addPara(name, pad, color, faction.getBaseUIColor(), name);
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;

		String str = "";
		Map<String, String> map = new HashMap<>();

		map.put("$TheFaction1", faction1.getDisplayNameWithArticleWithoutArticle());
		Alliance alliance = AllianceManager.getAllianceByUUID(allianceId);
		map.put("$alliance", alliance.getName());
		if(AllianceTypeEnum.FORMED == allianceTypeEnum) {
			info.addImages(width, 128, opad, opad, faction1.getCrest(), faction2.getCrest());
			str = "allianceFormedDesc";
			map.put("$TheFaction2", faction2.getDisplayNameWithArticleWithoutArticle());
		} else if (AllianceTypeEnum.JOINED == allianceTypeEnum) {
			info.addImages(width, 128, opad, opad, faction1.getCrest());
			str = "allianceJoinedDesc";
		} else if (AllianceTypeEnum.LEFT== allianceTypeEnum) {
			info.addImages(width, 128, opad, opad, faction1.getCrest());
			str = "allianceLeftDesc";
		} else if (AllianceTypeEnum.DISSOLVED == allianceTypeEnum) {
			str = "allianceDissolvedDesc";
		}

		map.put("$numMembers", alliance.getMembersCopy().size() + "");
		map.put("$numMarkets", alliance.getNumAllianceMarkets() + "");
		map.put("$marketSizeSum", alliance.getAllianceMarketSizeSum() + "");


		String para = StringHelper.getStringAndSubstituteTokens("exerelin_alliances", str, map);

		Color h = Misc.getHighlightColor();
		LabelAPI label = info.addPara(para, opad);
		label.setHighlight(faction1.getDisplayNameWithArticleWithoutArticle(), alliance.getName());
		label.setHighlightColors(faction1.getBaseUIColor(), h);

		if(AllianceTypeEnum.FORMED == allianceTypeEnum) {
			label.setHighlight(faction1.getDisplayNameWithArticleWithoutArticle(), alliance.getName(), faction2.getDisplayNameWithArticleWithoutArticle());
			label.setHighlightColors(faction1.getBaseUIColor(), h, faction2.getBaseUIColor());
		}

	}

	private String getName() {
		String str = "allianceChanged";
		if(AllianceTypeEnum.FORMED == allianceTypeEnum) {
			str = "allianceFormed";
		} else if(AllianceTypeEnum.DISSOLVED == allianceTypeEnum) {
			str = "allianceDissolved";
		}
		String allianceName = AllianceManager.getAllianceByUUID(allianceId).getName();
		return StringHelper.getStringAndSubstituteToken("exerelin_alliances", str, "$alliance", allianceName);
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add("Alliances");
		tags.add(faction1.getId());
		if(faction2 != null) tags.add(faction2.getId());
		return tags;
	}

	@Override
	public String getIcon() {
		//TODO hardcoded for now
		return "graphics/icons/intel/star.png";
	}

	@Override
	protected void notifyEnded() {
		super.notifyEnded();
		Global.getSector().removeScript(this);
	}
}

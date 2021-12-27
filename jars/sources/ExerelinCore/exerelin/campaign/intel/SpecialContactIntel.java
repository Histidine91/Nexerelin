package exerelin.campaign.intel;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Contacts that can't be dismissed or prioritized and don't count towards the limit.
 */
public class SpecialContactIntel extends ContactIntel {
	
	public SpecialContactIntel(PersonAPI person, MarketAPI market) {
		super(person, market);
		state = ContactState.SUSPENDED;	// so they don't count towards limit
	}
	
	// don't lose importance when relocating
	@Override
	public void relocateToMarket(MarketAPI other, boolean withIntelUpdate) {
		super.relocateToMarket(other, withIntelUpdate);
		person.setImportance(person.getImportance().next());
	}
	
	@Override
	public void doPeriodicCheck() {
		super.doPeriodicCheck();
		person.getMemoryWithoutUpdate().set(BaseMissionHub.CONTACT_SUSPENDED, false);
	}
	
	public String getName() {
		String name = StringHelper.getString("nex_contacts", "nameSpecialContact") + ": " + person.getNameString();
		if (isEnding() || isEnded()) name += " - " + StringHelper.getString("lost", true);
		return name;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		//info.addImage(person.getPortraitSprite(), width, 128, opad);
		
		FactionAPI faction = person.getFaction();
		info.addImages(width, 128, opad, opad, person.getPortraitSprite(), faction.getCrest());
		
		float relBarWidth = 128f * 2f + 10f;
		float importanceBarWidth = relBarWidth;
		
		float indent = 25;
		info.addSpacer(0).getPosition().setXAlignOffset(indent);
		
		//info.addRelationshipBar(person, relBarWidth, opad);
		
		relBarWidth = (relBarWidth - 10f) / 2f;
		info.addRelationshipBar(person, relBarWidth, opad);
		float barHeight = info.getPrev().getPosition().getHeight();
		info.addRelationshipBar(person.getFaction(), relBarWidth, 0f);
		UIComponentAPI prev = info.getPrev();
		prev.getPosition().setYAlignOffset(barHeight);
		prev.getPosition().setXAlignOffset(relBarWidth + 10f);
		info.addSpacer(0f);
		info.getPrev().getPosition().setXAlignOffset(-(relBarWidth + 10f));
		
		info.addImportanceIndicator(person.getImportance(), importanceBarWidth, opad);
		addImportanceTooltip(info);
		info.addSpacer(0).getPosition().setXAlignOffset(-indent);
		
		/*
			"$name was $postArticle $postName $onOrAt $market, a colony controlled by $theFaction."
			"This colony has decivilized, and you've since lost contact with $himOrHer."
			"$name is $postArticle $postName, and can be found $onOrAt $market, a size $size colony controlled by $theFaction".
		*/
		
		Map<String, String> subs = new HashMap<>();
		subs.put("$name", person.getNameString());
		subs.put("$postArticle", person.getPostArticle());
		subs.put("$postName", person.getPost().toLowerCase());
		subs.put("$onOrAt", market.getOnOrAt());
		subs.put("$market", market.getName());
		subs.put("$size", market.getSize() + "");
		subs.put("$theFaction", marketFaction.getDisplayNameLongWithArticle());
		subs.put("$himOrHer", person.getHimOrHer());
		
		if (market != null && state == ContactState.LOST_CONTACT_DECIV) {
			String str = StringHelper.getString("nex_contacts", "intelDescDeciv1");
			str = StringHelper.substituteTokens(str, subs);
			info.addPara(str, opad, marketFaction.getBaseUIColor(),
					Misc.ucFirst(marketFaction.getDisplayNameWithArticleWithoutArticle()));
			info.addPara(StringHelper.getString("nex_contacts", "intelDescDeciv2"), opad);
		} else {
			if (market != null) {
				String str = StringHelper.getString("nex_contacts", "intelDesc");
				str = StringHelper.substituteTokens(str, subs);
				LabelAPI label = info.addPara(str, opad, marketFaction.getBaseUIColor(),
						market.getSize() + "",
					Misc.ucFirst(marketFaction.getDisplayNameWithArticleWithoutArticle()));
				label.setHighlightColors(h, market.getFaction().getBaseUIColor());
			}
		}
		
		addBulletPoints(info, ListInfoMode.IN_DESC);
		
		
		if (state == ContactState.PRIORITY || state == ContactState.NON_PRIORITY || state == ContactState.SUSPENDED) {
			long ts = BaseMissionHub.getLastOpenedTimestamp(person);
			if (ts <= Long.MIN_VALUE) {
				//info.addPara("Never visited.", opad);	
			} else {
				info.addPara(StringHelper.getString("nex_contacts", "intelDescLastVisit"), 
						opad, h, Misc.getDetailedAgoString(ts));
			}
		}
		
		info.addPara(StringHelper.getString("nex_contacts", "intelDescSpecial"), opad);
	}
	
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
		if ((isEnding() || isEnded()) && state != ContactState.LOST_CONTACT_DECIV) return;
		super.addBulletPoints(info, mode);
	}
}

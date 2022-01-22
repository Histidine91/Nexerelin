package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HomeworldPickerDebugIntel extends BaseIntelPlugin {
	
	public static final int TAB_BUTTON_HEIGHT = 20;
	public static final int TAB_BUTTON_WIDTH = 180;
	public static final Object BUTTON_CORE = new Object();
	public static final Object BUTTON_NON_CORE = new Object();
	
	public boolean showCore = true;
	public boolean showNonCore = true;
	
	// runcode Global.getSector().getIntelManager().addIntel(new HomeworldPickerDebugIntel());
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		
	}
	
	public List<ProcGenEntity> getPlanets() {
		return (List<ProcGenEntity>)Global.getSector().getMemoryWithoutUpdate().get("$nex_randomSector_colonyCandidates");
	}
	
	public TooltipMakerAPI generateTabButton(CustomPanelAPI buttonRow, String name, Object buttonId,
			boolean show, Color base, Color bg, Color bright, TooltipMakerAPI rightOf) 
	{
		TooltipMakerAPI holder = buttonRow.createUIElement(TAB_BUTTON_WIDTH, 
				TAB_BUTTON_HEIGHT, false);
		
		ButtonAPI button = holder.addAreaCheckbox(name, buttonId, base, bg, bright,
				TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT, 0);
		button.setChecked(show);
		
		if (rightOf != null) {
			buttonRow.addUIElement(holder).rightOfTop(rightOf, 4);
		} else {
			buttonRow.addUIElement(holder).inTL(0, 3);
		}
		
		return holder;
	}
	
	protected TooltipMakerAPI addTabButtons(CustomPanelAPI panel, float width) {
		
		//CustomPanelAPI row = panel.createCustomPanel(width, TAB_BUTTON_HEIGHT, null);
		//CustomPanelAPI spacer = panel.createCustomPanel(width, TAB_BUTTON_HEIGHT, null);
		FactionAPI fc = getFactionForUIColors();
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor(), bright = fc.getBrightUIColor();
				
		TooltipMakerAPI btnHolder1 = generateTabButton(panel, "Show core", BUTTON_CORE, showCore,
				base, bg, bright, null);		
		TooltipMakerAPI btnHolder2 = generateTabButton(panel, "Show non-core", BUTTON_NON_CORE, showNonCore,
				base, bg, bright, btnHolder1);
		
		return btnHolder1;
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float pad = 3;
		float opad = 10;
		//float elWidth = width - 4;
		float textWidth = 240;
		
		TooltipMakerAPI buttonHolder = addTabButtons(panel, width);
		
		TooltipMakerAPI scroll = panel.createUIElement(width, height - 24, true);
		
		scroll.addSectionHeading("Planets", Misc.getBasePlayerColor(), 
			Misc.getDarkPlayerColor(), com.fs.starfarer.api.ui.Alignment.MID, 0);
		
		List<ProcGenEntity> planets = getPlanets();
		if (planets == null) return;
		
		scroll.addPara(String.format("%s planets found", planets.size()), pad);
		
		scroll.addSpacer(opad);
		
		for (ProcGenEntity ent : planets) {
			boolean core = ent.starSystem.hasTag(Tags.THEME_CORE);
			if (core && !showCore) continue;
			if (!core && !showNonCore) continue;
			
			CustomPanelAPI row = panel.createCustomPanel(width, 64, null);
			TooltipMakerAPI img = row.createUIElement(128, 64, false);
			
			PlanetAPI planet = (PlanetAPI)ent.entity;
			img.addImage(planet.getSpec().getTexture(), 128, pad);
			
			row.addUIElement(img).inTL(0, 0);
			
			TooltipMakerAPI text = row.createUIElement(textWidth, 64, false);
			text.addPara(planet.getName(), planet.getSpec().getIconColor(), pad);
			
			text.addPara(planet.getContainingLocation().getNameWithLowercaseTypeShort(), pad);
			//text.addButton("View", planet, 48, 14, pad);
			
			String desire = String.format("%.1f", ent.desirability);
			text.addPara(desire, pad);
			
			row.addUIElement(text).rightOfTop(img, pad);
			
			TooltipMakerAPI conds = row.createUIElement(width - 128 - textWidth - pad*2, 64, false);
			List<String> images = new ArrayList<>();
			List<String> names = new ArrayList<>();
			
			for (MarketConditionAPI cond : planet.getMarket().getConditions()) {
				images.add(cond.getSpec().getIcon());
				names.add(cond.getName());
			}
			conds.addImages(32, 32, pad, pad, images.toArray(new String[0]));
			//conds.addPara(names.toString(), pad);
			
			row.addUIElement(conds).rightOfTop(text, pad);
			
			scroll.addCustom(row, pad);
		}
		
		panel.addUIElement(buttonHolder).inTL(0, 0);
		panel.addUIElement(scroll).belowLeft(buttonHolder, pad);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_CORE) {
			showCore = !showCore;
			ui.updateUIForItem(this);
			return;
		} else if (buttonId == BUTTON_NON_CORE) {
			showNonCore = !showNonCore;
			ui.updateUIForItem(this);
			return;
		}
		
		//PlanetAPI planet = (PlanetAPI)buttonId;
		//RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl();
		//ui.showDialog(planet, plugin);
		//Global.getSector().getCampaignUI().showInteractionDialog(planet);
	}
	
	@Override
	public boolean hasSmallDescription() {
		return false;
	}
	
	@Override
	public boolean hasLargeDescription() {
		return true;
	}
	
	protected String getName() {
		return "Homeworld Picker Log";
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		//tags.add(StringHelper.getString("victory", true));
		tags.add(Tags.INTEL_FLEET_LOG);
		return tags;
	}
	
	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_0;
	}
	
	@Override
	public String getIcon() {
		return Global.getSector().getPlayerFaction().getCrest();
	}
}

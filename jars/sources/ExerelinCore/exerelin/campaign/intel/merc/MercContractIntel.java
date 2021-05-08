package exerelin.campaign.intel.merc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.merc.MercDataManager.MercCompanyDef;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MercContractIntel extends BaseIntelPlugin
{
	public static final float CONTRACT_PERIOD = 365;
	public static final float MERC_AVAILABLE_TIME = 60;
	
	protected boolean isDone;
	protected boolean annihilated;
	protected String companyId;
	
	protected List<FleetMemberAPI> ships = new ArrayList<>();
	protected List<PersonAPI> officers = new ArrayList<>();
	protected MercFleetGenPlugin fleetPlugin;
	protected CampaignFleetAPI offeredFleet;
	
	protected long startingShipValue;
	protected float daysRemaining;
	
	public MercContractIntel(String companyId) {
		this.companyId = companyId;
	}
	
	public void init(MarketAPI market) {
		fleetPlugin = MercFleetGenPlugin.createPlugin(this);
		if (fleetPlugin.isAvailableAt(market)) {
			offeredFleet = fleetPlugin.generateFleet(market);
		}
	}
	
	public CampaignFleetAPI getOfferedFleet() {
		return offeredFleet;
	}
	
	public void accept(TextPanelAPI text) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		for (FleetMemberAPI member : offeredFleet.getFleetData().getMembersListCopy()) {
			
			offeredFleet.getFleetData().removeFleetMember(member);
			player.getFleetData().addFleetMember(member);
			ships.add(member);
			
			PersonAPI officer = member.getCaptain();
			if (officer != null && !officer.isDefault()) {
				player.getFleetData().addOfficer(officer);
				offeredFleet.getFleetData().removeOfficer(officer);
				officers.add(officer);
			}
			player.getCargo().addCrew((int)member.getHullSpec().getMinCrew());
						
			Misc.setMercHiredNow(officer);
		}
		daysRemaining = CONTRACT_PERIOD;
		
		Global.getSector().getIntelManager().addIntel(this, false, text);
		Global.getSector().addScript(this);
	}
	
	// TODO: dismiss mercs; handle case where company is annihilated
	
	public MercCompanyDef getDef() {
		return MercDataManager.getDef(companyId);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
								   Color tc, float initPad) {
		if (!isDone)
			info.addPara(getString("intel_bullet_daysRemaining"), 3, Misc.getHighlightColor(), (int)daysRemaining + "");
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float pad = 3, opad = 10;
		Color h = Misc.getHighlightColor();
		MercCompanyDef def = getDef();
		
		if (officers.isEmpty()) {
			info.addImages(width, 128, opad, opad, def.getLogo(), "graphics/portraits/portrait_generic.png");
		} else {
			info.addImages(width, 128, opad, opad, def.getLogo(), officers.get(0).getPortraitSprite());
		}
		
		String str = getString("intel_desc_para1");
		str = StringHelper.substituteToken(str, "$playerName", Global.getSector().getPlayerPerson().getNameString());
		info.addPara(str, opad, def.getFaction().getBaseUIColor(), def.name);
		
		String fee1 = Misc.getDGSCredits(def.feeUpfront);
		String fee2 = Misc.getDGSCredits(def.feeMonthly);
		info.addPara(getString("intel_desc_feeUpfront") + ": " + fee1, opad, h, fee1);
		info.addPara(getString("intel_desc_feeMonthly") + ": " + fee2, pad, h, fee2);
		
		if (!isDone)
			info.addPara(getString("intel_desc_daysRemaining"), 3, h, (int)daysRemaining + "");
	}
	
	@Override
	public String getIcon() {
		return getDef().getLogo();
	}
	
	@Override
	protected String getName() {
		String str = String.format(getString("intel_title"), getDef().name);
		if (isDone || isEnding() || isEnded())
			str += " - " + StringHelper.getString("over", true);
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(getString("intel_tag"));
		return tags;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_mercs", id);
	}
	
	public static boolean hasOngoing() {
		for (IntelInfoPlugin iip : Global.getSector().getIntelManager().getIntel(MercContractIntel.class))
		{
			MercContractIntel merc = (MercContractIntel)iip;
			if (merc.isEnding() || merc.isEnded()) continue;
			return true;
		}
		return false;
	}
}

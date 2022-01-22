package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class Nex_DonateAdmin extends PaginatedOptions {
	
	public static final String OPTION_PREFIX = "nex_donateAdmin_select_";
	public static final int PREFIX_LENGTH = OPTION_PREFIX.length();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		this.memoryMap = memoryMap;
		
		switch (arg)
		{
			case "isEnabled":
				return canTransfer(dialog.getInteractionTarget().getMarket());
			
			case "list":
				setupDelegateDialog(dialog);
				addOptions();
				showOptions();
				break;
				
			case "transfer":
				int id = Integer.parseInt(memoryMap.get(MemKeys.LOCAL).getString("$option").substring(PREFIX_LENGTH));
				AdminData admin = Global.getSector().getCharacterData().getAdmins().get(id);
				transfer(admin, market);
				
				break;
		}
		
		return true;
	}
	
	public static boolean canTransfer(MarketAPI market) {
		return !market.isPlayerOwned() && market.getFaction().isPlayerFaction();
	}
	
	/**
	 * To be called only when paginated dialog options are required. 
	 * Otherwise we get nested dialogs that take multiple clicks of the exit option to actually exit.
	 * @param dialog
	 */
	protected void setupDelegateDialog(InteractionDialogAPI dialog)
	{
		this.dialog = dialog;
		originalPlugin = dialog.getPlugin();  

		dialog.setPlugin(this);  
		init(dialog);
	}
	
	protected String getSkillsString(AdminData admin) {
		List<SkillLevelAPI> skills = admin.getPerson().getStats().getSkillsCopy();
		
		String str;		
		List<String> strings = new ArrayList<>();
		
		for (SkillLevelAPI skill : skills) {
			if (skill.getLevel() <= 0) continue;
			String name = skill.getSkill().getName();
			String level = (int)skill.getLevel() + "";
			strings.add(name + " " + level);
		}
		if (strings.isEmpty())
			str = StringHelper.getString("exerelin_markets", "donateAdminNoSkills");
		else
			str = StringHelper.writeStringCollection(strings, false, true);
		
		str = " (" + str + ")";
		return str;
	}
	
	protected void addOptions() {
		List<AdminData> admins = Global.getSector().getCharacterData().getAdmins();
		int i = 0;
		for (AdminData admin : admins) {
			String name = admin.getPerson().getNameString() + getSkillsString(admin);
			addOption(name, OPTION_PREFIX + i);
			i++;
		}
		
		addOptionAllPages(StringHelper.getString("back", true), "nex_donateAdmin_cancel");
	}
	
	@Override
	public void showOptions() {
		super.showOptions();
		
		for (PaginatedOption opt : options) {
			dialog.getOptionPanel().addOptionConfirmation(opt.id, 
					StringHelper.getString("exerelin_markets", "donateAdminConfirm"), 
					StringHelper.getString("yes", true),
					StringHelper.getString("no", true));
		}
		
		dialog.getOptionPanel().setShortcut("nex_donateAdmin_cancel", 
				Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	public static void transfer(AdminData admin, MarketAPI market) {
		ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
		
		String post = Ranks.POST_ADMINISTRATOR;
		if (market.getPlanetEntity() == null) post = Ranks.POST_STATION_COMMANDER;
		
		PersonAPI currAdmin = market.getAdmin();
		if (post.equals(currAdmin.getPostId()))
		{
			market.removePerson(currAdmin);
			market.getCommDirectory().removePerson(currAdmin);
			ip.removePerson(currAdmin);
		}
		
		if (admin.getMarket() != null)
			admin.getMarket().setAdmin(Global.getSector().getPlayerPerson());
		
		PersonAPI newAdmin = admin.getPerson();
		newAdmin.setPostId(post);
		market.setAdmin(newAdmin);
		market.addPerson(newAdmin);
		market.getCommDirectory().addPerson(newAdmin);
		ip.addPerson(newAdmin);
		ip.getData(newAdmin).getLocation().setMarket(market);
		ip.checkOutPerson(newAdmin, "permanent_staff");
				
		Global.getSector().getCharacterData().removeAdmin(newAdmin);
		newAdmin.setFaction(market.getFactionId());
	}
}

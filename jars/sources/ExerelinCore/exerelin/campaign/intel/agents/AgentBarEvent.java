package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import static exerelin.campaign.intel.agents.AgentIntel.getString;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class AgentBarEvent extends BaseBarEventWithPerson {
	
	protected static final WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<>();
	
	protected int level = 1;
	protected AgentIntel.Specialization spec = null;
	
	public static enum OptionId {
		INIT,
		EXPLANATION,
		HIRE_OFFER,
		HIRE_CONFIRM,
		HIRE_DONE,
		CANCEL
	}
	
	static {
		picker.add(1, 10);
		picker.add(2, 4);
		picker.add(3, 1);
	}
	
	public static boolean isAtMaxAgents() {
		return CovertOpsManager.getManager().getAgents().size() >= CovertOpsManager.getManager().getMaxAgents().getModifiedValue();
	}
	
	@Override
	public boolean shouldShowAtMarket(MarketAPI market) {
		if (market.isHidden()) return false;
		NexFactionConfig conf = NexConfig.getFactionConfig(market.getFactionId());
		if (!conf.allowAgentActions)
			return false;
		
		// doesn't work as intended since it's called every time the bar is opened
		/*
		if (isAtMaxAgents() && Math.random() < 0.5f) {
			if (ExerelinModPlugin.isNexDev) {
				Global.getSector().getCampaignUI().addMessage("Agent bar spawn blocked due to being at max agents");
			}
			//return false;
		}
		*/
		
		return super.shouldShowAtMarket(market);
	}
	
	@Override
	protected void regen(MarketAPI market) {
		if (this.market == market) return;
		super.regen(market);
		level = picker.pick();
		spec = AgentIntel.Specialization.pickRandomSpecialization();
		
		Global.getLogger(this.getClass()).info("Generated agent at " + market.getName() + " bar");
	}
	
	@Override
	public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		super.addPromptAndOption(dialog, memoryMap);
		
		regen(dialog.getInteractionTarget().getMarket());
		
		TextPanelAPI text = dialog.getTextPanel();
		
		String str = getString("barPrompt");
		str = StringHelper.substituteToken(str, "$manOrWoman", getManOrWoman());
		str = StringHelper.substituteToken(str, "$heOrShe", getHeOrShe());
		str = StringHelper.substituteToken(str, "$HeOrShe", Misc.ucFirst(getHeOrShe()));
		
		text.addPara(str);
		
		str = getString("barOptionStart");
		dialog.getOptionPanel().addOption(str, this);
	}
	
	@Override
	public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		super.init(dialog, memoryMap);
		
		done = false;
		
		dialog.getVisualPanel().showPersonInfo(person, true);
		
		optionSelected(null, OptionId.INIT);
	}
	
	@Override
	public void optionSelected(String optionText, Object optionData) {
		if (!(optionData instanceof OptionId)) {
			return;
		}
		OptionId option = (OptionId) optionData;
		
		OptionPanelAPI options = dialog.getOptionPanel();
		TextPanelAPI text = dialog.getTextPanel();
		Color hl = Misc.getHighlightColor();
		String str;
		
		int salary = AgentIntel.getSalary(level);
		int hiringBonus = salary * 4;
		
		options.clearOptions();
		
		switch (option) {
			case INIT:
				text.addPara(getString("barDialogIntro1"));
				text.addPara(getString("barDialogIntro2"));

				options.addOption(StringHelper.getString("yes", true), OptionId.HIRE_OFFER);
				options.addOption(StringHelper.getString("no", true), OptionId.EXPLANATION);
				break;
			case EXPLANATION:
				String key = "barDialogExplanation";
				switch (spec) {
					case NEGOTIATOR:
						key += "Negotiator";
						break;
					case SABOTEUR:
						key += "Saboteur";
						break;
					case HYBRID:
						key += "Hybrid";
						break;
				}
				
				str = getString(key);
				str = StringHelper.substituteToken(str, "$manOrWoman", getManOrWoman());
				text.addPara(str);
				// fall through to next case
			case HIRE_OFFER:
				text.setFontSmallInsignia();
				text.addPara(StringHelper.HR);
				
				str = getString("intelDescName");
				str = StringHelper.substituteToken(str, "$name", person.getNameString());
				text.addPara(str, hl, level + "");
				
				if (NexConfig.useAgentSpecializations) {
					str = getString("intelDescSpecialization");
					text.addPara(str, hl, spec.getName());

					str = getString("intelDescActionList");
					text.addPara(str, Misc.getButtonTextColor(), StringHelper.writeStringCollection(spec.getAllowedActionNames(true)));
				}
				
				
				text.addPara(StringHelper.HR);
				text.setFontInsignia();
				
				String salaryStr = Misc.getWithDGS(salary);
				String bonusStr = Misc.getWithDGS(hiringBonus);
				str = getString("barDialogPay");
				
				text.addPara(str, hl, bonusStr, salaryStr);
				
				str = StringHelper.getString("exerelin_misc", "creditsAvailable");
				float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
				String creditsStr =  Misc.getWithDGS(credits);
				boolean enough = credits >= hiringBonus;
				text.addPara(str, enough? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(), creditsStr);
				
				if (isAtMaxAgents())
				{
					options.addOption(getString("barOptionMaxAgents"), OptionId.CANCEL);
				} 
				else if (!enough) {
					options.addOption(getString("barOptionNotEnoughCredits"), OptionId.CANCEL);
				} 
				else {
					options.addOption(getString("barOptionHire"), OptionId.HIRE_CONFIRM);
					options.addOption(getString("barOptionDecline"), OptionId.CANCEL);
					options.setShortcut(OptionId.HIRE_CONFIRM, Keyboard.KEY_RETURN, false, false, false, false);
				}
				options.setShortcut(OptionId.CANCEL, Keyboard.KEY_ESCAPE, false, false, false, false);
				break;
			case HIRE_CONFIRM:
				str = getString("barDialogHired");
				str = StringHelper.substituteToken(str, "$heOrShe", getHeOrShe());
				str = StringHelper.substituteToken(str, "$HeOrShe", Misc.ucFirst(getHeOrShe()));
				text.addPara(str);
				
				Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(hiringBonus);
				AddRemoveCommodity.addCreditsLossText(hiringBonus, text);
				
				BarEventManager.getInstance().notifyWasInteractedWith(this);
				addIntel(spec);
				text.setFontSmallInsignia();
				text.addPara(getString("barDialogHiredTip"));
				text.setFontInsignia();
				options.addOption(StringHelper.getString("leave", true), OptionId.HIRE_DONE);
				break;
			case HIRE_DONE:
			case CANCEL:
				noContinue = true;
				done = true;
				break;
		}
	}

	protected void addIntel(AgentIntel.Specialization spec) {
		AgentIntel intel = new AgentIntel(person, Global.getSector().getPlayerFaction(), level);
		if (spec != null) intel.addSpecialization(spec);
		intel.init();
		intel.setMarket(market);
		intel.setImportant(true);
		Global.getSector().getIntelManager().addIntelToTextPanel(intel, text);
	}

	@Override
	protected String getPersonFaction() {
		return Factions.INDEPENDENT;
	}
	
	@Override
	protected String getPersonRank() {
		return Ranks.AGENT;
	}
	
	@Override
	protected String getPersonPost() {
		return Ranks.POST_AGENT;
	}
	
	@Override
	protected String getPersonPortrait() {
		return null;
	}
	
	@Override
	protected Gender getPersonGender() {
		return Gender.ANY;
	}
	
	@Override
	public boolean isAlwaysShow() {
		return false;
	}
}

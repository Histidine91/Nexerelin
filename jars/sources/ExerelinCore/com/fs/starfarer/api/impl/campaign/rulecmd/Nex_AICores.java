package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AICores;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;

public class Nex_AICores extends AICores {
	
	// Uses Nexerelin reputation function
	// Prints relationship limit if applicable
	// External strings
	@Override
	protected void selectCores() {
		CargoAPI copy = Global.getFactory().createCargo(false);
		//copy.addAll(cargo);
		for (CargoStackAPI stack : playerCargo.getStacksCopy()) {
			CommoditySpecAPI spec = stack.getResourceIfResource();
			if (spec != null && spec.getDemandClass().equals(Commodities.AI_CORES)) {
				copy.addFromStack(stack);
			}
		}
		copy.sort();
		
		final float width = 310f;
		dialog.showCargoPickerDialog(StringHelper.getString("exerelin_misc", "aiCoresSelect"), 
				Misc.ucFirst(StringHelper.getString("confirm")), 
				Misc.ucFirst(StringHelper.getString("cancel")),
						true, width, copy, new CargoPickerListener() {
			public void pickedCargo(CargoAPI cargo) {
				cargo.sort();
				for (CargoStackAPI stack : cargo.getStacksCopy()) {
					playerCargo.removeItems(stack.getType(), stack.getData(), stack.getSize());
					if (stack.isCommodityStack()) { // should be always, but just in case
						int num = (int) stack.getSize();
						AddRemoveCommodity.addCommodityLossText(stack.getCommodityId(), num, text);

						String key = "$turnedIn_" + stack.getCommodityId();
						int turnedIn = faction.getMemoryWithoutUpdate().getInt(key);
						faction.getMemoryWithoutUpdate().set(key, turnedIn + num);

						// Also, total of all cores! -dgb
						String key2 = "$turnedIn_allCores";
						int turnedIn2 = faction.getMemoryWithoutUpdate().getInt(key2);
						faction.getMemoryWithoutUpdate().set(key2, turnedIn2 + num);
					}
				}
				
				float bounty = computeCoreCreditValue(cargo);
				float repChange = computeCoreReputationValue(cargo);

				if (bounty > 0) {
					playerCargo.getCredits().add(bounty);
					AddRemoveCommodity.addCreditsGainText((int)bounty, text);
				}
				
				if (repChange >= 1f) {
					CustomRepImpact impact = new CustomRepImpact();
					impact.delta = repChange * 0.01f;
					Global.getSector().adjustPlayerReputation(
							new RepActionEnvelope(RepActions.CUSTOM, impact,
												  null, text, true), 
												  faction.getId());
					
					impact.delta *= 0.25f;
					if (impact.delta >= 0.01f) {
						Global.getSector().adjustPlayerReputation(
								new RepActionEnvelope(RepActions.CUSTOM, impact,
													  null, text, true), 
													  person);
					}
				}
				
				FireBest.fire(null, dialog, memoryMap, "AICoresTurnedIn");
			}
			public void cancelledCargoSelection() {
			}
			public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
			
				float bounty = computeCoreCreditValue(combined);
				float repChange = computeCoreReputationValue(combined);
				
				float pad = 3f;
				float small = 5f;
				float opad = 10f;

				panel.setParaOrbitronLarge();
				panel.addPara(Misc.ucFirst(faction.getDisplayName()), faction.getBaseUIColor(), opad);
				//panel.addPara(faction.getDisplayNameLong(), faction.getBaseUIColor(), opad);
				//panel.addPara(faction.getDisplayName() + " (" + entity.getMarket().getName() + ")", faction.getBaseUIColor(), opad);
				panel.setParaFontDefault();
				
				panel.addImage(faction.getLogo(), width * 1f, pad);
				
				
				//panel.setParaFontColor(Misc.getGrayColor());
				//panel.setParaSmallInsignia();
				//panel.setParaInsigniaLarge();
				String str = StringHelper.getStringAndSubstituteToken("exerelin_misc",
						"aiCoresMsg1", "$faction", faction.getDisplayNameLongWithArticle());
				panel.addPara(str, opad);
				panel.beginGridFlipped(width, 1, 40f, 10f);
				//panel.beginGrid(150f, 1);
				panel.addToGrid(0, 0, StringHelper.getString("exerelin_misc", "aiCoresBounty"),
						"" + (int)(valueMult * 100f) + "%");
				panel.addToGrid(0, 1, StringHelper.getString("exerelin_misc", "aiCoresRep"),
						"" + (int)(repMult * 100f) + "%");
				panel.addGrid(pad);
				
				str = StringHelper.getStringAndSubstituteToken("exerelin_misc",
						"aiCoresMsg2", "$faction", faction.getDisplayNameWithArticle());
				panel.addPara(str, 	opad * 1f, Misc.getHighlightColor(),
						Misc.getWithDGS(bounty) + Strings.C, "" + (int) repChange);
				
				FactionAPI myFaction = PlayerFactionStore.getPlayerFaction();
				//if (!DiplomacyManager.haveRandomRelationships(faction.getId(), myFaction.getId()))
				{
					String shortName = NexUtilsFaction.getFactionShortName(faction);
					float maxRep = DiplomacyManager.getManager().getMaxRelationship(faction.getId(), myFaction.getId());
					if (maxRep < 1)
					{
						int maxRepInt = (int)(maxRep * 100f);
						str = StringHelper.getStringAndSubstituteToken("exerelin_factions", 
								"repLimit", "$faction", shortName);
						panel.setParaFontColor(Misc.getGrayColor());
						LabelAPI label = panel.addPara(str, opad * 1f, NexUtilsReputation.getRelColor(maxRep), 
								maxRepInt + "/100", NexUtilsReputation.getRelationStr(myFaction, faction));
						label.setHighlightColors(NexUtilsReputation.getRelColor(maxRep), faction.getRelColor(myFaction.getId()));
						
						panel.setParaFontColor(Misc.getTextColor());
					}
				}
				
				//panel.addPara("Bounty: %s", opad, Misc.getHighlightColor(), Misc.getWithDGS(bounty) + Strings.C);
				//panel.addPara("Reputation: %s", pad, Misc.getHighlightColor(), "+12");
			}
		});
	}
	
}

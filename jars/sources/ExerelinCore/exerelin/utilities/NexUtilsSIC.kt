package exerelin.utilities

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Skills
import com.fs.starfarer.api.util.WeightedRandomPicker
import org.lazywizard.lazylib.MathUtils
import second_in_command.SCData
import second_in_command.SCUtils
import second_in_command.misc.NPCOfficerGenerator
import second_in_command.misc.PotentialPick
import second_in_command.misc.SCSettings
import second_in_command.specs.SCBaseAptitudePlugin
import second_in_command.specs.SCBaseSkillPlugin
import second_in_command.specs.SCOfficer
import second_in_command.specs.SCSpecStore

/**
 * Handles stuff for Second in Command mod by Lukas04
 */
object NexUtilsSIC {
    @JvmStatic
    fun generateRandomXOs(data: SCData, fleet: CampaignFleetAPI, numAptitudes : Int, numSkills: Int) {

        var aptitudePicker = WeightedRandomPicker<SCBaseAptitudePlugin>()
        var aptitudes = ArrayList<SCBaseAptitudePlugin>()

        var availableAptitudes = SCSpecStore.getAptitudeSpecs().map { it.getPlugin() }.toMutableList()

        var priority = availableAptitudes.filter { it.guaranteePick(fleet) }.toMutableList()

        var aptitudeCount = numAptitudes.coerceAtLeast(1).coerceAtMost(3)
        var skillCount = numSkills.coerceAtLeast(aptitudeCount).coerceAtMost(aptitudeCount * 5)

        while (priority.isNotEmpty() && aptitudeCount >= 1) {

            var aptitude = priority.first()

            aptitudes.add(aptitude)
            priority.remove(aptitude)

            aptitudeCount -= 1

            var categories = aptitude.categories
            for (other in ArrayList(availableAptitudes)) {
                var otherCategories = other.categories

                if (categories.any { otherCategories.contains(it) }) {
                    availableAptitudes.remove(other)
                    priority.remove(other)
                }
            }
        }


        var noPriority = availableAptitudes.filter { !it.guaranteePick(fleet) }.toMutableList()
        for (aptitude in noPriority) {
            aptitudePicker.add(aptitude, aptitude.getNPCFleetSpawnWeight(data, fleet))
        }

        for (i in 0 until aptitudeCount) {
            if (aptitudeCount <= 0) break
            if (aptitudePicker.isEmpty) break

            var pick = aptitudePicker.pickAndRemove()
            aptitudes.add(pick)

            var categories = pick.categories
            for (other in ArrayList(noPriority)) {
                var otherCategories = other.categories

                if (categories.any { otherCategories.contains(it) }) {
                    noPriority.remove(other)
                    aptitudePicker.remove(other)
                }
            }
        }

        var officers = ArrayList<SCOfficer>()
        for (aptitude in aptitudes) {
            var officer = SCUtils.createRandomSCOfficer(aptitude.getId(), fleet.faction)

            officers.add(officer)
        }


        var unlocked = ArrayList<SCBaseSkillPlugin>()

        for (i in 0 until skillCount) {

            var unlockable = WeightedRandomPicker<PotentialPick>()

            for (officer in officers) {


                var aptitude = officer.getAptitudePlugin()

                aptitude.clearSections()
                aptitude.createSections()
                var sections = aptitude.getSections()

                var skillsInAptitude = sections.flatMap { it.getSkills() }
                var unlockedSkillsCount = unlocked.count { skillsInAptitude.contains(it.getId()) }

                if (unlockedSkillsCount >= 5) continue //Dont let it get more than 5 skills

                for (section in sections) {
                    if (unlockedSkillsCount >= section.requiredPreviousSkills) {
                        var skills = section.getSkills()

                        //Skip Section if one of its skills is unlocked and the section doesnt allow for more
                        var canChooseMultiple = section.canChooseMultiple
                        if (!canChooseMultiple && unlocked.map { it.getId() }.any { skills.contains(it) }) {
                            continue
                        }

                        for (skill in skills) {
                            if (!unlocked.map { it.getId() }.contains(skill)) {
                                var plugin = SCSpecStore.getSkillSpec(skill)!!.getPlugin()
                                unlockable.add(PotentialPick(officer, plugin), plugin.getNPCSpawnWeight(fleet))
                            }
                        }
                    }
                }

            }


            var pick = unlockable.pick()
            if (pick != null) {
                pick.officer.addSkill(pick.skill.getId())
                unlocked.add(pick.skill)
            }


        }


        var slotId = 0
        for (officer in officers) {


            officer.activeSkillIDs = officer.activeSkillIDs.sortedBy { SCSpecStore.getSkillSpec(it)!!.order }.toMutableSet()

            data.addOfficerToFleet(officer)
            data.setOfficerInSlot(slotId, officer)

            slotId += 1
        }
    }
}
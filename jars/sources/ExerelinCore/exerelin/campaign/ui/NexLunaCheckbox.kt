package exerelin.campaign.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.TooltipMakerAPI

class NexLunaCheckbox(var value: Boolean, tooltip: TooltipMakerAPI, width: Float, height: Float) : NexLunaElement(tooltip, width, height) {

    var offSprite = Global.getSettings().getSprite("ui", "toggle20_off")
    var onSprite = Global.getSettings().getSprite("ui", "toggle20_on")
    var glowSprite = Global.getSettings().getSprite("ui", "toggle20_on2")


    init {
        renderBackground = false
        renderBorder = false

        /*onClick {
            playClickSound()
        }*/

        onHoverEnter {
            playScrollSound()
        }
    }

    override fun render(alphaMult: Float) {
        super.render(alphaMult)

        var alpha = 0f
        if (isHovering) alpha = 0.15f

        glowSprite.alphaMult = alpha * alphaMult
        glowSprite.setSize(width, height)
        glowSprite.render(x, y)

        if (value) {
            onSprite.alphaMult = alphaMult
            onSprite.setSize(width, height)
            onSprite.render(x, y)
        }

    }

    override fun renderBelow(alphaMult: Float) {
        super.renderBelow(alphaMult)

        offSprite.alphaMult = alphaMult
        offSprite.setSize(width, height)
        offSprite.render(x, y)

    }

}
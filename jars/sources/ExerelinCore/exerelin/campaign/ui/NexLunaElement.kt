package exerelin.campaign.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.lwjgl.opengl.GL11
import java.awt.Color

open class NexLunaElement : CustomUIPanelPlugin {

    private var elementID = Misc.genUID() ?: ""

    var parentElement: TooltipMakerAPI
    var elementPanel: CustomPanelAPI
    var innerElement: TooltipMakerAPI
    var paragraph: LabelAPI? = null

    var width: Float
    var height: Float
    var x: Float
    var y: Float

    var isHeld = false
        private set

    var isHovering = false
        private set
    var hoverEnter = false
        private set

    var position: PositionAPI
        private set

    var backgroundColor = Misc.getDarkPlayerColor().darker()
    var borderColor = Misc.getDarkPlayerColor()

    var renderBorder = true
    var renderBackground = true

    var enableTransparency = false
    var borderAlpha = 1f
    var backgroundAlpha = 1f

    var selectionGroup = ""

    private var customData: HashMap<String, Any?> = HashMap()

    private var positionFunc: MutableList<(position: PositionAPI) -> Unit> = ArrayList()
    private var advanceFunc: MutableList<(amount: Float) -> Unit> = ArrayList()
    private var renderFunc: MutableList<(alphaMult: Float) -> Unit> = ArrayList()
    private var renderBGFunc: MutableList<(alphaMult: Float) -> Unit> = ArrayList()
    private var inputFunc: MutableList<(inputs: MutableList<InputEventAPI>) -> Unit> = ArrayList()

    private var onClickFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onClickOutsideFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()

    private var onHeldFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()

    private var onHoverFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onHoverEnterFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onHoverExitFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()

    constructor(tooltip: TooltipMakerAPI, width: Float, height: Float)
    {
        this.parentElement = tooltip
        this.elementPanel = Global.getSettings().createCustom(width, height, this)
        var comp = tooltip.addCustom(elementPanel, 0f)
        this.position = comp.position

        this.width = width
        this.height = height
        this.x = position.x
        this.y = position.y

        this.innerElement = elementPanel.createUIElement(width, height, false)
        elementPanel.addUIElement(innerElement)

        selectionGroup = "all"
    }

    fun playClickSound() {
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
    }

    fun playScrollSound()  {
        Global.getSoundPlayer().playUISound("ui_number_scrolling", 1f, 0.8f)
    }

    fun playSound(id: String, volume: Float = 1f, pitch: Float = 1f)
    {
        Global.getSoundPlayer().playUISound(id, pitch, volume)
    }

    fun addText(text: String, baseColor: Color = Misc.getTextColor(), highlightColor: Color = Misc.getHighlightColor(), highlights: List<String> = listOf())
    {
        paragraph = innerElement.addPara(text, 0f, baseColor, highlightColor, *highlights.toTypedArray())
    }

    fun changeText(text: String, highlights: List<String> = listOf()) {
        if (paragraph == null)
        {
            addText("")
        }
        paragraph!!.text = text
        paragraph!!.setHighlight(*highlights.toTypedArray())
    }

    fun centerText() {
        if (paragraph == null)
        {
            addText("")
        }
        paragraph!!.position.inTL(width / 2 - paragraph!!.computeTextWidth(paragraph!!.text) / 2, height / 2 - paragraph!!.computeTextHeight(paragraph!!.text) / 2)
    }

    fun getCustomData(id: String) = customData.get(id)
    fun setCustomData(id: String, value: Any?) = customData.set(id, value)

    override fun positionChanged(position: PositionAPI?) {
        if (position != null)
        {
            width = position.width
            height = position.height
            x = position.x
            y = position.y

            positionFunc.forEach {
                it(position)
            }
        }
    }

    override fun renderBelow(alphaMult: Float) {
        if (renderBackground)
        {
            /*   GL11.glPushMatrix()
               GL11.glDisable(GL11.GL_TEXTURE_2D)

               if (enableTransparency)
               {
                   GL11.glEnable(GL11.GL_BLEND)
                   GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
               }
               else
               {
                   GL11.glDisable(GL11.GL_BLEND)
               }

               GL11.glColor4f(c.red / 255f,
                   c.green / 255f,
                   c.blue / 255f,
                   c.alpha / 255f * (alphaMult * backgroundAlpha))

               GL11.glRectf(x, y , x + width, y + height)

              // GL11.glEnd()
               GL11.glPopMatrix()*/

            var color = backgroundColor

            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_CULL_FACE)

            if (enableTransparency)
            {
                GL11.glEnable(GL11.GL_BLEND)
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            }
            else
            {
                GL11.glDisable(GL11.GL_BLEND)
            }

            /* GL11.glEnable(GL11.GL_CULL_FACE)
             GL11.glCullFace(GL11.GL_FRONT)
             GL11.glFrontFace(GL11.GL_CW)*/
            //GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glColor4f(color.red / 255f,
                color.green / 255f,
                color.blue / 255f,
                color.alpha / 255f * (alphaMult * backgroundAlpha))

            GL11.glRectf(x, y , x + width, y + height)

            GL11.glPopMatrix()
        }

        renderBGFunc.forEach {
            it(alphaMult * backgroundAlpha)
        }
    }

    override fun render(alphaMult: Float) {

        if (renderBorder)
        {
            var c = borderColor
            GL11.glPushMatrix()

            GL11.glTranslatef(0f, 0f, 0f)
            GL11.glRotatef(0f, 0f, 0f, 1f)

            GL11.glDisable(GL11.GL_TEXTURE_2D)

            if (enableTransparency)
            {
                GL11.glEnable(GL11.GL_BLEND)
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            }
            else
            {
                GL11.glDisable(GL11.GL_BLEND)
            }

            GL11.glColor4f(c.red / 255f,
                c.green / 255f,
                c.blue / 255f,
                c.alpha / 255f * (alphaMult * borderAlpha))

            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glBegin(GL11.GL_LINE_STRIP)

            GL11.glVertex2f(x, y)
            GL11.glVertex2f(x, y + height)
            GL11.glVertex2f(x + width, y + height)
            GL11.glVertex2f(x + width, y)
            GL11.glVertex2f(x, y)

            GL11.glEnd()
            GL11.glPopMatrix()
        }

        renderFunc.forEach {
            it(alphaMult * borderAlpha)
        }
    }

    override fun advance(amount: Float) {
        advanceFunc.forEach {
            it(amount)
        }
    }

    override fun processInput(events: MutableList<InputEventAPI>?) {
        if (events == null) return

        for (event in events)
        {
            if (event.isMouseEvent)
            {
                if (event.x.toFloat() in x..(x + width) && event.y.toFloat() in y..(y +height))
                {
                    if (!hoverEnter)
                    {
                        hoverEnter = true
                        isHovering = true
                        for (onHoverEnter in onHoverEnterFunctions)
                        {
                            onHoverEnter(event)
                        }
                        onHoverEnter(event)
                    }
                    for (onHover in onHoverFunctions)
                    {
                        isHovering = true
                        onHover(event)
                    }
                    onHover(event)
                }
                else
                {
                    if (isHovering)
                    {
                        for (onHoverExit in onHoverExitFunctions)
                        {
                            onHoverExit(event)
                        }
                        hoverEnter = false
                        isHovering = false
                        onHoverExit(event)
                    }
                }
            }

            if (event.isMouseDownEvent && position != null)
            {
                if (event.x.toFloat() in x..(x + width) && event.y.toFloat() in y..(y +height))
                {
                    isHeld = true
                    for (onClick in onClickFunctions)
                    {
                        onClick(event)
                    }
                    onClick(event)
                    event.consume()
                }
                else
                {
                    for (onClickOutside in onClickOutsideFunctions)
                    {
                        onClickOutside(event)
                    }
                    onClickOutside(event)
                }
            }
            else if (event.isMouseUpEvent)
            {
                /*for (onNotHeld in onNotHeldFunctions)
                {
                    onNotHeld(event)
                }*/
                isHeld = false
            }
            else if (event.isMouseEvent && isHeld)
            {
                for (onHeld in onHeldFunctions)
                {
                    onHeld(event)
                }
                event.consume()
            }
        }

        inputFunc.forEach {
            it(events)
        }
    }

    open fun onClick(input: InputEventAPI) { }

    open fun onClickOutside(input: InputEventAPI) { }

    open fun onHeld(input: InputEventAPI) { }

    open fun onHover(input: InputEventAPI) { }

    open fun onHoverEnter(input: InputEventAPI) { }

    open fun onHoverExit(input: InputEventAPI) { }

    fun advance(function: (Float) -> Unit) {
        advanceFunc.add(function)
    }

    fun render(function: (Float) -> Unit) {
        renderFunc.add(function)
    }

    fun renderBelow(function: (Float) -> Unit) {
        renderBGFunc.add(function)
    }

    fun onPositionChanged(function: (PositionAPI) -> Unit) {
        positionFunc.add(function)
    }

    fun onInput(function: (List<InputEventAPI>) -> Unit) {
        inputFunc.add(function)
    }

    fun onClick(function: (InputEventAPI) -> Unit)
    {
        onClickFunctions.add(function)
    }
    fun onClickOutside(function: (InputEventAPI) -> Unit)
    {
        onClickOutsideFunctions.add(function)
    }

    fun onHeld(function: (InputEventAPI) -> Unit)
    {
        onHeldFunctions.add(function)
    }

    fun onHover(function: (InputEventAPI) -> Unit)
    {
        onHoverFunctions.add(function)
    }
    fun onHoverEnter(function: (InputEventAPI) -> Unit)
    {
        onHoverEnterFunctions.add(function)
    }
    fun onHoverExit(function: (InputEventAPI) -> Unit)
    {
        onHoverExitFunctions.add(function)
    }

    final override fun buttonPressed(buttonId: Any?) {

    }
}
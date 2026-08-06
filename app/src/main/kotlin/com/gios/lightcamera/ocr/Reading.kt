package com.gios.lightcamera.ocr

/**
 * One line of recognised text, and where on the page it was.
 *
 * **Lines rather than blocks or words**, which is a judgement about reading and not about the
 * API. A block is often the whole poster, so boxing it says nothing about where anything is; a
 * word puts forty boxes on a menu and turns the picture into a grid. A line is the unit a person
 * would point at.
 *
 * The rectangle is in the coordinates of the **upright** image — the recogniser's own space,
 * after the rotation it was handed. Getting it onto the screen is three more transformations and
 * lives in [TextBoxes], away from anything with a display in it.
 */
data class TextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Everything one look at a page produced.
 *
 * [width] and [height] are the upright image's, not the source bitmap's — for a frame read
 * sideways the two are swapped, and confusing them is how every box ends up in the wrong place
 * on exactly the shots people take of signs.
 */
data class Reading(
    val text: String,
    val lines: List<TextLine>,
    val width: Int,
    val height: Int,
) {
    val isEmpty: Boolean get() = text.isBlank()
}

package com.datataxi.service

import android.view.accessibility.AccessibilityNodeInfo
import com.datataxi.domain.CapturedLine
import com.datataxi.util.Constants

object NodeTextExtractor {

    /** Parcourt l'arbre de vues et retourne les textes visibles, dans l'ordre de l'écran. */
    fun extract(root: AccessibilityNodeInfo): List<CapturedLine> {
        val lines = ArrayList<CapturedLine>()
        var visited = 0

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > Constants.MAX_DEPTH || visited >= Constants.MAX_NODES) return
            visited++

            val text = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val label = text.ifEmpty { description }
            if (label.isNotEmpty() && node.isVisibleToUser) {
                lines.add(
                    CapturedLine(
                        depth = depth,
                        viewId = node.viewIdResourceName,
                        className = node.className?.toString(),
                        text = label,
                    )
                )
            }

            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1)
            }
        }

        walk(root, 0)
        return lines
    }
}

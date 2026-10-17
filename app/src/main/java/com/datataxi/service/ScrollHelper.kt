package com.datataxi.service

import android.view.accessibility.AccessibilityNodeInfo
import com.datataxi.domain.JournalParser
import com.datataxi.util.Constants

object ScrollHelper {

    /**
     * Fait défiler d'une page vers le bas la liste qui contient les lignes d'événement
     * (les plus anciennes sont en bas). Retourne false si aucune liste ne réagit.
     */
    fun scrollJournalForward(root: AccessibilityNodeInfo): Boolean {
        val node = findJournalScrollable(root) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun findJournalScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var fallback: AccessibilityNodeInfo? = null
        var visited = 0

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || best != null) return
            if (depth > Constants.MAX_DEPTH || visited >= Constants.MAX_NODES) return
            visited++

            if (node.isScrollable) {
                if (fallback == null) fallback = node
                if (containsEventText(node)) {
                    best = node
                    return
                }
            }
            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1)
            }
        }

        walk(root, 0)
        return best ?: fallback
    }

    private fun containsEventText(node: AccessibilityNodeInfo): Boolean =
        JournalParser.eventLines(NodeTextExtractor.extract(node)).isNotEmpty()
}

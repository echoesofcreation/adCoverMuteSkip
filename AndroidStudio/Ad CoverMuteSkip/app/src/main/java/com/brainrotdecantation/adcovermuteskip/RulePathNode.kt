package com.brainrotdecantation.adcovermuteskip

import android.view.accessibility.AccessibilityNodeInfo

sealed class RulePathNode {
    data class FindById(val id: String) : RulePathNode()
    data class GetChild(val index: Int) : RulePathNode()
}

object RulePathNodeTools {
    fun convert(str: String): List<RulePathNode> {
        return convert(arrayOf(str))
    }
    fun convert(array: Array<Any>): List<RulePathNode> {
        val path = mutableListOf<RulePathNode>()

        for (item in array) {
            when (item) {
                is String -> {
                    path.add(RulePathNode.FindById(item))
                }
                is Int -> {
                    if (item < 0) throw IllegalArgumentException("NodePathSegment.GetChild index cannot be negative: $item")
                    if (item > 999) throw IllegalArgumentException("NodePathSegment.GetChild index max value is 999: $item")
                    path.add(RulePathNode.GetChild(item))
                }
                else -> {
                    throw IllegalArgumentException("Unsupported type '${item::class.simpleName}' in NodeFunc.convert array. Expected String or Int.")
                }
            }
        }
        return path
    }

    // Use rootInActiveWindow as default root
    fun getNode(root: AccessibilityNodeInfo?, path: List<RulePathNode>): AccessibilityNodeInfo? {
        var currentNode: AccessibilityNodeInfo? = root
        if (currentNode == null) {
            //Log.d("DetectorService", "getNode: rootInActiveWindow is null. Cannot start path traversal.")
            return null
        }

        for (segment in path) {
            if (currentNode == null) {
                //Log.d("DetectorService", "getNode: Current node became null during traversal at segment: $segment. Path broken.")
                return null
            }

            val nextNode: AccessibilityNodeInfo? = when (segment) {
                is RulePathNode.FindById -> {
                    val foundNodes = currentNode.findAccessibilityNodeInfosByViewId(segment.id)
                    foundNodes?.firstOrNull()
                }
                is RulePathNode.GetChild -> {
                    val childIndex = segment.index
                    if (childIndex >= 0 && childIndex < currentNode.childCount) {
                        currentNode.getChild(childIndex)
                    } else {
                        //Log.d("DetectorService", "getNode: Child index $childIndex out of bounds (0..${currentNode.childCount - 1}) for node: ${currentNode.viewIdResourceName ?: currentNode.className}. Path broken.")
                        null // Indicate path failure
                    }
                }
            }
            currentNode = nextNode
            if (currentNode == null) {
                //Log.d("DetectorService", "getNode: Failed to find node for path segment '${segment::class.simpleName}' (value: ${when(segment) { is NodePathSegment.FindById -> segment.id; is NodePathSegment.GetChild -> segment.index; else -> "N/A" }}). Path broken.")
                return null
            }
        }

        //Log.d("DetectorService", "getNode: Successfully traversed path. Final node found: ${currentNode.viewIdResourceName ?: currentNode.className ?: "No ID/Class Name"}")
        return currentNode
    }
}


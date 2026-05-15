package dev.jfronny.zerointerest

import org.w3c.dom.Node
import org.w3c.dom.NodeList

inline fun NodeList.forEach(action: (Node) -> Unit) {
    for (i in 0 until length) action(item(i))
}
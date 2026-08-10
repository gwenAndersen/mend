package com.mend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class DataMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }
    
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D0D7DE")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.SANS_SERIF
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0ABC0")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private var graphDataList: List<JSONObject> = emptyList()
    
    data class Node(val id: String, val label: String, var x: Float = 0f, var y: Float = 0f)
    data class Edge(val from: String, val to: String)
    
    private val nodes = mutableMapOf<String, Node>()
    private val edges = mutableListOf<Edge>()

    private var scaleFactor = 1f
    private var posX = 0f
    private var posY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = android.view.MotionEvent.INVALID_POINTER_ID
    
    private val scaleListener = object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.1f, Math.min(scaleFactor, 5.0f))
            invalidate()
            return true
        }
    }
    private val scaleDetector = android.view.ScaleGestureDetector(context, scaleListener)
    
    override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        
        val action = ev.actionMasked
        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val pointerIndex = ev.actionIndex
                lastTouchX = ev.getX(pointerIndex)
                lastTouchY = ev.getY(pointerIndex)
                activePointerId = ev.getPointerId(pointerIndex)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = ev.getX(pointerIndex)
                    val y = ev.getY(pointerIndex)
                    
                    if (!scaleDetector.isInProgress) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        posX += dx / scaleFactor
                        posY += dy / scaleFactor
                        invalidate()
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                activePointerId = android.view.MotionEvent.INVALID_POINTER_ID
            }
            android.view.MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = ev.getX(newPointerIndex)
                    lastTouchY = ev.getY(newPointerIndex)
                    activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    fun setData(jsonString: String) {
        setMultipleData(listOf(jsonString))
    }

    fun setMultipleData(jsonStrings: List<String>) {
        try {
            graphDataList = jsonStrings.map { JSONObject(it) }
            parseData()
            calculateStaticLayout()
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseData() {
        nodes.clear()
        edges.clear()
        
        for (json in graphDataList) {
            // 1. Support for the new Hierarchical/Tree format
            val coreConcept = json.optJSONObject("core_concept")
            if (coreConcept != null) {
                parseHierarchicalData(coreConcept, null)
                continue
            }

            // 2. Fallback to the old Flat/Relational format
            val nodesArray = json.optJSONArray("nodes") ?: json.optJSONArray("extracted_entities")
            val localEdgesArray = json.optJSONArray("localEdges") ?: json.optJSONArray("extracted_relationships")
            
            if (nodesArray != null) {
                for (i in 0 until nodesArray.length()) {
                    val nodeObj = nodesArray.getJSONObject(i)
                    val id = nodeObj.optString("id", "")
                    val label = nodeObj.optString("label", "")
                    if (id.isNotEmpty()) {
                        nodes[id] = Node(id, label)
                    }
                }
            }
            
            if (localEdgesArray != null) {
                for (i in 0 until localEdgesArray.length()) {
                    val edgeObj = localEdgesArray.getJSONObject(i)
                    val from = edgeObj.optString("from", edgeObj.optString("from_id", ""))
                    val to = edgeObj.optString("to", edgeObj.optString("to_id", ""))
                    if (from.isNotEmpty() && to.isNotEmpty()) {
                        edges.add(Edge(from, to))
                    }
                }
            }
        }
    }

    private fun parseHierarchicalData(nodeObj: JSONObject, parentId: String?) {
        val id = nodeObj.optString("id", "")
        val label = nodeObj.optString("label", "")
        if (id.isNotEmpty()) {
            nodes[id] = Node(id, label)
            if (parentId != null) {
                edges.add(Edge(parentId, id))
            }
        }
        
        // Traverse grouped dependencies array
        val deps = nodeObj.optJSONObject("dependencies")
        if (deps != null) {
            val keys = deps.keys()
            while (keys.hasNext()) {
                val groupName = keys.next()
                val groupArray = deps.optJSONArray(groupName)
                if (groupArray != null) {
                    for (i in 0 until groupArray.length()) {
                        val childObj = groupArray.getJSONObject(i)
                        parseHierarchicalData(childObj, id)
                    }
                }
            }
        }
        
        // Traverse arbitrary direct nested sub-concepts (like "notation" from the prompt)
        val keys = nodeObj.keys()
        while(keys.hasNext()) {
            val key = keys.next()
            if (key != "dependencies") {
                val potentialChild = nodeObj.optJSONObject(key)
                if (potentialChild != null && potentialChild.has("id") && potentialChild.has("label")) {
                    parseHierarchicalData(potentialChild, id)
                }
            }
        }
    }

    private fun calculateStaticLayout() {
        val adj = mutableMapOf<String, MutableList<String>>()
        val inDegree = mutableMapOf<String, Int>()
        
        nodes.keys.forEach { inDegree[it] = 0 }
        
        edges.forEach { edge ->
            adj.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
            inDegree[edge.to] = (inDegree[edge.to] ?: 0) + 1
        }
        
        val roots = inDegree.filter { it.value == 0 }.keys.toList().takeIf { it.isNotEmpty() } ?: nodes.keys.take(1).toList()
        
        val leafCount = mutableMapOf<String, Int>()
        val visitedForLeaves = mutableSetOf<String>()
        
        fun computeLeaves(nodeId: String): Int {
            if (leafCount.containsKey(nodeId)) return leafCount[nodeId]!!
            visitedForLeaves.add(nodeId)
            val children = adj[nodeId]?.filter { !visitedForLeaves.contains(it) } ?: emptyList()
            if (children.isEmpty()) {
                leafCount[nodeId] = 1
                return 1
            }
            var sum = 0
            for (child in children) {
                sum += computeLeaves(child)
            }
            leafCount[nodeId] = max(1, sum)
            return leafCount[nodeId]!!
        }
        
        for (root in roots) {
            computeLeaves(root)
        }
        
        val visited = mutableSetOf<String>()
        
        val rootAngleRange = (2 * PI).toFloat()
        val totalRootLeaves = roots.sumOf { leafCount[it] ?: 1 }
        var currentStartAngle = 0f
        
        val layerRadius = 500f // increased for more breathing room
        
        fun layoutNode(nodeId: String, centerAngle: Float, angleRange: Float, depth: Int) {
            if (visited.contains(nodeId)) return
            visited.add(nodeId)
            
            val node = nodes[nodeId] ?: return
            val radius = depth * layerRadius
            
            node.x = radius * cos(centerAngle)
            node.y = radius * sin(centerAngle)
            
            val children = adj[nodeId]?.filter { !visited.contains(it) } ?: emptyList()
            if (children.isNotEmpty()) {
                val totalLeaves = children.sumOf { leafCount[it] ?: 1 }
                var childStartAngle = centerAngle - angleRange / 2f
                for (child in children) {
                    val leaves = leafCount[child] ?: 1
                    val childSlice = angleRange * (leaves.toFloat() / max(1, totalLeaves))
                    val childCenterAngle = childStartAngle + childSlice / 2f
                    layoutNode(child, childCenterAngle, childSlice, depth + 1)
                    childStartAngle += childSlice
                }
            }
        }
        
        for (root in roots) {
            val leaves = leafCount[root] ?: 1
            val slice = rootAngleRange * (leaves.toFloat() / max(1, totalRootLeaves))
            val center = currentStartAngle + slice / 2f
            layoutNode(root, center, slice, 0)
            currentStartAngle += slice
        }
        
        // --- Post-Layout Collision Resolution ---
        val iterations = 50
        val padding = 60f
        val nodeList = nodes.values.toList()
        for (i in 0 until iterations) {
            var moved = false
            // Force Damping: Cooling factor decreases linearly over iterations to prevent jitter/oscillation
            val cooling = 1.0f - (i.toFloat() / iterations.toFloat())
            
            for (j in 0 until nodeList.size) {
                for (k in j + 1 until nodeList.size) {
                    val n1 = nodeList[j]
                    val n2 = nodeList[k]
                    
                    val w1 = max(textPaint.measureText(n1.label) + 80f, 150f)
                    val w2 = max(textPaint.measureText(n2.label) + 80f, 150f)
                    val h1 = 80f
                    val h2 = 80f
                    
                    val dx = n2.x - n1.x
                    val dy = n2.y - n1.y
                    val dist = sqrt(dx * dx + dy * dy)
                    
                    val minXDist = (w1 + w2) / 2f + padding
                    val minYDist = (h1 + h2) / 2f + padding
                    
                    if (kotlin.math.abs(dx) < minXDist && kotlin.math.abs(dy) < minYDist) {
                        moved = true
                        val overlapX = minXDist - kotlin.math.abs(dx)
                        val overlapY = minYDist - kotlin.math.abs(dy)
                        
                        if (dist > 0.1f) {
                            val pushForce = 0.5f * cooling
                            val nx = dx / dist
                            val ny = dy / dist
                            val pushDist = max(overlapX, overlapY) * 0.5f * pushForce
                            
                            n1.x -= nx * pushDist
                            n1.y -= ny * pushDist
                            n2.x += nx * pushDist
                            n2.y += ny * pushDist
                        } else {
                            n2.x += 10f
                            n2.y += 10f
                        }
                    }
                }
            }
            if (!moved) break
        }
    }

    fun showGlobalConnectors() {
        if (graphDataList.isEmpty()) return
        
        for (json in graphDataList) {
            val globalConnectorsArray = json.optJSONArray("globalConnectors") ?: continue
            
            for (i in 0 until globalConnectorsArray.length()) {
                val connector = globalConnectorsArray.getJSONObject(i)
                val localNodeId = connector.getString("localNodeId")
                val targetGlobalNodeId = connector.getString("targetGlobalNodeId")
                
                if (!nodes.containsKey(targetGlobalNodeId)) {
                    val label = targetGlobalNodeId.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                    nodes[targetGlobalNodeId] = Node(targetGlobalNodeId, label)
                }
                
                // Link from global hub -> local concept so it becomes the root
                edges.add(Edge(targetGlobalNodeId, localNodeId))
            }
        }
        
        calculateStaticLayout()
        invalidate()
    }

    fun restartSpawning() {
        parseData()
        calculateStaticLayout()
        scaleFactor = 1f
        posX = 0f
        posY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.drawColor(Color.parseColor("#FAFAFA"))

        canvas.save()
        
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(posX, posY)

        // Draw curved branches (cubic bezier)
        val path = Path()
        for (edge in edges) {
            val fromNode = nodes[edge.from]
            val toNode = nodes[edge.to]
            if (fromNode != null && toNode != null) {
                path.reset()
                path.moveTo(fromNode.x, fromNode.y)
                
                val r1 = sqrt(fromNode.x * fromNode.x + fromNode.y * fromNode.y)
                val r2 = sqrt(toNode.x * toNode.x + toNode.y * toNode.y)
                val rMid = (r1 + r2) / 2f
                
                var angle1 = atan2(fromNode.y, fromNode.x)
                var angle2 = atan2(toNode.y, toNode.x)
                
                // Fix for nodes at the exact center (r = 0), otherwise their angle defaults to 0
                // causing all central lines to shoot rightward before curving.
                if (r1 < 1f) angle1 = angle2
                if (r2 < 1f) angle2 = angle1
                
                val cp1x = rMid * cos(angle1)
                val cp1y = rMid * sin(angle1)
                
                val cp2x = rMid * cos(angle2)
                val cp2y = rMid * sin(angle2)
                
                path.cubicTo(cp1x, cp1y, cp2x, cp2y, toNode.x, toNode.y)
                canvas.drawPath(path, edgePaint)
            }
        }

        // Draw pill/capsule nodes
        val rectHeight = 80f
        val rx = rectHeight / 2f
        
        for (node in nodes.values) {
            val textWidth = textPaint.measureText(node.label)
            val rectWidth = max(textWidth + 80f, 150f)
            
            val left = node.x - rectWidth / 2f
            val top = node.y - rectHeight / 2f
            val right = node.x + rectWidth / 2f
            val bottom = node.y + rectHeight / 2f
            
            canvas.drawRoundRect(left, top, right, bottom, rx, rx, nodeFillPaint)
            canvas.drawRoundRect(left, top, right, bottom, rx, rx, nodeStrokePaint)
            
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(node.label, node.x, node.y - textOffset, textPaint)
        }
        
        canvas.restore()
    }
}

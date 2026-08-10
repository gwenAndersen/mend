package com.mend

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

class DataMapActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val dataMapView = DataMapView(this)
        
        val frameLayout = FrameLayout(this)
        frameLayout.addView(dataMapView)
        
        val buttonLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        
        val btnUnified = Button(this).apply {
            text = "Unified Map"
            setOnClickListener {
                dataMapView.showGlobalConnectors()
            }
        }
        
        val btnRestart = Button(this).apply {
            text = "Restart"
            setOnClickListener {
                dataMapView.restartSpawning()
            }
        }
        
        val btnCopyPrompt = Button(this).apply {
            text = "Copy Prompt"
            setOnClickListener {
                val promptText = """
You are a highly precise Data Extraction Agent. Your job is to extract semantic concepts from the provided text and organize them into a strict hierarchical curriculum tree, regardless of the source formatting.

1. STABILITY RULE: Ignore the source's structural formatting, grammar style, or conversational noise. Focus on the core underlying facts, principles, and their dependencies.

2. TARGET SCHEMA: Extract the data into the following strict nested JSON format. 
{
  "metadata": {
    "domain": "e.g., Quantum Mechanics Mathematics",
    "summary": "A high-level map detailing the essential tools required to understand the core concept."
  },
  "core_concept": {
    "id": "snake_case_root_id",
    "label": "The primary, overarching topic of the text",
    "complexity": "advanced | intermediate | basic",
    "description": "A concise explanation of the main topic.",
    "dependencies": {
      "thematic_group_name_1": [
        {
          "id": "snake_case_sub_id",
          "label": "A required sub-concept",
          "complexity": "intermediate",
          "description": "What this is and why it matters to the core concept.",
          "nested_sub_concept": { 
             // Optional
          }
        }
      ],
      "thematic_group_name_2": [
      ]
    }
  }
}

3. EXTRACTION GUIDELINES:
- HIERARCHY OVER FLATNESS: Identify the single most important "core_concept" first. Everything else must be categorized as a dependency of this core concept.
- THEMATIC GROUPING: Group the dependencies into logical, snake_case arrays. Do not just dump them into one list.
- NESTING: If a concept is entirely dependent on a parent concept, nest it inside the parent.
- DESCRIPTIONS: Ensure the 'description' clearly explains *how* the concept connects to its parent or the core concept.
- Output ONLY the valid JSON object. Do not include any conversational text or markdown wrappers outside the JSON block.
                """.trimIndent()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Prompt", promptText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@DataMapActivity, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
        
        buttonLayout.addView(btnUnified)
        buttonLayout.addView(btnRestart)
        buttonLayout.addView(btnCopyPrompt)
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 100
        }
        frameLayout.addView(buttonLayout, params)
        
        setContentView(frameLayout)
        
        val jsonStrings = mutableListOf<String>()
        try {
            val dir = java.io.File("/sdcard/alyf/mend/data/")
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                if (files != null) {
                    for (f in files) {
                        if (f.extension == "json") {
                            jsonStrings.add(f.readText())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (jsonStrings.isEmpty()) {
            jsonStrings.add("""
            {
              "session": {
                "groupId": "chem_molecular_orbitals_001",
                "groupTitle": "Molecular Orbital Theory & Hybridization",
                "timestamp": 1782672000
              },
              "nodes": [
                { "id": "atomic_orbitals", "label": "Atomic Orbitals", "type": "basic" },
                { "id": "hybridization", "label": "Orbital Hybridization", "type": "intermediate" },
                { "id": "molecular_orbitals", "label": "Molecular Orbitals", "type": "advanced" }
              ],
              "localEdges": [
                { "from": "atomic_orbitals", "to": "hybridization", "relation": "undergo" },
                { "from": "hybridization", "to": "molecular_orbitals", "relation": "overlap_to_form" }
              ],
              "globalConnectors": [
                { "localNodeId": "atomic_orbitals", "targetGlobalNodeId": "quantum_mechanics_core", "relation": "derived_from" }
              ]
            }
            """.trimIndent())
        }
        
        dataMapView.setMultipleData(jsonStrings)
    }
}

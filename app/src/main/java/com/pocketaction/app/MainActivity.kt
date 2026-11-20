package com.pocketaction.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var input: EditText
    private lateinit var actions: LinearLayout
    private lateinit var history: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var db: HistoryDb
    private var pendingScanMode: String = ScannerActivity.MODE_QR

    private val phoneRegex = Regex("""(?<!\w)(?:\+?\d[\d\s().-]{7,}\d)(?!\w)""")
    private val emailRegex = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
    private val urlRegex = Regex("""https?://[^\s<>()]+|www\.[^\s<>()]+""", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex(
        """\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\s+\d{1,2}(?:,\s*\d{4})?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchScanner(pendingScanMode)
        else toast("Camera access is needed only for scanning. You can enable it in Android Settings.")
    }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_SHARED_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) processImageUris(BatchProcessingPolicy.limit(uris))
        else toast("No image was selected.")
    }

    private val batchImagePicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_SHARED_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) processBatchImages(BatchProcessingPolicy.limit(uris))
        else toast("No images selected for batch processing.")
    }

    private val scanResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanned = result.data?.getStringExtra(ScannerActivity.EXTRA_RESULT).orEmpty()
            if (scanned.isNotBlank()) {
                input.setText(scanned)
                statusText.text = if (pendingScanMode == ScannerActivity.MODE_QR) "QR code scanned" else "Text captured from camera"
                analyseText()
                findViewById<ScrollView>(ROOT_SCROLL_ID)?.smoothScrollTo(0, 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = HistoryDb(this)
        input = EditText(this)
        buildUi()
        handleIncomingIntent(intent)
        refreshHistory()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun buildUi() {
        window.statusBarColor = C_WHITE
        window.navigationBarColor = C_WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(C_WHITE) }
        val scroll = ScrollView(this).apply { id = ROOT_SCROLL_ID; isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(28))
        }

        // Centered brand header, with menu and search kept at the edges.
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(textButton("☰", 27, C_NAVY) { showNavigationMenu() }, LinearLayout.LayoutParams(dp(42), dp(54)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false }
        val logo = label("P", 25, true, C_WHITE).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(C_BLUE, C_BLUE_DARK)).apply { cornerRadius = dp(13).toFloat() }
        }
        brand.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(9) })
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        titleBlock.addView(label("Pocket Action", 23, true, C_NAVY).apply { gravity = Gravity.CENTER })
        titleBlock.addView(label("v1.0.0", 12, false, C_VERSION).apply { gravity = Gravity.CENTER; alpha = 0.55f })
        brand.addView(titleBlock)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(textButton("⌕", 30, C_NAVY) { showSearchDialog() }, LinearLayout.LayoutParams(dp(42), dp(54)))
        content.addView(header, matchWidth().apply { bottomMargin = dp(12) })

        // Hero banner: restrained pale blue, with a prominent Scan control.
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(17), dp(14), dp(17))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#F8FBFF"), Color.parseColor("#EAF4FF"))).apply {
                cornerRadius = dp(24).toFloat(); setStroke(dp(1), C_BORDER)
            }
        }
        val heroCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroCopy.addView(label("Turn your shared content into actions", 22, true, C_NAVY))
        heroCopy.addView(label("Share text or images from any app. We'll find useful details and prepare actions for you.", 13, false, C_BODY).apply {
            setPadding(0, dp(7), dp(8), 0)
        })
        statusText = label("Ready when you are", 10, false, C_MUTED).apply { setPadding(0, dp(6), 0, 0) }
        heroCopy.addView(statusText)
        hero.addView(heroCopy, LinearLayout.LayoutParams(0, -2, 1f))
        val scanTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(C_BLUE, C_BLUE_DARK)).apply {
                cornerRadius = dp(20).toFloat(); setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { showScanModePopup() }
        }
        scanTile.addView(label("▣", 38, true, C_WHITE).apply { gravity = Gravity.CENTER })
        scanTile.addView(label("Scan", 16, true, C_WHITE).apply { gravity = Gravity.CENTER })
        hero.addView(scanTile, LinearLayout.LayoutParams(dp(112), dp(118)).apply { leftMargin = dp(6) })
        content.addView(hero, matchWidth().apply { bottomMargin = dp(14) })

        // Full-width Input container with three equal white action cards.
        val inputSection = sectionContainer()
        addSectionHeading(inputSection, "Input", "Add content to get started.")
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        inputRow.addView(inputTile("↗", "Share to App", "From any app", C_BLUE) {
            toast("Open Share in another app and choose Pocket Action.")
        }, equalTileParams())
        inputRow.addView(inputTile("▧", "Choose Images", "From gallery", C_BLUE) { pickImage() }, equalTileParams().apply { leftMargin = dp(7) })
        inputRow.addView(inputTile("▤", "Paste Text", "From clipboard", C_BLUE) { pasteText() }, equalTileParams().apply { leftMargin = dp(7) })
        inputSection.addView(inputRow)
        content.addView(inputSection, matchWidth().apply { bottomMargin = dp(14) })

        // Full-width Quick Actions container. White icon cards; each icon retains its own color.
        val quickSection = sectionContainer()
        addSectionHeading(quickSection, "Quick Actions", "Detected actions will appear here after you add content.")
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        quickSection.addView(actions)
        content.addView(quickSection, matchWidth().apply { bottomMargin = dp(14) })

        // Workspace has a unique stacked-layers glyph inside a white icon holder.
        content.addView(sectionTitle("Workspace"), matchWidth().apply { bottomMargin = dp(7) })
        val workspaceCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cardBackground(C_WHITE, 20)
            elevation = dp(2).toFloat()
            isClickable = true; isFocusable = true
            setOnClickListener { showWorkspaceDialog() }
        }
        val layersHolder = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = cardBackground(C_WHITE, 15)
            elevation = dp(1).toFloat()
        }
        layersHolder.addView(label("▱", 37, true, C_BLUE).apply { gravity = Gravity.CENTER })
        workspaceCard.addView(layersHolder, LinearLayout.LayoutParams(dp(62), dp(62)).apply { rightMargin = dp(12) })
        val workspaceCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        workspaceCopy.addView(label("Review & Process", 17, true, C_NAVY))
        workspaceCopy.addView(label("View, edit and manage extracted content in detail.", 12, false, C_BODY))
        workspaceCard.addView(workspaceCopy, LinearLayout.LayoutParams(0, -2, 1f))
        workspaceCard.addView(label("›", 30, false, C_NAVY))
        content.addView(workspaceCard, matchWidth().apply { bottomMargin = dp(14) })

        val historyHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        historyHeader.addView(sectionTitle("Recent Captures"), LinearLayout.LayoutParams(0, -2, 1f))
        historyHeader.addView(textButton("View All", 13, C_BLUE) { showAllHistory() })
        content.addView(historyHeader, matchWidth().apply { bottomMargin = dp(6) })
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = cardBackground(C_WHITE, 18); setPadding(dp(9), dp(4), dp(9), dp(4)); elevation = dp(2).toFloat() }
        content.addView(history, matchWidth())

        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        showEmptyActions()
    }

    private fun showScanModePopup() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(14)) }
        val popupHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        popupHeader.addView(label("Choose Scan Mode", 20, true, C_NAVY), LinearLayout.LayoutParams(0, -2, 1f))
        popupHeader.addView(textButton("×", 27, C_NAVY) { (body.tag as? AlertDialog)?.dismiss() }, LinearLayout.LayoutParams(dp(34), dp(40)))
        body.addView(popupHeader, matchWidth().apply { bottomMargin = dp(10) })
        val qrOption = scanOption("▦", "QR Scan", "Scan QR codes and read their contents.", C_BLUE) { }
        val cameraOption = scanOption("◎", "Camera Scan", "Capture documents, images and text using the camera.", C_BLUE) { }
        body.addView(qrOption, matchWidth())
        body.addView(cameraOption, matchWidth().apply { topMargin = dp(9) })
        val dialog = AlertDialog.Builder(this).setView(body).create()
        body.tag = dialog
        qrOption.setOnClickListener { dialog.dismiss(); requestScan(ScannerActivity.MODE_QR) }
        cameraOption.setOnClickListener { dialog.dismiss(); requestScan(ScannerActivity.MODE_TEXT) }
        dialog.show()
        dialog.window?.setBackgroundDrawable(roundedDrawable(C_WHITE, 26, C_BORDER))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), -2)
    }

    private fun scanOption(icon: String, title: String, description: String, tint: Int, callback: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = cardBackground(C_WHITE, 17)
            elevation = dp(2).toFloat()
            val holder = LinearLayout(context).apply { gravity = Gravity.CENTER; background = roundedDrawable(C_ICON_BG, 14, C_ICON_BG) }
            holder.addView(label(icon, 27, true, tint).apply { gravity = Gravity.CENTER })
            addView(holder, LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) })
            val copy = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(label(title, 16, true, C_NAVY))
            copy.addView(label(description, 11, false, C_BODY))
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(label("›", 27, false, C_NAVY))
            setOnClickListener { callback() }
        }

    private fun requestScan(mode: String) {
        pendingScanMode = mode
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchScanner(mode)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner(mode: String) {
        scanResult.launch(Intent(this, ScannerActivity::class.java).putExtra(ScannerActivity.EXTRA_MODE, mode))
    }

    private fun showWorkspaceDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(8)) }
        layout.addView(label("Review & Process", 21, true, C_NAVY))
        layout.addView(label("Review the content, make corrections, then choose what to do.", 12, false, C_BODY).apply { setPadding(0, dp(4), 0, dp(10)) })
        val existingContent = if (::input.isInitialized) input.text.toString() else ""
        input = EditText(this).apply {
            setText(existingContent)
            hint = "Shared or extracted content appears here…"
            textSize = 14f; minLines = 5; maxLines = 10; gravity = Gravity.TOP
            setTextColor(C_NAVY); setHintTextColor(C_MUTED); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedDrawable(C_SURFACE, 13, C_BORDER)
        }
        layout.addView(input, matchWidth())
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(smallButton("Analyse") { analyseText() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        row.addView(smallButton("Save") { saveCapture() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        row.addView(smallButton("Copy") { copyText() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        layout.addView(row, matchWidth().apply { topMargin = dp(10) })
        AlertDialog.Builder(this).setView(layout).setNegativeButton("Close", null).create().also { dialog ->
            dialog.setOnShowListener { dialog.window?.setBackgroundDrawable(roundedDrawable(C_WHITE, 22, C_BORDER)) }
            dialog.show()
        }
    }

    private fun showNavigationMenu() {
        val items = arrayOf("Home", "Workspace — Review & Process", "Recent Captures", "Batch Process", "Settings", "About Pocket Action")
        AlertDialog.Builder(this).setTitle("Pocket Action").setItems(items) { _, which ->
            when (which) {
                0 -> findViewById<ScrollView>(ROOT_SCROLL_ID)?.smoothScrollTo(0, 0)
                1 -> showWorkspaceDialog()
                2 -> showAllHistory()
                3 -> startBatchProcess()
                4 -> showSettings()
                5 -> showAbout()
            }
        }.show()
    }

    private fun showSettings() {
        val message = "Appearance: White theme with subtle blue accents\nOCR: On-device Latin text recognition\nStorage: Captures are stored locally on this device\nPrivacy: Camera is requested only when you start scanning. No contacts, SMS, location or notification access."
        AlertDialog.Builder(this).setTitle("Settings").setMessage(message).setPositiveButton("Done", null).show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this).setTitle("Pocket Action").setMessage("Version 1.0.0\n\nTurn shared content into useful actions.\n\nPocket Action processes content you explicitly share, select or scan. Captures remain on this device unless you choose to share them.").setPositiveButton("Done", null).show()
    }

    private fun showSearchDialog() {
        val query = EditText(this).apply { hint = "Search all saved captures"; textSize = 15f }
        AlertDialog.Builder(this).setTitle("Search Pocket Action").setView(query)
            .setPositiveButton("Search") { _, _ -> showHistoryFiltered(query.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showAllHistory() = showHistoryFiltered("")

    private fun showHistoryFiltered(query: String) {
        val matches = db.search(query)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        if (matches.isEmpty()) body.addView(label(if (query.isBlank()) "Nothing saved yet." else "No matching captures.", 13, false, C_BODY))
        matches.forEachIndexed { index, capture ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            row.addView(label(capture.text.replace('\n', ' ').take(180), 13, true, C_NAVY), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(smallButton("Open") { ensureInput().setText(capture.text); analyseText(); showWorkspaceDialog() })
            row.addView(smallButton("Delete") { confirmDeleteCapture(capture.id, query) }.apply { setTextColor(Color.parseColor("#B42318")) }, LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(5) })
            body.addView(row)
            if (index < matches.lastIndex) body.addView(View(this).apply { setBackgroundColor(C_BORDER) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(if (query.isBlank()) "Recent Captures" else "Search Results")
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton("Done", null)
        if (query.isBlank() && matches.isNotEmpty()) builder.setNeutralButton("Clear All") { _, _ -> confirmClearAll() }
        builder.show()
    }

    private fun confirmDeleteCapture(id: Long, query: String) {
        AlertDialog.Builder(this).setTitle("Delete saved capture?")
            .setMessage("This removes the selected capture from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> db.delete(id); refreshHistory(); showHistoryFiltered(query) }
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this).setTitle("Clear all saved captures?")
            .setMessage("This permanently deletes all Pocket Action captures stored on this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear All") { _, _ -> db.clearAll(); refreshHistory(); toast("Saved captures deleted") }
            .show()
    }

    private fun sectionContainer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(11), dp(13), dp(13))
        background = cardBackground(C_WHITE, 22)
    }

    private fun addSectionHeading(container: LinearLayout, title: String, subtitle: String) {
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), 0, 0, dp(9)) }
        heading.addView(label(title, 19, true, C_NAVY))
        heading.addView(label(subtitle, 12, false, C_BODY))
        container.addView(heading)
    }

    private fun inputTile(glyph: String, title: String, subtitle: String, tint: Int, callback: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(9))
            background = cardBackground(C_WHITE, 16); elevation = dp(2).toFloat()
            addView(label(glyph, 33, true, tint).apply { gravity = Gravity.CENTER })
            addView(label(title, 12, true, C_NAVY).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
            addView(label(subtitle, 11, false, C_BODY).apply { gravity = Gravity.CENTER })
            isClickable = true; isFocusable = true; setOnClickListener { callback() }
        }

    private fun equalTileParams() = LinearLayout.LayoutParams(0, dp(112), 1f)

    private fun quickTile(glyph: String, title: String, tint: Int, callback: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(2), dp(7), dp(2), dp(7))
            background = cardBackground(C_WHITE, 15); elevation = dp(2).toFloat()
            addView(label(glyph, 28, true, tint).apply { gravity = Gravity.CENTER })
            addView(label(title, 10, true, C_NAVY).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
        }

    private fun showEmptyActions() {
        actions.removeAllViews()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = listOf(
            Triple("☎", "Call", Color.parseColor("#12B65A")),
            Triple("♙", "Save\\nContact", Color.parseColor("#1685F8")),
            Triple("✉", "Send\\nEmail", Color.parseColor("#8848E8")),
            Triple("⌖", "Maps", Color.parseColor("#F04452")),
            Triple("▦", "Calendar", Color.parseColor("#F28A16")),
            Triple("↗", "Open\\nLink", Color.parseColor("#0DB5B0"))
        )
        items.forEachIndexed { index, item ->
            val tile = quickTile(item.first, item.second.replace("\\n", "\n"), item.third) { }
            row.addView(tile, LinearLayout.LayoutParams(0, dp(92), 1f).apply { if (index > 0) leftMargin = dp(5) })
        }
        actions.addView(row)
        actions.addView(label("Add content to see available actions.", 11, false, C_MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(7), 0, 0) })
    }

    private fun showDetectedActions(items: List<Pair<String, () -> Unit>>) {
        actions.removeAllViews()
        items.forEach { (title, callback) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)); background = cardBackground(C_WHITE, 13) }
            row.addView(label(title, 13, true, C_NAVY), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(smallButton("Open") { callback() })
            actions.addView(row, matchWidth().apply { bottomMargin = dp(6) })
        }
    }

    private fun pasteText() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) return toast("Clipboard is empty")
        val value = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (value.isBlank()) return toast("Clipboard does not contain text")
        ensureInput().setText(value)
        statusText.text = "Text pasted from clipboard"
        analyseText()
    }

    private fun ensureInput(): EditText {
        if (!::input.isInitialized) input = EditText(this)
        return input
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                val sharedUri = getSharedUri(intent)
                when {
                    !sharedText.isNullOrBlank() && sharedUri != null -> processImageUris(listOf(sharedUri), sharedText)
                    !sharedText.isNullOrBlank() -> { ensureInput().setText(sharedText); statusText.text = "Text received from another app"; analyseText() }
                    sharedUri != null -> processImageUris(listOf(sharedUri))
                    else -> toast("This shared item did not contain readable text or an image.")
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = getSharedUris(intent)
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (uris.isNotEmpty()) processImageUris(uris, sharedText)
                else if (!sharedText.isNullOrBlank()) { ensureInput().setText(sharedText); statusText.text = "Text received from another app"; analyseText() }
                else toast("No image attachments were provided.")
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val processed = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!processed.isNullOrBlank()) { ensureInput().setText(processed); statusText.text = "Selected text received"; analyseText() }
            }
        }
    }

    private fun getSharedUri(intent: Intent): Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else {
        @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun getSharedUris(intent: Intent): List<Uri> {
        val result = linkedSetOf<Uri>()
        val streams: ArrayList<Uri>? = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else {
            @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        streams?.filterNotNull()?.forEach { result.add(it) }
        intent.clipData?.let { clip -> for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let { result.add(it) } }
        return result.take(MAX_SHARED_IMAGES)
    }

    private fun processImageUris(uris: List<Uri>, initialText: String? = null) {
        if (uris.isEmpty()) return
        statusText.text = "Reading ${uris.size} image${if (uris.size == 1) "" else "s"}…"
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val extracted = mutableListOf<String>()
        var index = 0
        fun processNext() {
            if (index >= uris.size) {
                recognizer.close()
                val ocrText = extracted.filter { it.isNotBlank() }.joinToString("\n\n")
                val combined = listOfNotNull(initialText?.takeIf { it.isNotBlank() }, ocrText.takeIf { it.isNotBlank() }).joinToString("\n\n")
                if (combined.isBlank()) { statusText.text = "No readable text found"; showEmptyActions(); toast("No readable text was found.") }
                else { ensureInput().setText(combined); statusText.text = "Text extracted from ${extracted.size} of ${uris.size} image(s)"; analyseText() }
                return
            }
            val current = uris[index++]
            try {
                recognizer.process(InputImage.fromFilePath(this, current))
                    .addOnSuccessListener { result -> if (result.text.isNotBlank()) extracted.add(result.text); processNext() }
                    .addOnFailureListener { error -> android.util.Log.w(TAG, "OCR failed", error); processNext() }
            } catch (error: Exception) { android.util.Log.w(TAG, "Unable to open shared image", error); processNext() }
        }
        processNext()
    }

    private fun pickImage() {
        imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Opens the dedicated batch flow. The picker and OCR queue are both capped at ten images. */
    private fun startBatchProcess() {
        batchImagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Processes one image at a time and reports an independent result for every selected image. */
    private fun processBatchImages(uris: List<Uri>) {
        val safeUris = BatchProcessingPolicy.limit(uris)
        if (safeUris.isEmpty()) return

        val extractedByImage = MutableList(safeUris.size) { "" }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val summary = label("Preparing ${safeUris.size} images…", 13, false, C_BODY)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = safeUris.size
            progress = 0
        }
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(summary, matchWidth())
        body.addView(progress, matchWidth().apply { topMargin = dp(9); bottomMargin = dp(10) })
        body.addView(rows, matchWidth())

        val rowLabels = safeUris.indices.map { index ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(8), dp(9), dp(8))
                background = cardBackground(C_WHITE, 12)
            }
            row.addView(label("Image ${index + 1}", 13, true, C_NAVY), LinearLayout.LayoutParams(0, -2, 1f))
            val state = label("Waiting", 12, false, C_MUTED)
            row.addView(state)
            rows.addView(row, matchWidth().apply { bottomMargin = dp(5) })
            state
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Batch Process")
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save Results", null)
            .create()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var currentIndex = 0
        var readableCount = 0
        var inFlight = false
        var cancelled = false
        var recognizerClosed = false
        var completed = false

        fun closeRecognizerOnce() {
            if (!recognizerClosed) {
                recognizerClosed = true
                recognizer.close()
            }
        }

        fun updateSaveButton() {
            if (!dialog.isShowing || isFinishing || isDestroyed) return
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                completed && extractedByImage.any { it.isNotBlank() }
        }

        fun finishCancelled() {
            cancelled = true
            if (!inFlight) closeRecognizerOnce()
        }

        dialog.setOnDismissListener {
            // ML Kit tasks are not synchronously cancellable. Stop queuing work and
            // close the recognizer after the currently running task completes.
            finishCancelled()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val combined = extractedByImage.mapIndexedNotNull { index, text ->
                    text.takeIf { it.isNotBlank() }?.let { "Image ${index + 1}\n$it" }
                }.joinToString("\n\n")
                if (combined.isNotBlank()) {
                    db.save(combined)
                    refreshHistory()
                    toast("Batch results saved")
                    dialog.dismiss()
                } else {
                    toast("There is no extracted text to save.")
                }
            }
        }
        dialog.show()

        fun finishBatch() {
            if (cancelled || completed) return
            completed = true
            closeRecognizerOnce()
            val noTextOrFailed = safeUris.size - readableCount
            summary.text = "Finished: $readableCount of ${safeUris.size} images contained readable text; $noTextOrFailed had no readable text or could not be processed."
            progress.progress = safeUris.size
            if (readableCount > 0) {
                val combined = extractedByImage.mapIndexedNotNull { index, text ->
                    text.takeIf { it.isNotBlank() }?.let { "Image ${index + 1}\n$it" }
                }.joinToString("\n\n")
                ensureInput().setText(combined)
                statusText.text = "Batch complete: $readableCount/${safeUris.size} images processed"
                analyseText()
            } else {
                statusText.text = "Batch complete: no readable text found"
            }
            updateSaveButton()
        }

        lateinit var processNext: () -> Unit
        processNext = {
            if (cancelled || isFinishing || isDestroyed) {
                finishCancelled()
            } else if (currentIndex >= safeUris.size) {
                finishBatch()
            } else {
                val index = currentIndex++
                inFlight = true
                rowLabels[index].text = "Processing…"
                summary.text = "Processing image ${index + 1} of ${safeUris.size} — one at a time"
                try {
                    recognizer.process(InputImage.fromFilePath(this, safeUris[index]))
                        .addOnSuccessListener { result ->
                            inFlight = false
                            if (cancelled || isFinishing || isDestroyed) {
                                finishCancelled()
                                return@addOnSuccessListener
                            }
                            if (result.text.isNotBlank()) {
                                extractedByImage[index] = result.text
                                readableCount++
                                rowLabels[index].text = "Complete"
                                rowLabels[index].setTextColor(Color.parseColor("#16803C"))
                            } else {
                                rowLabels[index].text = "No text found"
                            }
                            progress.progress = index + 1
                            processNext()
                        }
                        .addOnFailureListener { error ->
                            inFlight = false
                            android.util.Log.w(TAG, "Batch OCR failed for image ${index + 1}", error)
                            if (cancelled || isFinishing || isDestroyed) {
                                finishCancelled()
                                return@addOnFailureListener
                            }
                            rowLabels[index].text = "Failed"
                            rowLabels[index].setTextColor(Color.parseColor("#B42318"))
                            progress.progress = index + 1
                            processNext()
                        }
                } catch (error: Exception) {
                    inFlight = false
                    android.util.Log.w(TAG, "Unable to open batch image ${index + 1}", error)
                    if (cancelled || isFinishing || isDestroyed) {
                        finishCancelled()
                    } else {
                        rowLabels[index].text = "Failed"
                        rowLabels[index].setTextColor(Color.parseColor("#B42318"))
                        progress.progress = index + 1
                        processNext()
                    }
                }
            }
        }
        processNext()
    }

    private fun analyseText() {
        if (!::input.isInitialized) return
        val text = input.text.toString().trim()
        if (text.isBlank()) { showEmptyActions(); return }
        val results = mutableListOf<Pair<String, () -> Unit>>()
        val phone = phoneRegex.findAll(text).map { it.value.trim() }.firstOrNull { it.count(Char::isDigit) in 8..15 }
        val email = emailRegex.find(text)?.value
        val url = urlRegex.find(text)?.value
        val date = dateRegex.find(text)?.value
        if (phone != null) results.add("Call $phone" to {
            launchWithFallback(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")), "No dialer app is available.", "Copy phone number", phone)
        })
        if (phone != null || email != null) results.add("Save contact" to { saveContact(phone, email) })
        if (email != null) results.add("Email $email" to { launchWithFallback(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(email)}")), "No email app is available.", "Copy email address", email) })
        if (url != null) {
            val safeUrl = InputValidation.normalizeHttpUrl(url)
            if (safeUrl != null) results.add("Open $safeUrl" to { launchWithFallback(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)), "No compatible browser is available.", "Copy link", safeUrl) })
        }
        if (date != null) results.add("Create calendar event for $date" to { createCalendarEvent(text, date) })
        val mapQuery = text.take(300)
        results.add("Search this content in Maps" to { launchWithFallback(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(mapQuery)}")), "No compatible maps app is available.", "Copy location or address", mapQuery) })
        results.add("Share extracted text" to {
            val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
            try { startActivity(Intent.createChooser(share, "Share text")) }
            catch (_: Exception) { showCopyFallback("Sharing is unavailable.", "Copy extracted text", text) }
        })
        showDetectedActions(results)
    }

    private fun saveContact(phone: String?, email: String?) {
        val contact = Intent(Intent.ACTION_INSERT).setType(ContactsContract.Contacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, phone.orEmpty())
            .putExtra(ContactsContract.Intents.Insert.EMAIL, email.orEmpty())
        launchWithFallback(contact, "No compatible contacts app is available.", "Copy contact details", listOfNotNull(phone, email).joinToString("\n"))
    }

    private fun launchWithFallback(intent: Intent, unavailableMessage: String, fallbackLabel: String, fallbackContent: String) {
        try {
            // Attempt the action directly; resolveActivity can be visibility-filtered on newer Android versions.
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            showCopyFallback(unavailableMessage, fallbackLabel, fallbackContent)
        } catch (_: SecurityException) {
            showCopyFallback("Android blocked this action.", fallbackLabel, fallbackContent)
        } catch (_: Exception) {
            showCopyFallback("This action could not be opened.", fallbackLabel, fallbackContent)
        }
    }

    private fun showCopyFallback(message: String, buttonLabel: String, content: String) {
        AlertDialog.Builder(this).setTitle("Alternative available").setMessage(message)
            .setNegativeButton("Close", null)
            .setPositiveButton(buttonLabel) { _, _ ->
                if (content.isNotBlank()) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pocket Action", content))
                    toast("Copied to clipboard")
                }
            }.show()
    }

    private fun createCalendarEvent(source: String, detectedDate: String) {
        val parsed = parseDate(detectedDate)
        val event = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, source.lineSequence().first().take(100))
            .putExtra(CalendarContract.Events.DESCRIPTION, source)
        if (parsed != null) { event.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, parsed.time); event.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, parsed.time + 3600000L) }
        launchWithFallback(event, "No compatible calendar app is available.", "Copy event details", "Title: ${source.lineSequence().first().take(100)}\nDate: $detectedDate\n\n$source")
    }

    private fun parseDate(value: String): Date? {
        val formats = listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "d-M-yyyy", "dd/MM/yy", "d/M/yy", "MMM d, yyyy", "MMMM d, yyyy", "MMM d", "MMMM d")
        for (format in formats) try {
            val parsed = SimpleDateFormat(format, Locale.ENGLISH).apply { isLenient = false }.parse(value) ?: continue
            if (!format.contains("yy")) {
                val c = java.util.Calendar.getInstance(); val parsedCal = java.util.Calendar.getInstance().apply { time = parsed }; parsedCal.set(java.util.Calendar.YEAR, c.get(java.util.Calendar.YEAR)); return parsedCal.time
            }
            return parsed
        } catch (_: Exception) { }
        return null
    }

    private fun saveCapture() {
        if (!::input.isInitialized || input.text.isBlank()) return toast("Nothing to save")
        val savedId = db.save(input.text.toString().trim())
        if (savedId == -1L) return toast("Could not save this capture. Your content has not been marked as saved.")
        statusText.text = "Capture saved on this device"
        toast("Saved on this device")
        refreshHistory()
    }

    private fun copyText() {
        if (!::input.isInitialized || input.text.isBlank()) return toast("Nothing to copy")
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pocket Action", input.text.toString())); toast("Copied")
    }

    private fun refreshHistory() {
        if (!::history.isInitialized) return
        history.removeAllViews()
        val captures = db.recent(limit = 5)
        if (captures.isEmpty()) { history.addView(label("Nothing saved yet.", 12, false, C_MUTED).apply { setPadding(dp(10), dp(12), dp(10), dp(12)) }); return }
        captures.forEachIndexed { index, capture ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(7), dp(9), dp(7), dp(9)) }
            val glyph = if (capture.text.contains("http", true)) "▤" else "▧"
            val iconHolder = LinearLayout(this).apply { gravity = Gravity.CENTER; background = roundedDrawable(C_ICON_BG, 12, C_ICON_BG) }
            iconHolder.addView(label(glyph, 24, true, C_BLUE).apply { gravity = Gravity.CENTER })
            row.addView(iconHolder, LinearLayout.LayoutParams(dp(48), dp(46)).apply { rightMargin = dp(10) })
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            body.addView(label(capture.text.replace('\n', ' ').take(34), 12, true, C_NAVY))
            body.addView(label("Saved on this device", 10, false, C_MUTED))
            row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(textButton("›", 24, C_NAVY) { ensureInput().setText(capture.text); analyseText(); showWorkspaceDialog() })
            row.addView(textButton("×", 22, Color.parseColor("#B42318")) { confirmDeleteCapture(capture.id, "") })
            history.addView(row)
            if (index < captures.lastIndex) history.addView(View(this).apply { setBackgroundColor(C_BORDER) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun smallButton(text: String, callback: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 11f; isAllCaps = false; minHeight = dp(42); setTextColor(C_BLUE_DARK)
        background = roundedDrawable(C_BUTTON_BG, 12, C_BORDER); setOnClickListener { callback() }
    }

    private fun textButton(text: String, size: Int, tint: Int, callback: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(tint); gravity = Gravity.CENTER; setTypeface(typeface, Typeface.NORMAL)
        isClickable = true; isFocusable = true; setOnClickListener { callback() }
    }

    private fun sectionTitle(text: String) = label(text, 19, true, C_NAVY).apply {
        setPadding(dp(2), dp(2), 0, dp(2))
    }

    private fun label(text: String, size: Int, bold: Boolean, tint: Int) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(tint); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun cardBackground(fill: Int, radius: Int) = roundedDrawable(fill, radius, C_BORDER)
    private fun roundedDrawable(fill: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MAX_SHARED_IMAGES = 10
        private const val TAG = "PocketAction"
        private const val ROOT_SCROLL_ID = 0x504143
        private const val C_WHITE = Color.WHITE
        private const val C_NAVY = 0xFF101A4A.toInt()
        private const val C_BLUE = 0xFF0878F9.toInt()
        private const val C_BLUE_DARK = 0xFF0758D6.toInt()
        private const val C_BODY = 0xFF343B52.toInt()
        private const val C_MUTED = 0xFF818A9D.toInt()
        private const val C_VERSION = 0xFF8EA2C2.toInt()
        private const val C_BORDER = 0xFFD7E3F3.toInt()
        private const val C_ICON_BG = 0xFFF1F7FF.toInt()
        private const val C_SURFACE = 0xFFFAFCFF.toInt()
        private const val C_BUTTON_BG = 0xFFF4F8FF.toInt()
    }
}

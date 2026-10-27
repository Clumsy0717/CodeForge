package com.codeforge.mobile

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var fileList: ListView
    private lateinit var tab: TextView
    private lateinit var tabContainer: LinearLayout
    private val openTabs = mutableListOf<File>()
    private var applyingAutoPair = false
    private lateinit var breadcrumb: TextView
    private lateinit var outputLabel: TextView
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var lineNumbers: TextView
    private lateinit var explorerPanel: LinearLayout
    private lateinit var fileAdapter: ArrayAdapter<String>
    private lateinit var outputScroll: ScrollView
    private var terminalExpanded = true

    private lateinit var projectDir: File
    private var currentFile: File? = null
    private var suppressDirtyFlag = false
    private var isDirty = false
    private val files = mutableListOf<File>()
    private val expandedDirs = linkedSetOf<String>("")
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val preferences by lazy { getSharedPreferences("codeforge_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editor = findViewById(R.id.codeEditor)
        fileList = findViewById(R.id.fileList)
        tabContainer = findViewById(R.id.tabContainer)
        tab = TextView(this)
        breadcrumb = findViewById(R.id.tvBreadcrumb)
        outputLabel = findViewById(R.id.tvOutputLabel)
        output = findViewById(R.id.tvOutput)
        status = findViewById(R.id.tvStatus)
        lineNumbers = findViewById(R.id.tvLineNumbers)
        explorerPanel = findViewById(R.id.explorerPanel)
        outputScroll = findViewById(R.id.outputScroll)

        // Start with a clean workspace so old demo files from earlier versions do not appear.
        // Existing MyProject files are preserved; the new workspace is empty.
        projectDir = File(getExternalFilesDir(null), "projects/Workspace")
        if (!projectDir.exists()) projectDir.mkdirs()

        setupFileList()
        setupEditor()
        // Start on a purpose-built welcome screen. No sample files are created.
        refreshFiles()
        showWelcomeScreen()

        explorerPanel.visibility = if (preferences.getBoolean("explorer_visible", true)) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.btnSaveTop).setOnClickListener { saveCurrentFile() }
        findViewById<TextView>(R.id.btnNew).setOnClickListener { showNewItemMenu() }
        findViewById<TextView>(R.id.tvProject).text = projectDir.name
        findViewById<Button>(R.id.btnRun).setOnClickListener { runCurrentFile() }
        findViewById<ImageButton>(R.id.btnMore).setOnClickListener { showMoreMenu() }
        findViewById<TextView>(R.id.btnClearOutput).setOnClickListener {
            output.text = "Output cleared."
            outputLabel.text = "OUTPUT  ·  Ready"
        }
        findViewById<TextView>(R.id.btnTerminal).setOnClickListener {
            terminalExpanded = !terminalExpanded
            outputScroll.visibility = if (terminalExpanded) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.tvOutputLabel).text = if (terminalExpanded) "TERMINAL  ·  Output" else "TERMINAL  ·  Hidden"
        }
        findViewById<TextView>(R.id.btnActivityExplorer).setOnClickListener { toggleExplorer() }
        findViewById<TextView>(R.id.btnActivitySearch).setOnClickListener { showSearchDialog() }
        findViewById<TextView>(R.id.btnActivitySettings).setOnClickListener { showSettings() }
        // Adapt sidebar and output height to available screen size.
        val screenWidthDp = resources.configuration.screenWidthDp
        explorerPanel.layoutParams = explorerPanel.layoutParams.apply {
            width = dp(if (screenWidthDp < 360) 112 else if (screenWidthDp < 600) 140 else 190)
        }
        outputScroll.layoutParams = outputScroll.layoutParams.apply { height = dp(if (resources.configuration.screenHeightDp < 600) 66 else 104) }
        applyEditorSettings()
        applyTheme()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply { hint = "Find text in current file"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Find") .setView(input)
            .setPositiveButton("Find") { _, _ ->
                val query = input.text.toString()
                if (query.isNotEmpty()) {
                    val index = editor.text.toString().indexOf(query, (editor.selectionStart.coerceAtLeast(0)))
                    if (index >= 0) { editor.requestFocus(); editor.setSelection(index, index + query.length) }
                    else toast("Text not found")
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showWelcomeScreen() {
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? LinearLayout ?: return
        // Keep the existing editor workspace intact, but hide it until the user opens a file/project.
        for (i in 0 until root.childCount) root.getChildAt(i).visibility = View.GONE

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(18))
            setBackgroundColor(Color.rgb(30, 30, 30))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "‹/›"
            textSize = 18f
            setTextColor(Color.rgb(79, 193, 255))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(39, 67, 82), 12)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        top.addView(mark)
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(TextView(this).apply {
            text = "CodeForge"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        brand.addView(TextView(this).apply {
            text = "YOUR CODE. YOUR WORKSPACE."
            textSize = 10f
            letterSpacing = .08f
            setTextColor(Color.rgb(150, 155, 164))
            setPadding(0, dp(3), 0, 0)
        })
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        welcome.addView(top)

        val heading = TextView(this).apply {
            text = "Welcome back.\nWhat are we building today?"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(242, 244, 248))
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(38), 0, dp(10))
        }
        welcome.addView(heading)
        welcome.addView(TextView(this).apply {
            text = "Start fresh or continue a project on your device."
            textSize = 14f
            setTextColor(Color.rgb(160, 165, 174))
            setPadding(0, 0, 0, dp(24))
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun action(icon: String, title: String, subtitle: String, accent: Int, click: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(14), dp(12), dp(14))
                background = rounded(Color.rgb(37, 37, 38), 14, Color.rgb(57, 59, 63))
                isClickable = true
                isFocusable = true
                setOnClickListener { showWorkspace(); click() }
            }
            val iconView = TextView(this).apply {
                text = icon
                textSize = 22f
                setTextColor(accent)
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(47, 49, 53), 10)
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            }
            card.addView(iconView)
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), 0, dp(6), 0)
            }
            labels.addView(TextView(this).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(235, 237, 241))
            })
            labels.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.rgb(155, 160, 168))
                setPadding(0, dp(4), 0, 0)
            })
            card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(TextView(this).apply {
                text = "›"
                textSize = 25f
                setTextColor(Color.rgb(130, 136, 146))
                gravity = Gravity.CENTER
            })
            actions.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        action("＋", "New File", "Create a Python, PHP, HTML, or other file", Color.rgb(79, 193, 255)) { showCreateFileDialog() }
        action("▱", "Open Folder", "Browse a project folder on this device", Color.rgb(230, 194, 117)) { openFolderPicker() }
        action("↗", "Open File", "Continue editing an existing file", Color.rgb(79, 193, 255)) { openFilePicker() }
        action("▣", "New Project", "Start with a clean project workspace", Color.rgb(78, 201, 176)) { newProject() }
        welcome.addView(actions)

        val spacer = View(this)
        welcome.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val footer = TextView(this).apply {
            text = "●  READY     Offline editing available"
            textSize = 11f
            setTextColor(Color.rgb(160, 190, 180))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(38, 48, 45), 10)
        }
        welcome.addView(footer)
        root.addView(welcome, 0)
    }

    private fun showWorkspace() {
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? LinearLayout ?: return
        if (root.childCount > 0) root.getChildAt(0).visibility = View.GONE
        for (i in 1 until root.childCount) root.getChildAt(i).visibility = View.VISIBLE
    }

    private fun toggleExplorer() {
        val visible = explorerPanel.visibility != View.VISIBLE
        explorerPanel.visibility = if (visible) View.VISIBLE else View.GONE
        preferences.edit().putBoolean("explorer_visible", visible).apply()
        toast(if (visible) "Explorer shown" else "Explorer hidden")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun showWelcomeDialog() {
        val items = arrayOf(
            "New File        Create a code file",
            "Open Folder     Choose a project folder",
            "Open File       Open an existing file",
            "New Project     Start a clean project"
        )
        AlertDialog.Builder(this)
            .setTitle("CodeForge\nYour code. Your workspace.")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showCreateFileDialog()
                    1 -> openFolderPicker()
                    2 -> openFilePicker()
                    3 -> newProject()
                }
            }
            .setNegativeButton("Close", null)
            .setOnCancelListener { status.text = "  Ready · Offline editing available" }
            .show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try { startActivityForResult(intent, 4101) } catch (_: Exception) { toast("No file picker is available.") }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try { startActivityForResult(intent, 4102) } catch (_: Exception) { toast("No folder picker is available.") }
    }

    private fun newProject() {
        val input = EditText(this).apply { hint = "MyProject"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("New project")
            .setMessage("Create a clean project folder on this device.")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().replace(Regex("[^A-Za-z0-9 _-]"), "")
                if (name.isBlank()) { toast("Enter a project name."); return@setPositiveButton }
                val next = File(getExternalFilesDir(null), "projects/$name")
                if (!next.exists()) next.mkdirs()
                projectDir = next
                expandedDirs.clear(); expandedDirs.add("")
                findViewById<TextView>(R.id.tvProject).text = projectDir.name
                currentFile = null
                editor.setText("")
                tab.text = "No file open"
                breadcrumb.text = "project  ›  no file"
                refreshFiles()
                toast("Project created: $name")
            }.show()
    }

    @Deprecated("Deprecated in Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            4101 -> try {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "opened_file.txt"
                val target = File(projectDir, name)
                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                refreshFiles(); openFile(target)
            } catch (_: Exception) { toast("Couldn't import that file.") }
            4102 -> {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val pickedName = queryDisplayName(uri).ifBlank { "ImportedProject" }.replace(Regex("[^A-Za-z0-9 _.-]"), "_")
                    val target = File(getExternalFilesDir(null), "projects/$pickedName")
                    if (target.exists()) target.deleteRecursively()
                    target.mkdirs()
                    copyTreeFromUri(uri, target)
                    projectDir = target
                    expandedDirs.clear(); expandedDirs.add("")
                    currentFile = null; openTabs.clear(); isDirty = false
                    editor.setText(""); refreshTabs(); refreshFiles()
                    findViewById<TextView>(R.id.tvProject).text = projectDir.name
                    breadcrumb.text = "${projectDir.name}  ›  no file"
                    status.text = "  Folder opened · ${projectDir.name}"
                    showWorkspace()
                    toast("Folder opened: $pickedName")
                } catch (e: Exception) { toast("Couldn't open folder: ${e.localizedMessage ?: "unsupported folder"}") }
            }
        }
    }

    private fun setupEditor() {
        editor.typeface = Typeface.MONOSPACE
        // Tab inserts indentation into the editor instead of moving focus to another control.
        editor.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_TAB && event.action == KeyEvent.ACTION_DOWN) {
                val start = editor.selectionStart.coerceAtLeast(0)
                val end = editor.selectionEnd.coerceAtLeast(start)
                val size = preferences.getInt("indent_size", 4).coerceIn(1, 8)
                val indent = " ".repeat(size)
                editor.text.replace(start, end, indent)
                editor.setSelection(start + indent.length)
                true
            } else false
        }
        editor.addTextChangedListener(object : TextWatcher {
            private var pairStart = -1
            private var typedChar: Char? = null
            private var cursorBefore = 0
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { cursorBefore = editor.selectionStart }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLineNumbers(s?.toString().orEmpty())
                if (!applyingAutoPair && count == 1 && before == 0 && start < (s?.length ?: 0)) {
                    typedChar = s?.get(start); pairStart = start
                } else { typedChar = null; pairStart = -1 }
                if (!suppressDirtyFlag) {
                    isDirty = true; updateTabTitle()
                    status.text = "  SYNTAX: ${currentFile?.let { languageLabel(it) } ?: "Code"}  ·  ${s?.toString()?.lines()?.size ?: 1} lines  ·  Unsaved changes"
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val ch = typedChar
                // Emmet-style HTML starter: typing ! on an otherwise empty line expands to a full document.
                if (ch == '!' && currentFile?.extension?.lowercase() in listOf("html", "htm") && !applyingAutoPair) {
                    val cursor = editor.selectionStart.coerceIn(0, s.length)
                    val lineStart = s.lastIndexOf("\n", (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                    val lineEnd = s.indexOf("\n", cursor).let { if (it < 0) s.length else it }
                    if (s.substring(lineStart, lineEnd).trim() == "!") {
                        val template = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>Document</title>\n</head>\n<body>\n    \n</body>\n</html>"
                        applyingAutoPair = true
                        s.replace(lineStart, lineEnd, template)
                        editor.setSelection((template.indexOf("    \n") + 5).coerceIn(0, s.length))
                        applyingAutoPair = false
                        typedChar = null; pairStart = -1
                        return
                    }
                }
                val close = when (ch) { '(' -> ')'; '[' -> ']'; '{' -> '}'; '"' -> '"'; '\'' -> '\''; '`' -> '`'; else -> null }
                if (close != null && pairStart >= 0 && !applyingAutoPair && editor.selectionStart == pairStart + 1) {
                    applyingAutoPair = true
                    s.insert(pairStart + 1, close.toString())
                    editor.setSelection(pairStart + 1)
                    applyingAutoPair = false
                } else if (ch == '>' && !applyingAutoPair && currentFile?.extension?.lowercase() in listOf("html", "htm", "xml", "svg")) {
                    // Complete an opening HTML/XML tag: <div>| becomes <div></div> with cursor between tags.
                    val cursor = editor.selectionStart.coerceAtLeast(0).coerceAtMost(s.length)
                    val beforeCursor = s.substring(0, cursor)
                    val match = Regex("<([A-Za-z][A-Za-z0-9:-]*)(?:\\s[^<>]*)?>$").find(beforeCursor)
                    if (match != null && !beforeCursor.endsWith("/>") && match.groupValues[1].lowercase() !in setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")) {
                        val tag = match.groupValues[1]
                        applyingAutoPair = true
                        s.insert(cursor, "</$tag>")
                        editor.setSelection(cursor)
                        applyingAutoPair = false
                    }
                }
                typedChar = null; pairStart = -1
                val file = currentFile
                if (file != null && !suppressDirtyFlag) {
                    // Syntax highlighting must preserve the cursor; otherwise typing quotes can jump to the top.
                    val selectionStart = editor.selectionStart.coerceAtLeast(0)
                    val selectionEnd = editor.selectionEnd.coerceAtLeast(selectionStart)
                    SyntaxHighlighter.apply(s, file.extension)
                    editor.setSelection(selectionStart.coerceAtMost(s.length), selectionEnd.coerceAtMost(s.length))
                }
            }
        })
    }

    private fun updateLineNumbers(text: String) {
        val count = text.count { it == '\n' } + 1
        lineNumbers.text = (1..count).joinToString("\n")
    }

    private fun setupFileList() {
        refreshFiles()
        fileAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf<String>()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                (row as? TextView)?.apply {
                    setTextColor(if (files.getOrNull(position) == currentFile) 0xFFFFFFFF.toInt() else getColor(R.color.cf_text))
                    textSize = 12f; maxLines = 2; setPadding(10, 10, 4, 10)
                    setBackgroundColor(if (files.getOrNull(position) == currentFile) 0xFF37373D.toInt() else 0x00000000)
                }
                return row
            }
        }
        fileList.adapter = fileAdapter
        fileList.setOnItemClickListener { _, _, position, _ ->
            val item = files.getOrNull(position) ?: return@setOnItemClickListener
            if (item.isDirectory) {
                val rel = relativePath(item)
                if (!expandedDirs.add(rel)) expandedDirs.remove(rel)
                refreshFiles()
            } else if (isDirty) {
                AlertDialog.Builder(this).setTitle("Unsaved changes")
                    .setMessage("Save changes before opening another file?")
                    .setNegativeButton("Discard") { _, _ -> openFile(item) }
                    .setNeutralButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ -> if (saveCurrentFile(false)) openFile(item) }.show()
            } else openFile(item)
        }
        fileList.setOnItemLongClickListener { _, _, position, _ ->
            val item = files.getOrNull(position) ?: return@setOnItemLongClickListener true
            val kind = if (item.isDirectory) "folder and everything inside it" else "file"
            AlertDialog.Builder(this).setTitle("Delete ${item.name}?")
                .setMessage("This will permanently delete this $kind from the CodeForge workspace.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> deleteWorkspaceItem(item) }.show()
            true
        }
    }

    private fun relativePath(file: File): String = try { file.relativeTo(projectDir).path.replace(File.separatorChar, '/') } catch (_: Exception) { file.name }

    private fun displayFilePath(file: File): String {
        val relative = relativePath(file)
        val parent = file.parentFile?.let { relativePath(it) } ?: ""
        val depth = if (parent.isBlank() || parent == ".") 0 else parent.count { it == '/' } + 1
        val label = if (file.isDirectory) "▸ ${file.name}/" else "   ${file.name}"
        return "  ".repeat(depth) + label
    }

    private fun refreshFiles() {
        files.clear()
        fun collect(dir: File, relative: String) {
            val children = dir.listFiles()?.filterNot { it.name.startsWith(".") }?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: return
            children.forEach { child ->
                files.add(child)
                if (child.isDirectory && expandedDirs.contains(relativePath(child))) collect(child, relativePath(child))
            }
        }
        if (projectDir.exists()) collect(projectDir, "")
        if (::fileAdapter.isInitialized) {
            fileAdapter.clear()
            fileAdapter.addAll(files.map { displayFilePath(it) })
            fileAdapter.notifyDataSetChanged()
        }
        if (::breadcrumb.isInitialized && currentFile == null) breadcrumb.text = "${projectDir.name}  ›  no file"
        if (::status.isInitialized) status.text = "  ${projectDir.name}  ·  ${files.count { it.isFile }} files"
        runCatching { findViewById<TextView>(R.id.tvProject).text = projectDir.name }
    }

    private fun deleteWorkspaceItem(item: File) {
        if (item == projectDir) { toast("You can't delete the open project root."); return }
        if (currentFile != null && (currentFile == item || (item.isDirectory && currentFile!!.path.startsWith(item.path + File.separator)))) {
            currentFile = null; isDirty = false; editor.setText("")
        }
        openTabs.removeAll { it == item || (item.isDirectory && it.path.startsWith(item.path + File.separator)) }
        expandedDirs.remove(relativePath(item))
        if (item.deleteRecursively()) {
            refreshTabs(); refreshFiles()
            breadcrumb.text = "${projectDir.name}  ›  no file"
            outputLabel.text = "PROBLEMS  ·  Workspace updated"
            output.text = "Deleted ${item.name}."
            toast("Deleted ${item.name}")
        } else toast("Couldn't delete this item.")
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = ""
        contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0).orEmpty()
        }
        return name
    }

    private fun copyTreeFromUri(treeUri: Uri, target: File) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        copyDocumentChildren(treeUri, rootId, target)
    }

    private fun copyDocumentChildren(treeUri: Uri, parentId: String, targetDir: File) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol); val safeName = cursor.getString(nameCol).orEmpty().replace("/", "_").replace("\\", "_")
                if (safeName.isBlank() || safeName.startsWith(".")) continue
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                val dest = File(targetDir, safeName)
                if (cursor.getString(mimeCol) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    dest.mkdirs(); copyDocumentChildren(treeUri, id, dest)
                } else contentResolver.openInputStream(childUri)?.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            }
        }
    }

    private fun openFile(file: File) {
        try {
            if (!file.exists() || !file.isFile) return
            currentFile = file
            suppressDirtyFlag = true
            editor.setText(file.readText())
            editor.setSelection(editor.text.length)
            suppressDirtyFlag = false
            isDirty = false
            if (!openTabs.contains(file)) openTabs.add(file)
            refreshTabs()
            breadcrumb.text = "project  ›  ${file.relativeTo(projectDir).path}"
            status.text = "  SYNTAX: ${languageLabel(file)}  ·  ${editor.text.lines().size} lines  ·  UTF-8"
            outputLabel.text = "OUTPUT  ·  Opened ${file.name}"
            output.text = "Opened ${file.name}. Changes stay on this device until you choose to run remotely."
            updateLineNumbers(editor.text.toString())
            SyntaxHighlighter.apply(editor.text, file.extension)
            refreshFiles()
        } catch (e: IOException) {
            suppressDirtyFlag = false
            toast("Couldn't open file: ${e.localizedMessage}")
        }
    }

    private fun refreshTabs() {
        if (!::tabContainer.isInitialized) return
        tabContainer.removeAllViews()
        openTabs.removeAll { !it.exists() }
        openTabs.forEach { file ->
            val selected = file == currentFile
            val tabView = TextView(this).apply {
                text = file.name + if (selected && isDirty) "  •" else ""
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                setTextColor(if (selected) Color.WHITE else getColor(R.color.cf_muted))
                setBackgroundColor(if (selected) Color.rgb(37, 37, 38) else Color.TRANSPARENT)
                setOnClickListener {
                    if (file == currentFile) return@setOnClickListener
                    if (isDirty) {
                        AlertDialog.Builder(this@MainActivity).setTitle("Unsaved changes")
                            .setMessage("Save changes before switching tabs?")
                            .setNegativeButton("Discard") { _, _ -> openFile(file) }
                            .setNeutralButton("Cancel", null)
                            .setPositiveButton("Save") { _, _ -> if (saveCurrentFile(false)) openFile(file) }.show()
                    } else openFile(file)
                }
            }
            tabContainer.addView(tabView, LinearLayout.LayoutParams(-2, -1))
        }
    }

    private fun updateTabTitle() {
        refreshTabs()
        if (::fileAdapter.isInitialized) fileAdapter.notifyDataSetChanged()
    }

    private fun saveCurrentFile(showToast: Boolean = true): Boolean {
        val file = currentFile ?: run {
            toast("Create or open a file first.")
            return false
        }
        return try {
            file.writeText(editor.text.toString())
            isDirty = false
            updateTabTitle()
            outputLabel.text = "OUTPUT  ·  Saved ${file.name}"
            status.text = "  Saved  ·  ${languageLabel(file)}  ·  UTF-8"
            refreshFiles()
            if (showToast) toast("Saved ${file.name}")
            true
        } catch (e: IOException) {
            toast("Couldn't save file: ${e.localizedMessage}")
            false
        }
    }

    private fun showNewItemMenu() {
        AlertDialog.Builder(this)
            .setTitle("Create in Workspace")
            .setItems(arrayOf("New File", "New Folder")) { _, which ->
                if (which == 0) showCreateFileDialog() else showCreateFolderDialog()
            }
            .show()
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = "assets/images"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("New folder")
            .setMessage("Enter a folder name or relative path, such as css or images/icons.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().trim('/')
                val parts = name.split('/')
                if (parts.isEmpty() || parts.any { !isSafeFileName(it) }) {
                    toast("Use a simple folder name, like assets/images")
                    return@setPositiveButton
                }
                val folder = File(projectDir, name)
                if (folder.exists()) toast("That folder already exists.")
                else if (folder.mkdirs()) {
                    refreshFiles()
                    toast("Folder created")
                } else toast("Couldn't create folder.")
            }.show()
    }

    private fun showCreateFileDialog() {
        val input = EditText(this).apply {
            hint = "src/main.py"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("New file")
            .setMessage("Enter a file name or relative path, such as css/style.css.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                val parts = name.split('/')
                if (name.startsWith("/") || name.contains("\\") || parts.isEmpty() || parts.any { !isSafeFileName(it) }) {
                    toast("Use a relative path, like css/style.css")
                    return@setPositiveButton
                }
                val file = File(projectDir, name)
                if (file.exists()) {
                    toast("That file already exists.")
                } else {
                    try {
                        file.parentFile?.mkdirs()
                        file.createNewFile()
                        refreshFiles()
                        openFile(file)
                    } catch (_: IOException) {
                        toast("Couldn't create file.")
                    }
                }
            }.show()
    }

    private fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && name.length <= 80 && name != "." && name != ".." &&
            !name.contains("/") && !name.contains("\\") && !name.startsWith(".")

    private fun runCurrentFile() {
        if (preferences.getBoolean("save_before_run", true) && !saveCurrentFile(showToast = false)) return
        val file = currentFile ?: return
        when (LanguageAdapter.forFile(file)) {
            is LanguageAdapter.Web -> previewWebFile(file)
            is LanguageAdapter.Python, is LanguageAdapter.Php, is LanguageAdapter.Cpp -> runOnBackend(file)
            is LanguageAdapter.Unsupported -> {
                outputLabel.text = "OUTPUT  ·  Unsupported file"
                output.text = "No runner is configured for .${file.extension} files yet."
            }
        }
    }

    private fun previewWebFile(file: File) {
        // HTML is the entry point; load it from its real workspace path so relative CSS,
        // JavaScript, image, and nested-folder links resolve correctly.
        if (!file.extension.equals("html", true) && !file.extension.equals("htm", true)) {
            outputLabel.text = "OUTPUT  ·  Open HTML in preview"
            output.text = "CSS and JavaScript are support files. Open an .html file to preview the website with its linked assets."
            toast("Open an .html file to preview the website.")
            return
        }
        if (!saveCurrentFile(showToast = false)) return
        try {
            val webView = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = false
                setBackgroundColor(Color.rgb(255, 255, 255))
                loadUrl("file://${file.absolutePath}")
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle("Preview · ${file.name}")
                .setView(webView)
                .setPositiveButton("Close", null)
                .setNeutralButton("Open externally") { _, _ ->
                    try {
                        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "text/html")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Open HTML with"))
                    } catch (_: Exception) { toast("Couldn't open HTML externally.") }
                }
                .create()
            dialog.setOnDismissListener { webView.destroy() }
            dialog.show()
            outputLabel.text = "OUTPUT  ·  HTML preview"
            output.text = "Previewing ${file.name}. Relative links to CSS, JavaScript, images, and nested assets load from the workspace folder."
            status.text = "  HTML Preview  ·  ${file.relativeTo(projectDir).path}"
        } catch (e: Exception) {
            outputLabel.text = "OUTPUT  ·  Preview failed"
            output.text = "Couldn't preview HTML: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    private fun runOnBackend(file: File) {
        val endpoint = preferences.getString("backend_url", "").orEmpty().trim().trimEnd('/')
        if (endpoint.isBlank()) {
            showBackendSettings()
            return
        }
        val compiler = when (file.extension.lowercase()) {
            "py" -> "python"
            "php" -> "php"
            "cpp", "cc", "cxx" -> "cpp"
            "c" -> "c"
            else -> {
                toast("This file type is not supported by the online runner.")
                return
            }
        }
        val sourceCode = editor.text.toString()
        val runButton = findViewById<Button>(R.id.btnRun)
        outputLabel.text = "OUTPUT  ·  Running…"
        output.text = "Sending ${file.name} to OnlineCompiler.io…"
        runButton.isEnabled = false

        ioExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                val body = JSONObject()
                    .put("compiler", compiler)
                    .put("code", sourceCode)
                    .put("input", "")
                    .toString()
                    .toByteArray(StandardCharsets.UTF_8)
                connection = (URL("$endpoint/api/run").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(responseText) }.getOrNull()
                if (code !in 200..299) {
                    throw IOException(json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Backend returned HTTP $code")
                }
                val resultText = buildList {
                    val stdout = json?.optString("output").orEmpty().takeUnless { it == "null" }.orEmpty()
                    val stderr = json?.optString("error").orEmpty().takeUnless { it == "null" }.orEmpty()
                    val statusText = json?.optString("status").orEmpty().takeUnless { it == "null" }.orEmpty()
                    if (stdout.isNotBlank()) add(stdout.trimEnd())
                    if (stderr.isNotBlank()) add("ERROR:\n${stderr.trimEnd()}")
                    if (isEmpty()) add(statusText.ifBlank { "Program finished with no output." })
                }.joinToString("\n\n")
                runOnUiThread {
                    outputLabel.text = "OUTPUT  ·  Finished"
                    output.text = resultText
                    status.text = "  Online run  ·  ${languageLabel(file)}"
                    runButton.isEnabled = true
                }
            } catch (e: Exception) {
                connection?.disconnect()
                runOnUiThread {
                    outputLabel.text = "OUTPUT  ·  Run failed"
                    output.text = "Could not run code: ${e.localizedMessage ?: "Network error"}\n\nCheck your backend URL and OnlineCompiler.io API settings."
                    status.text = "  Online run failed"
                    runButton.isEnabled = true
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showBackendContract() {
        val contract = """
            CodeForge OnlineCompiler.io backend API

            POST /api/run
            {"compiler":"python","code":"print('Hello')","input":""}

            Returns output, error, status, and optional runtime details.

            Keep your OnlineCompiler.io API key in the backend .env file only.
            Never put the key in the Android app or GitHub.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Backend API contract")
            .setMessage(contract)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkCodeProblems() {
        val file = currentFile
        if (file == null) { toast("Open a file first."); return }
        val code = editor.text.toString()
        val ext = file.extension.lowercase()
        val problems = mutableListOf<String>()
        fun lineOf(index: Int) = code.substring(0, index.coerceIn(0, code.length)).count { it == '\n' } + 1
        if (code.isBlank()) problems += "Info: File is empty (line 1)."
        val stack = mutableListOf<Pair<Char, Int>>()
        var quote: Char? = null; var escaped = false; var lineComment = false; var blockComment = false
        code.forEachIndexed { i, ch ->
            if (ch == '\n') lineComment = false
            if (lineComment) return@forEachIndexed
            if (blockComment) { if (i > 0 && code[i-1] == '*' && ch == '/') blockComment = false; return@forEachIndexed }
            if (quote != null) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == quote) quote = null
                return@forEachIndexed
            }
            if ((ext in listOf("js", "ts", "java", "c", "cpp", "php", "css", "kt") && i + 1 < code.length && code.substring(i, i+2) == "//")) { lineComment = true; return@forEachIndexed }
            if (ext in listOf("js", "ts", "java", "c", "cpp", "php", "css", "kt") && i + 1 < code.length && code.substring(i, i+2) == "/*") { blockComment = true; return@forEachIndexed }
            if (ch == '\'' || ch == '"' || (ch == '`' && ext in listOf("js", "ts"))) { quote = ch; return@forEachIndexed }
            when (ch) {
                '(', '[', '{' -> stack.add(ch to i)
                ')', ']', '}' -> {
                    val expected = when (ch) { ')' -> '('; ']' -> '['; else -> '{' }
                    if (stack.lastOrNull()?.first == expected) stack.removeAt(stack.lastIndex)
                    else problems += "Error: Unexpected '$ch' at line ${lineOf(i)}."
                }
            }
        }
        stack.forEach { (ch, i) -> problems += "Error: '$ch' has no matching closing bracket (line ${lineOf(i)})." }
        if (quote != null) problems += "Warning: A quote may not be closed."
        if (blockComment) problems += "Warning: A block comment may not be closed."
        if (ext in listOf("html", "htm", "xml", "svg")) {
            val opens = Regex("<([A-Za-z][A-Za-z0-9-]*)(?:\\s[^<>]*)?>").findAll(code).map { it.groupValues[1].lowercase() }.toList()
            val closes = Regex("</([A-Za-z][A-Za-z0-9-]*)\\s*>").findAll(code).map { it.groupValues[1].lowercase() }.toList()
            val voids = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr", "!doctype")
            val unclosed = opens.filter { it !in voids && it !in closes }
            unclosed.take(8).forEach { tag -> problems += "Warning: <$tag> may be missing a closing tag." }
        }
        if (Regex("TODO|FIXME").containsMatchIn(code)) problems += "Info: TODO/FIXME marker found; check whether work is unfinished."
        if (problems.isEmpty()) problems += "No obvious bracket or quote problems found. This is a basic check, not a full compiler or language-aware linter."
        outputLabel.text = "PROBLEMS  ·  ${problems.count { it.startsWith("Error") }} errors, ${problems.count { it.startsWith("Warning") }} warnings"
        output.text = problems.joinToString("\n")
        toast("Basic code check finished")
    }

    private fun showMoreMenu() {
        val options = arrayOf("Check Code / Problems", "Open Folder", "Open File", "Settings", "Toggle explorer", "Save file", "Configure backend", "Backend API contract", "About CodeForge")
        AlertDialog.Builder(this)
            .setTitle("CodeForge")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCodeProblems()
                    1 -> openFolderPicker()
                    2 -> openFilePicker()
                    3 -> showSettings()
                    4 -> toggleExplorer()
                    5 -> saveCurrentFile()
                    6 -> showBackendSettings()
                    7 -> showBackendContract()
                    8 -> AlertDialog.Builder(this)
                        .setTitle("About CodeForge")
                        .setMessage("A lightweight, offline-first mobile code editor. Editing and local saving work without internet. PHP, Python, and C++ run remotely after you configure a secure backend.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }.show()
    }


    private fun showSettings() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        fun label(text: String) {
            container.addView(TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(getColor(R.color.cf_text))
                setPadding(0, dp(10), 0, dp(4))
            })
        }
        label("Editor font size")
        val fontSize = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(preferences.getInt("font_size", 14).toString())
            hint = "12–24"
        }
        container.addView(fontSize)

        label("Indent size (spaces)")
        val indent = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(preferences.getInt("indent_size", 4).toString())
            hint = "2 or 4"
        }
        container.addView(indent)

        val wrap = android.widget.CheckBox(this).apply {
            text = "Word wrap"
            setTextColor(getColor(R.color.cf_text))
            isChecked = preferences.getBoolean("word_wrap", false)
        }
        container.addView(wrap)
        val lineNumbersToggle = android.widget.CheckBox(this).apply {
            text = "Show line numbers"
            setTextColor(getColor(R.color.cf_text))
            isChecked = preferences.getBoolean("show_line_numbers", true)
        }
        container.addView(lineNumbersToggle)
        val saveBeforeRun = android.widget.CheckBox(this).apply {
            text = "Save file before running"
            setTextColor(getColor(R.color.cf_text))
            isChecked = preferences.getBoolean("save_before_run", true)
        }
        container.addView(saveBeforeRun)

        label("Editor theme")
        val themes = arrayOf("Dark", "Light", "Midnight")
        val themeSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, themes)
            setSelection(themes.indexOf(preferences.getString("editor_theme", "Dark")).coerceAtLeast(0))
        }
        container.addView(themeSpinner)

        label("Online runner backend URL")
        val backend = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            hint = "https://your-backend.example"
            setText(preferences.getString("backend_url", ""))
        }
        container.addView(backend)
        val scroll = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle("CodeForge Settings")
            .setView(scroll)
            .setNeutralButton("Test connection") { _, _ ->
                val url = backend.text.toString().trim().trimEnd('/')
                preferences.edit().putString("backend_url", url).apply()
                testBackendConnection(url)
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save settings") { _, _ ->
                val font = fontSize.text.toString().toIntOrNull()?.coerceIn(10, 32) ?: 14
                val tabSize = indent.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 4
                val url = backend.text.toString().trim().trimEnd('/')
                val localHttp = url.startsWith("http://10.0.2.2") || url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
                if (url.isNotBlank() && !url.startsWith("https://") && !localHttp) {
                    toast("Use HTTPS, or a local emulator URL for testing.")
                    return@setPositiveButton
                }
                preferences.edit()
                    .putInt("font_size", font)
                    .putInt("indent_size", tabSize)
                    .putBoolean("word_wrap", wrap.isChecked)
                    .putBoolean("show_line_numbers", lineNumbersToggle.isChecked)
                    .putBoolean("save_before_run", saveBeforeRun.isChecked)
                    .putString("backend_url", url)
                    .putString("editor_theme", themes[themeSpinner.selectedItemPosition])
                    .apply()
                applyEditorSettings()
                applyTheme()
                toast("Settings saved")
            }
            .show()
    }

    private fun applyEditorSettings() {
        if (!::editor.isInitialized) return
        editor.textSize = preferences.getInt("font_size", 14).toFloat()
        editor.setHorizontallyScrolling(!preferences.getBoolean("word_wrap", false))
        editor.isHorizontalScrollBarEnabled = !preferences.getBoolean("word_wrap", false)
        val showLines = preferences.getBoolean("show_line_numbers", true)
        if (::lineNumbers.isInitialized) lineNumbers.visibility = if (showLines) View.VISIBLE else View.GONE
        editor.setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun applyTheme() {
        if (!::editor.isInitialized) return
        val theme = preferences.getString("editor_theme", "Dark") ?: "Dark"
        val bg = when (theme) { "Light" -> Color.rgb(250, 250, 250); "Midnight" -> Color.rgb(15, 20, 35); else -> Color.rgb(30, 30, 30) }
        val fg = if (theme == "Light") Color.rgb(35, 35, 35) else Color.rgb(220, 220, 220)
        editor.setBackgroundColor(bg); editor.setTextColor(fg)
        editor.setHintTextColor(if (theme == "Light") Color.GRAY else Color.rgb(120, 130, 145))
        lineNumbers.setBackgroundColor(bg)
        lineNumbers.setTextColor(if (theme == "Light") Color.GRAY else Color.rgb(106, 106, 106))
        output.setBackgroundColor(if (theme == "Light") Color.WHITE else bg)
        output.setTextColor(if (theme == "Light") Color.rgb(20, 120, 75) else Color.rgb(78, 220, 170))
    }

    private fun testBackendConnection(baseUrl: String) {
        if (baseUrl.isBlank()) {
            toast("Enter your backend URL first.")
            return
        }
        toast("Testing backend connection…")
        ioExecutor.execute {
            try {
                val connection = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                }
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle(if (code in 200..299) "Backend connected" else "Backend responded with an error")
                        .setMessage("HTTP $code\n$body")
                        .setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this).setTitle("Connection failed")
                        .setMessage("${e.localizedMessage ?: "Could not reach the server"}\n\nCheck that the backend is running, the URL is correct, and your phone can reach the computer.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun showBackendSettings() {
        val input = EditText(this).apply {
            hint = "https://your-backend.example"
            setText(preferences.getString("backend_url", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Online runner backend URL")
            .setMessage("Enter your backend base URL (not /api/run). For Android Emulator local testing use http://10.0.2.2:3000. Use HTTPS for a deployed server.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("API contract") { _, _ -> showBackendContract() }
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                val localHttp = value.startsWith("http://10.0.2.2") || value.startsWith("http://localhost")
                if (value.isNotBlank() && !value.startsWith("https://") && !localHttp) {
                    toast("Use HTTPS, or http://10.0.2.2:3000 for emulator testing.")
                } else {
                    preferences.edit().putString("backend_url", value).apply()
                    toast(if (value.isBlank()) "Backend URL cleared" else "Backend URL saved")
                }
            }.show()
    }

    private fun languageLabel(file: File): String = when (file.extension.lowercase()) {
        "py" -> "Python"
        "php" -> "PHP"
        "cpp", "cc", "cxx", "c", "h", "hpp" -> "C++"
        "html", "htm" -> "HTML"
        "css" -> "CSS"
        "js" -> "JavaScript"
        "kt" -> "Kotlin"
        "java" -> "Java"
        else -> "Plain Text"
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}

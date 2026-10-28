package com.neo.touchtalk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val ink = Color.rgb(13, 22, 39)
    private val panel = Color.rgb(25, 39, 60)
    private val panel2 = Color.rgb(34, 52, 77)
    private val mint = Color.rgb(105, 239, 192)
    private val sky = Color.rgb(133, 185, 255)
    private val muted = Color.rgb(174, 190, 211)
    private val white = Color.WHITE
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var statusText: TextView
    private lateinit var receiveText: TextView
    private lateinit var draftText: TextView
    private lateinit var speechInput: EditText
    private lateinit var brailleText: TextView
    private lateinit var connectButton: Button

    private val braille = mapOf(
        'a' to 1, 'b' to 3, 'c' to 9, 'd' to 25, 'e' to 17, 'f' to 11, 'g' to 27,
        'h' to 19, 'i' to 10, 'j' to 26, 'k' to 5, 'l' to 7, 'm' to 13, 'n' to 29,
        'o' to 21, 'p' to 15, 'q' to 31, 'r' to 23, 's' to 14, 't' to 30, 'u' to 37,
        'v' to 39, 'w' to 58, 'x' to 45, 'y' to 61, 'z' to 53,
        '1' to 1, '2' to 3, '3' to 9, '4' to 25, '5' to 17, '6' to 11, '7' to 27,
        '8' to 19, '9' to 10, '0' to 26,
        ',' to 2, ';' to 6, ':' to 18, '.' to 50, '!' to 22, '?' to 38, '-' to 36,
        '\'' to 4, '"' to 20, '(' to 38, ')' to 38
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.getDefault()
        }
        buildUi()
        updateConnectionStatus("Not connected", false)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(ink); isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "TouchTalk"; textSize = 29f; setTextColor(white); typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val pill = TextView(this).apply {
            text = "  BETA  "; textSize = 11f; setTextColor(ink); setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(mint, 18)
        }
        brandRow.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        brandRow.addView(pill)
        root.addView(brandRow)
        root.addView(label("Communication without barriers, in both directions.", 14f, muted).apply { setPadding(0, dp(6), 0, dp(16) }) )

        val connectionCard = card()
        connectionCard.addView(label("ESP32 CONNECTION", 12f, sky, true))
        statusText = label("Not connected", 15f, white, true)
        statusText.setPadding(0, dp(8), 0, dp(12))
        connectionCard.addView(statusText)
        connectButton = button("Connect to TouchTalk ESP32", mint, ink) { chooseDevice() }
        connectionCard.addView(connectButton, matchWrap())
        root.addView(connectionCard, marginBottom())

        val receiveCard = card()
        receiveCard.addView(label("01  ·  RECEIVE & SPEAK", 12f, mint, true))
        receiveCard.addView(label("Text received from your Braille keypad appears here.", 13f, muted).apply { setPadding(0, dp(7), 0, dp(10)) })
        receiveText = label("Waiting for a message…", 21f, white, true)
        receiveText.minHeight = dp(54)
        receiveText.gravity = Gravity.CENTER_VERTICAL
        receiveCard.addView(receiveText)
        draftText = label("Keypad preview: —", 13f, muted)
        draftText.setPadding(0, dp(8), 0, dp(12))
        receiveCard.addView(draftText)
        val speakRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        speakRow.addView(button("Read aloud", sky, ink) { speak(receiveText.text.toString()) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        speakRow.addView(button("Clear", panel2, white) {
            receiveText.text = "Waiting for a message…"; draftText.text = "Keypad preview: —"
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        receiveCard.addView(speakRow)
        root.addView(receiveCard, marginBottom())

        val sendCard = card()
        sendCard.addView(label("02  ·  SPEAK → BRAILLE", 12f, mint, true))
        sendCard.addView(label("Speak a message, review the text, then send its Braille cells to the ESP32 output pins.", 13f, muted).apply { setPadding(0, dp(7), 0, dp(12)) })
        sendCard.addView(button("🎙  Speak now", sky, ink) { startSpeechRecognition() }, matchWrap().apply { bottomMargin = dp(10) })
        speechInput = EditText(this).apply {
            hint = "Your recognized text appears here…"
            setHintTextColor(muted); setTextColor(white); textSize = 16f
            setPadding(dp(13), dp(10), dp(13), dp(10)); setSingleLine(false); minLines = 2; maxLines = 5
            background = rounded(panel2, 14)
        }
        sendCard.addView(speechInput, matchWrap().apply { bottomMargin = dp(10) })
        sendCard.addView(button("Preview Braille dots", panel2, white) { updateBraillePreview() }, matchWrap().apply { bottomMargin = dp(8) })
        brailleText = label("Braille preview will appear here.", 13f, muted)
        brailleText.setPadding(dp(4), dp(5), dp(4), dp(10))
        sendCard.addView(brailleText)
        sendCard.addView(button("Send to ESP32 outputs", mint, ink) { sendBrailleMessage() }, matchWrap())
        root.addView(sendCard, marginBottom())

        root.addView(label("QUICK NOTES", 12f, sky, true).apply { setPadding(0, dp(2), 0, dp(8)) })
        root.addView(label("• Pair the ESP32 named TouchTalk in Android Bluetooth settings first.\n• Speech recognition uses the phone's installed speech service.\n• For now, use LEDs on the six output pins to test. Never connect coils directly to ESP32 GPIO.", 13f, muted))
        root.addView(label("TouchTalk · NEO", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0) })
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(panel, 20)
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = textValue; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private fun button(textValue: String, bg: Int, fg: Int, action: () -> Unit): Button = Button(this).apply {
        text = textValue; textSize = 14f; setTextColor(fg); isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = rounded(bg, 14)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun marginBottom() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }

    private fun updateConnectionStatus(message: String, connected: Boolean) {
        if (!::statusText.isInitialized) return
        statusText.text = (if (connected) "●  " else "○  ") + message
        statusText.setTextColor(if (connected) mint else muted)
        if (::connectButton.isInitialized) connectButton.text = if (connected) "Reconnect to ESP32" else "Connect to TouchTalk ESP32"
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun chooseDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 410)
            toast("Allow Bluetooth permission, then tap Connect again.")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { toast("This phone does not support Bluetooth."); return }
        if (!adapter.isEnabled) { toast("Turn on Bluetooth in Android settings first."); return }
        val devices = try { adapter.bondedDevices?.toList().orEmpty() } catch (_: SecurityException) { emptyList() }
        if (devices.isEmpty()) {
            AlertDialog.Builder(this).setTitle("No paired devices")
                .setMessage("Pair your ESP32 named TouchTalk in Android Bluetooth settings, then return here.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = devices.map { "${it.name ?: "Unknown device"}\n${it.address}" }
        AlertDialog.Builder(this).setTitle("Choose your ESP32")
            .setItems(labels.toTypedArray()) { _, which -> connectDevice(devices[which]) }
            .setNegativeButton("Cancel", null).show()
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        updateConnectionStatus("Connecting…", false)
        thread(name = "TouchTalkBluetoothConnect") {
            try {
                try { socket?.close() } catch (_: Exception) {}
                val candidate = device.createRfcommSocketToServiceRecord(sppUuid)
                candidate.connect()
                socket = candidate
                output = candidate.outputStream
                runOnUiThread { updateConnectionStatus("Connected to ${device.name ?: "ESP32"}", true) }
                val reader = BufferedReader(InputStreamReader(candidate.inputStream, Charsets.UTF_8))
                while (candidate.isConnected) {
                    val line = reader.readLine() ?: break
                    runOnUiThread { handleIncoming(line.trim()) }
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Connection failed: ${e.message ?: "check pairing and firmware"}") }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                socket = null; output = null
                runOnUiThread { updateConnectionStatus("Not connected", false) }
            }
        }
    }

    private fun handleIncoming(line: String) {
        when {
            line.startsWith("DRAFT:") -> draftText.text = "Keypad preview: " + line.removePrefix("DRAFT:").ifBlank { "—" }
            line.startsWith("TEXT:") -> {
                val message = line.removePrefix("TEXT:").trim()
                if (message.isNotBlank()) { receiveText.text = message; speak(message) }
            }
            line.startsWith("STATUS:") -> toast(line.removePrefix("STATUS:"))
            line.isNotBlank() -> { receiveText.text = line; speak(line) }
        }
    }

    private fun sendLine(line: String) {
        thread(name = "TouchTalkBluetoothSend") {
            try {
                val stream = output ?: runOnUiThread { toast("Connect to the ESP32 first.") }.let { return@thread }
                synchronized(this) { stream.write((line + "\n").toByteArray(Charsets.UTF_8)); stream.flush() }
            } catch (e: Exception) {
                runOnUiThread { toast("Send failed: ${e.message ?: "Bluetooth disconnected"}") }
            }
        }
    }

    private fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || clean == "Waiting for a message…") { toast("There is no text to read yet."); return }
        if (ttsReady) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "touchtalk-${System.currentTimeMillis()}")
        else toast("Text-to-speech is not ready on this phone yet.")
    }

    private fun startSpeechRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 411)
            toast("Allow microphone permission, then tap Speak now again.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message for Braille conversion")
        }
        try { startActivityForResult(intent, 412) }
        catch (_: Exception) { toast("No speech-recognition service is available on this phone.") }
    }

    @Deprecated("Uses the platform speech recognition activity for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 412 && resultCode == RESULT_OK) {
            val words = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!words.isNullOrEmpty()) { speechInput.setText(words[0]); updateBraillePreview() }
        }
    }

    private fun updateBraillePreview() {
        val text = speechInput.text.toString()
        if (text.isBlank()) { brailleText.text = "Speak or type a message first."; return }
        val preview = text.lowercase(Locale.ROOT).map { ch ->
            when {
                ch == ' ' -> "SPACE"
                ch in braille -> "$ch:${dots(braille[ch]!!)}"
                ch.isLetterOrDigit() -> "$ch:unsupported"
                else -> "$ch:skip"
            }
        }.joinToString("   ")
        brailleText.text = preview
    }

    private fun dots(mask: Int): String = (1..6).filter { mask and (1 shl (it - 1)) != 0 }.joinToString("·", prefix = "[", postfix = "]").ifEmpty { "[blank]" }

    private fun sendBrailleMessage() {
        val text = speechInput.text.toString().trim()
        if (text.isBlank()) { toast("Speak or type a message first."); return }
        if (socket?.isConnected != true) { toast("Connect to the TouchTalk ESP32 first."); return }
        thread(name = "TouchTalkSendBraille") {
            try {
                val stream = output ?: throw IllegalStateException("Bluetooth output is unavailable")
                val cells = mutableListOf<Int>()
                text.lowercase(Locale.ROOT).forEach { ch ->
                    if (ch == ' ') cells.add(0)
                    else braille[ch]?.let { cells.add(it) }
                }
                for (mask in cells) {
                    synchronized(this) {
                        stream.write("CELL:$mask\n".toByteArray(Charsets.UTF_8)); stream.flush()
                    }
                    Thread.sleep(650)
                }
                synchronized(this) { stream.write("DONE\n".toByteArray(Charsets.UTF_8)); stream.flush() }
                runOnUiThread { toast("Sent ${cells.size} Braille cells to TouchTalk.") }
            } catch (e: Exception) {
                runOnUiThread { toast("Could not send message: ${e.message ?: "Bluetooth error"}") }
            }
        }
    }

    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    override fun onDestroy() {
        try { socket?.close() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }
}

package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.example.model.MacroConfig
import com.example.model.MacroConstants
import com.example.util.CpsValidationResult
import com.example.util.TapCoordinateMapper
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders the Live Keymap Editor directly over the running game using WindowManager overlays.
 *
 * Shows ONLY:
 * - Compact floating top bar (LIVE KEYMAP, CPS badge, SAVE, CANCEL)
 * - Draggable & resizable circular TRIGGER node (light white semi-transparent)
 * - Draggable & resizable circular TAP node with corner Settings icon (opens compact TAP SPEED panel)
 *
 * When dismissed or saved, all editor views are removed cleanly.
 */
class LiveEditorOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var isOpen = false

    private var currentTriggerNormX = 0.16f
    private var currentTriggerNormY = 0.72f
    private var currentTriggerSizeDp = MacroConstants.DEFAULT_TRIGGER_SIZE_DP

    private var currentTapNormX = 0.80f
    private var currentTapNormY = 0.65f
    private var currentTapSizeDp = MacroConstants.DEFAULT_TAP_SIZE_DP

    private var currentCps = MacroConstants.DEFAULT_CPS

    private var toolbarView: LinearLayout? = null
    private var toolbarCpsBadge: TextView? = null
    private var triggerNodeView: EditorCircleNodeView? = null
    private var triggerParams: WindowManager.LayoutParams? = null
    private var tapNodeView: EditorCircleNodeView? = null
    private var tapParams: WindowManager.LayoutParams? = null
    private var speedPanelView: LinearLayout? = null

    private var saveCallback: ((Float, Float, Float, Float, Float, Float, Int) -> Unit)? = null
    private var cancelCallback: (() -> Unit)? = null

    val isEditorOpen: Boolean
        get() = isOpen

    fun show(
        config: MacroConfig,
        onSave: (
            triggerNormX: Float,
            triggerNormY: Float,
            triggerSizeDp: Float,
            tapNormX: Float,
            tapNormY: Float,
            tapSizeDp: Float,
            cps: Int
        ) -> Unit,
        onCancel: () -> Unit
    ) {
        if (isOpen) {
            dismiss()
        }

        currentTriggerNormX = config.triggerNormX
        currentTriggerNormY = config.triggerNormY
        currentTriggerSizeDp = config.triggerSizeDp.coerceIn(
            MacroConstants.MIN_TRIGGER_SIZE_DP,
            MacroConstants.MAX_TRIGGER_SIZE_DP
        )
        currentTapNormX = config.tapNormX
        currentTapNormY = config.tapNormY
        currentTapSizeDp = config.tapSizeDp.coerceIn(
            MacroConstants.MIN_TAP_SIZE_DP,
            MacroConstants.MAX_TAP_SIZE_DP
        )
        currentCps = config.cps.coerceIn(MacroConstants.MIN_CPS, MacroConstants.MAX_CPS)
        saveCallback = onSave
        cancelCallback = onCancel

        isOpen = true

        addTriggerEditorNode()
        addTapEditorNode()
        addTopToolbar()
    }

    fun onDisplayChanged() {
        if (!isOpen) return
        val (displayW, displayH) = readRealDisplaySize()

        triggerParams?.let { params ->
            val pos = TapCoordinateMapper.mapTriggerToOverlayParams(
                triggerNormX = currentTriggerNormX,
                triggerNormY = currentTriggerNormY,
                overlaySizePx = params.width,
                displayWidth = displayW,
                displayHeight = displayH
            )
            params.x = pos.x
            params.y = pos.y
            triggerNodeView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        }

        tapParams?.let { params ->
            val pos = TapCoordinateMapper.mapTriggerToOverlayParams(
                triggerNormX = currentTapNormX,
                triggerNormY = currentTapNormY,
                overlaySizePx = params.width,
                displayWidth = displayW,
                displayHeight = displayH
            )
            params.x = pos.x
            params.y = pos.y
            tapNodeView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        }
    }

    fun dismiss() {
        isOpen = false
        removeSpeedPanel()
        toolbarView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        triggerNodeView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        tapNodeView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        toolbarView = null
        toolbarCpsBadge = null
        triggerNodeView = null
        triggerParams = null
        tapNodeView = null
        tapParams = null
        saveCallback = null
        cancelCallback = null
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun createBaseLayoutParams(
        width: Int,
        height: Int,
        focusable: Boolean = false
    ): WindowManager.LayoutParams {
        val baseFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val flags = if (focusable) {
            baseFlags
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            overlayWindowType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun addTopToolbar() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12f).toInt(), dpToPx(8f).toInt(), dpToPx(12f).toInt(), dpToPx(8f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18f)
                setColor(Color.argb(236, 13, 17, 21))
                setStroke(dpToPx(1f).toInt(), Color.argb(140, 0, 230, 118))
            }
        }

        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dpToPx(10f).toInt(), 0)
        }

        val titleText = TextView(context).apply {
            text = "LIVE KEYMAP"
            setTextColor(Color.rgb(242, 246, 250))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cpsBadge = TextView(context).apply {
            text = "$currentCps CPS · Drag & resize circles"
            setTextColor(Color.rgb(0, 230, 118))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        toolbarCpsBadge = cpsBadge
        titleColumn.addView(titleText)
        titleColumn.addView(cpsBadge)

        val cancelBtn = Button(context).apply {
            text = "CANCEL"
            isAllCaps = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.rgb(159, 176, 192))
            minHeight = dpToPx(38f).toInt()
            minimumHeight = dpToPx(38f).toInt()
            minWidth = dpToPx(64f).toInt()
            minimumWidth = dpToPx(64f).toInt()
            setPadding(dpToPx(10f).toInt(), dpToPx(4f).toInt(), dpToPx(10f).toInt(), dpToPx(4f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f)
                setColor(Color.rgb(26, 34, 42))
                setStroke(dpToPx(1f).toInt(), Color.rgb(38, 50, 61))
            }
            setOnClickListener {
                val cb = cancelCallback
                dismiss()
                cb?.invoke()
            }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(6f).toInt(), 1)
        }

        val saveBtn = Button(context).apply {
            text = "SAVE"
            isAllCaps = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.rgb(7, 9, 11))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            minHeight = dpToPx(38f).toInt()
            minimumHeight = dpToPx(38f).toInt()
            minWidth = dpToPx(68f).toInt()
            minimumWidth = dpToPx(68f).toInt()
            setPadding(dpToPx(12f).toInt(), dpToPx(4f).toInt(), dpToPx(12f).toInt(), dpToPx(4f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f)
                setColor(Color.rgb(0, 230, 118))
            }
            setOnClickListener {
                commitAndSave()
            }
        }

        container.addView(titleColumn)
        container.addView(cancelBtn)
        container.addView(spacer)
        container.addView(saveBtn)

        val params = createBaseLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            focusable = false
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(14f).toInt()
        }

        runCatching {
            windowManager.addView(container, params)
            toolbarView = container
        }
    }

    private fun addTriggerEditorNode() {
        val marginPx = dpToPx(NODE_HANDLE_MARGIN_DP).roundToInt()
        val circlePx = dpToPx(currentTriggerSizeDp).roundToInt()
        val windowSizePx = circlePx + marginPx * 2
        val (displayW, displayH) = readRealDisplaySize()

        val pos = TapCoordinateMapper.mapTriggerToOverlayParams(
            triggerNormX = currentTriggerNormX,
            triggerNormY = currentTriggerNormY,
            overlaySizePx = windowSizePx,
            displayWidth = displayW,
            displayHeight = displayH
        )

        val params = createBaseLayoutParams(windowSizePx, windowSizePx, focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pos.x
            y = pos.y
        }

        val nodeView = EditorCircleNodeView(
            context = context,
            isTrigger = true,
            marginPx = marginPx.toFloat(),
            minDiameterDp = MacroConstants.MIN_TRIGGER_SIZE_DP,
            maxDiameterDp = MacroConstants.MAX_TRIGGER_SIZE_DP,
            initialDiameterDp = currentTriggerSizeDp,
            initialSubLabel = null,
            onMoved = { dx, dy ->
                val (w, h) = readRealDisplaySize()
                params.x = (params.x + dx).roundToInt().coerceIn(0, max(0, w - params.width))
                params.y = (params.y + dy).roundToInt().coerceIn(0, max(0, h - params.height))
                val norm = TapCoordinateMapper.mapOverlayTopLeftToNormalizedCenter(
                    xPx = params.x,
                    yPx = params.y,
                    overlaySizePx = params.width,
                    displayWidth = w,
                    displayHeight = h
                )
                currentTriggerNormX = norm.x
                currentTriggerNormY = norm.y
                runCatching { windowManager.updateViewLayout(triggerNodeView, params) }
            },
            onResized = { newDiameterDp ->
                currentTriggerSizeDp = newDiameterDp
                val newCirclePx = dpToPx(newDiameterDp).roundToInt()
                val newWindowPx = newCirclePx + marginPx * 2
                val oldCenterX = params.x + params.width / 2f
                val oldCenterY = params.y + params.height / 2f
                val (w, h) = readRealDisplaySize()
                params.width = newWindowPx
                params.height = newWindowPx
                params.x = (oldCenterX - newWindowPx / 2f).roundToInt().coerceIn(0, max(0, w - newWindowPx))
                params.y = (oldCenterY - newWindowPx / 2f).roundToInt().coerceIn(0, max(0, h - newWindowPx))
                val norm = TapCoordinateMapper.mapOverlayTopLeftToNormalizedCenter(
                    xPx = params.x,
                    yPx = params.y,
                    overlaySizePx = params.width,
                    displayWidth = w,
                    displayHeight = h
                )
                currentTriggerNormX = norm.x
                currentTriggerNormY = norm.y
                runCatching { windowManager.updateViewLayout(triggerNodeView, params) }
            },
            onSettingsClick = null
        )

        runCatching {
            windowManager.addView(nodeView, params)
            triggerNodeView = nodeView
            triggerParams = params
        }
    }

    private fun addTapEditorNode() {
        val marginPx = dpToPx(NODE_HANDLE_MARGIN_DP).roundToInt()
        val circlePx = dpToPx(currentTapSizeDp).roundToInt()
        val windowSizePx = circlePx + marginPx * 2
        val (displayW, displayH) = readRealDisplaySize()

        val pos = TapCoordinateMapper.mapTriggerToOverlayParams(
            triggerNormX = currentTapNormX,
            triggerNormY = currentTapNormY,
            overlaySizePx = windowSizePx,
            displayWidth = displayW,
            displayHeight = displayH
        )

        val params = createBaseLayoutParams(windowSizePx, windowSizePx, focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pos.x
            y = pos.y
        }

        val nodeView = EditorCircleNodeView(
            context = context,
            isTrigger = false,
            marginPx = marginPx.toFloat(),
            minDiameterDp = MacroConstants.MIN_TAP_SIZE_DP,
            maxDiameterDp = MacroConstants.MAX_TAP_SIZE_DP,
            initialDiameterDp = currentTapSizeDp,
            initialSubLabel = "$currentCps CPS",
            onMoved = { dx, dy ->
                val (w, h) = readRealDisplaySize()
                params.x = (params.x + dx).roundToInt().coerceIn(0, max(0, w - params.width))
                params.y = (params.y + dy).roundToInt().coerceIn(0, max(0, h - params.height))
                val norm = TapCoordinateMapper.mapOverlayTopLeftToNormalizedCenter(
                    xPx = params.x,
                    yPx = params.y,
                    overlaySizePx = params.width,
                    displayWidth = w,
                    displayHeight = h
                )
                currentTapNormX = norm.x
                currentTapNormY = norm.y
                runCatching { windowManager.updateViewLayout(tapNodeView, params) }
            },
            onResized = { newDiameterDp ->
                currentTapSizeDp = newDiameterDp
                val newCirclePx = dpToPx(newDiameterDp).roundToInt()
                val newWindowPx = newCirclePx + marginPx * 2
                val oldCenterX = params.x + params.width / 2f
                val oldCenterY = params.y + params.height / 2f
                val (w, h) = readRealDisplaySize()
                params.width = newWindowPx
                params.height = newWindowPx
                params.x = (oldCenterX - newWindowPx / 2f).roundToInt().coerceIn(0, max(0, w - newWindowPx))
                params.y = (oldCenterY - newWindowPx / 2f).roundToInt().coerceIn(0, max(0, h - newWindowPx))
                val norm = TapCoordinateMapper.mapOverlayTopLeftToNormalizedCenter(
                    xPx = params.x,
                    yPx = params.y,
                    overlaySizePx = params.width,
                    displayWidth = w,
                    displayHeight = h
                )
                currentTapNormX = norm.x
                currentTapNormY = norm.y
                runCatching { windowManager.updateViewLayout(tapNodeView, params) }
            },
            onSettingsClick = {
                showTapSpeedPanel()
            }
        )

        runCatching {
            windowManager.addView(nodeView, params)
            tapNodeView = nodeView
            tapParams = params
        }
    }

    private fun showTapSpeedPanel() {
        removeSpeedPanel()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18f).toInt(), dpToPx(16f).toInt(), dpToPx(18f).toInt(), dpToPx(16f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18f)
                setColor(Color.rgb(20, 26, 32))
                setStroke(dpToPx(1.2f).toInt(), Color.argb(170, 0, 230, 118))
            }
        }

        val title = TextView(context).apply {
            text = "TAP SPEED"
            setTextColor(Color.rgb(242, 246, 250))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitle = TextView(context).apply {
            text = "Set taps per second (${MacroConstants.MIN_CPS}–${MacroConstants.MAX_CPS} CPS)"
            setTextColor(Color.rgb(159, 176, 192))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dpToPx(2f).toInt(), 0, dpToPx(10f).toInt())
        }

        val intervalText = TextView(context).apply {
            val ms = TapCoordinateMapper.cpsToExactIntervalMs(currentCps)
            text = String.format(Locale.US, "%d CPS · ~%.1f ms per tap", currentCps, ms)
            setTextColor(Color.rgb(0, 230, 118))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dpToPx(4f).toInt(), 0, dpToPx(8f).toInt())
        }

        val errorText = TextView(context).apply {
            text = ""
            setTextColor(Color.rgb(255, 82, 82))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            visibility = View.GONE
            setPadding(0, dpToPx(4f).toInt(), 0, 0)
        }

        val cpsInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            setText(currentCps.toString())
            setSelection(text.length)
            setTextColor(Color.rgb(242, 246, 250))
            setHintTextColor(Color.rgb(99, 115, 132))
            hint = "1 - 30"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dpToPx(12f).toInt(), dpToPx(10f).toInt(), dpToPx(12f).toInt(), dpToPx(10f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f)
                setColor(Color.rgb(13, 17, 21))
                setStroke(dpToPx(1f).toInt(), Color.rgb(38, 50, 61))
            }
        }

        val presetButtons = mutableListOf<Pair<Int, Button>>()
        fun stylePresetButtons(selected: Int?) {
            presetButtons.forEach { (preset, btn) ->
                val isSelected = selected == preset
                btn.setTextColor(if (isSelected) Color.rgb(7, 9, 11) else Color.rgb(242, 246, 250))
                btn.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(10f)
                    setColor(if (isSelected) Color.rgb(0, 230, 118) else Color.rgb(26, 34, 42))
                    setStroke(
                        dpToPx(1f).toInt(),
                        if (isSelected) Color.rgb(0, 230, 118) else Color.rgb(38, 50, 61)
                    )
                }
            }
        }

        cpsInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                when (val result = TapCoordinateMapper.validateCps(s?.toString().orEmpty())) {
                    is CpsValidationResult.Valid -> {
                        errorText.visibility = View.GONE
                        val ms = TapCoordinateMapper.cpsToExactIntervalMs(result.cps)
                        intervalText.text = String.format(Locale.US, "%d CPS · ~%.1f ms per tap", result.cps, ms)
                        stylePresetButtons(result.cps)
                    }
                    is CpsValidationResult.Invalid -> {
                        errorText.text = result.reason
                        errorText.visibility = View.VISIBLE
                        stylePresetButtons(null)
                    }
                }
            }
        })

        val presetsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(10f).toInt(), 0, dpToPx(4f).toInt())
        }
        MacroConstants.CPS_PRESETS.forEachIndexed { index, preset ->
            val btn = Button(context).apply {
                text = "$preset CPS"
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                minHeight = dpToPx(40f).toInt()
                minimumHeight = dpToPx(40f).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(40f).toInt(),
                    1f
                ).apply {
                    if (index > 0) marginStart = dpToPx(6f).toInt()
                }
                setOnClickListener {
                    cpsInput.setText(preset.toString())
                    cpsInput.setSelection(cpsInput.text.length)
                }
            }
            presetButtons += preset to btn
            presetsRow.addView(btn)
        }
        stylePresetButtons(currentCps)

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12f).toInt(), 0, 0)
        }

        val cancelPanelBtn = Button(context).apply {
            text = "Cancel"
            isAllCaps = false
            setTextColor(Color.rgb(159, 176, 192))
            minHeight = dpToPx(44f).toInt()
            minimumHeight = dpToPx(44f).toInt()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f)
                setColor(Color.rgb(26, 34, 42))
            }
            setOnClickListener { removeSpeedPanel() }
        }

        val applyBtn = Button(context).apply {
            text = "Apply"
            isAllCaps = false
            setTextColor(Color.rgb(7, 9, 11))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            minHeight = dpToPx(44f).toInt()
            minimumHeight = dpToPx(44f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(8f).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f)
                setColor(Color.rgb(0, 230, 118))
            }
            setOnClickListener {
                when (val result = TapCoordinateMapper.validateCps(cpsInput.text.toString())) {
                    is CpsValidationResult.Valid -> {
                        currentCps = result.cps
                        tapNodeView?.updateSubLabel("$currentCps CPS")
                        toolbarCpsBadge?.text = "$currentCps CPS · Drag & resize circles"
                        removeSpeedPanel()
                    }
                    is CpsValidationResult.Invalid -> {
                        errorText.text = result.reason
                        errorText.visibility = View.VISIBLE
                    }
                }
            }
        }

        actionsRow.addView(cancelPanelBtn)
        actionsRow.addView(applyBtn)

        card.addView(title)
        card.addView(subtitle)
        card.addView(cpsInput)
        card.addView(errorText)
        card.addView(presetsRow)
        card.addView(intervalText)
        card.addView(actionsRow)

        val (displayW, _) = readRealDisplaySize()
        val panelWidth = min(dpToPx(320f).toInt(), (displayW * 0.90f).toInt())
        val params = createBaseLayoutParams(
            width = panelWidth,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            focusable = true
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching {
            windowManager.addView(card, params)
            speedPanelView = card
        }
    }

    private fun removeSpeedPanel() {
        speedPanelView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        speedPanelView = null
    }

    private fun commitAndSave() {
        val (displayW, displayH) = readRealDisplaySize()
        triggerParams?.let { params ->
            val norm = TapCoordinateMapper.mapOverlayTopLeftToNormalizedCenter(
                xPx = params.x,
                yPx = params.y,
                overlaySizePx = params.width,
                displayWidth = displayW,
                displayHeight = displayH
            )
            currentTriggerNormX = norm.x
            currentTriggerNormY = norm.y
        }
        tapParams?.let { params ->
            val norm = TapCoordinateMapper.mapOverlayTopLeftToNormalizedCenter(
                xPx = params.x,
                yPx = params.y,
                overlaySizePx = params.width,
                displayWidth = displayW,
                displayHeight = displayH
            )
            currentTapNormX = norm.x
            currentTapNormY = norm.y
        }

        val cb = saveCallback
        val tX = currentTriggerNormX
        val tY = currentTriggerNormY
        val tSize = currentTriggerSizeDp
        val tapX = currentTapNormX
        val tapY = currentTapNormY
        val tapSize = currentTapSizeDp
        val cps = currentCps

        dismiss()
        cb?.invoke(tX, tY, tSize, tapX, tapY, tapSize, cps)
    }

    private fun readRealDisplaySize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density

    companion object {
        private const val NODE_HANDLE_MARGIN_DP = 10f
    }
}

/**
 * Interactive circular node used inside the Live Keymap Editor over the running game.
 * Supports smooth single-finger dragging, bottom-right corner resizing, and (for TAP) a top-right Settings icon.
 */
@SuppressLint("ViewConstructor")
private class EditorCircleNodeView(
    context: Context,
    private val isTrigger: Boolean,
    private val marginPx: Float,
    private val minDiameterDp: Float,
    private val maxDiameterDp: Float,
    initialDiameterDp: Float,
    initialSubLabel: String?,
    private val onMoved: (dx: Float, dy: Float) -> Unit,
    private val onResized: (newDiameterDp: Float) -> Unit,
    private val onSettingsClick: (() -> Unit)?
) : View(context) {

    private var diameterDp = initialDiameterDp
    private var subLabel = initialSubLabel

    private enum class DragMode { NONE, MOVE, RESIZE, SETTINGS }
    private var dragMode = DragMode.NONE
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var startDiameterDp = initialDiameterDp

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (isTrigger) Color.argb(66, 255, 255, 255) else Color.argb(64, 0, 229, 255)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.4f)
        color = if (isTrigger) Color.argb(235, 255, 255, 255) else Color.rgb(0, 229, 255)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(105, 240, 174)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val handleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(13, 17, 21)
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        color = if (isTrigger) Color.rgb(0, 230, 118) else Color.rgb(0, 229, 255)
    }

    private val handleIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.8f)
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    fun updateSubLabel(label: String) {
        subLabel = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = max(1f, (min(width, height) / 2f) - marginPx)

        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        labelPaint.textSize = (radius * 0.34f).coerceIn(spToPx(9f), spToPx(14f))
        if (subLabel != null) {
            canvas.drawText(if (isTrigger) "TRIGGER" else "TAP", cx, cy - dpToPx(2f), labelPaint)
            subLabelPaint.textSize = (radius * 0.27f).coerceIn(spToPx(8f), spToPx(11f))
            canvas.drawText(subLabel!!, cx, cy + dpToPx(11f), subLabelPaint)
        } else {
            val offset = (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText("TRIGGER", cx, cy - offset, labelPaint)
        }

        // Bottom-right resize handle
        val resizeCenter = resizeHandleCenter()
        val handleRadius = dpToPx(10f)
        canvas.drawCircle(resizeCenter.first, resizeCenter.second, handleRadius, handleBgPaint)
        canvas.drawCircle(resizeCenter.first, resizeCenter.second, handleRadius, handleBorderPaint)
        val arrowDelta = dpToPx(3.6f)
        canvas.drawLine(
            resizeCenter.first - arrowDelta,
            resizeCenter.second - arrowDelta,
            resizeCenter.first + arrowDelta,
            resizeCenter.second + arrowDelta,
            handleIconPaint
        )

        // Top-right Settings gear icon on TAP node only
        if (!isTrigger && onSettingsClick != null) {
            val gearCenter = settingsHandleCenter()
            canvas.drawCircle(gearCenter.first, gearCenter.second, handleRadius, handleBgPaint)
            canvas.drawCircle(gearCenter.first, gearCenter.second, handleRadius, handleBorderPaint)
            // Minimal crisp gear glyph (outer ring + center dot)
            canvas.drawCircle(gearCenter.first, gearCenter.second, dpToPx(4.2f), handleIconPaint)
            canvas.drawCircle(gearCenter.first, gearCenter.second, dpToPx(1.5f), handleIconPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                downRawX = event.rawX
                downRawY = event.rawY
                startDiameterDp = diameterDp

                val gearCenter = settingsHandleCenter()
                val resizeCenter = resizeHandleCenter()
                val hitRadius = dpToPx(19f)

                dragMode = when {
                    !isTrigger && onSettingsClick != null &&
                        hypot((event.x - gearCenter.first).toDouble(), (event.y - gearCenter.second).toDouble()) <= hitRadius ->
                        DragMode.SETTINGS
                    hypot((event.x - resizeCenter.first).toDouble(), (event.y - resizeCenter.second).toDouble()) <= hitRadius ->
                        DragMode.RESIZE
                    else -> DragMode.MOVE
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                lastRawX = event.rawX
                lastRawY = event.rawY

                when (dragMode) {
                    DragMode.MOVE -> onMoved(dx, dy)
                    DragMode.RESIZE -> {
                        val totalDeltaPx = ((event.rawX - downRawX) + (event.rawY - downRawY)) / 2f
                        val deltaDp = totalDeltaPx / resources.displayMetrics.density
                        val updated = (startDiameterDp + deltaDp).coerceIn(minDiameterDp, maxDiameterDp)
                        if (updated != diameterDp) {
                            diameterDp = updated
                            onResized(updated)
                        }
                    }
                    DragMode.SETTINGS, DragMode.NONE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragMode == DragMode.SETTINGS) {
                    val movedDist = hypot(
                        (event.rawX - downRawX).toDouble(),
                        (event.rawY - downRawY).toDouble()
                    )
                    if (movedDist < dpToPx(14f)) {
                        onSettingsClick?.invoke()
                    }
                }
                dragMode = DragMode.NONE
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resizeHandleCenter(): Pair<Float, Float> {
        val r = dpToPx(11f)
        return (width - r) to (height - r)
    }

    private fun settingsHandleCenter(): Pair<Float, Float> {
        val r = dpToPx(11f)
        return (width - r) to r
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    @Suppress("DEPRECATION")
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}

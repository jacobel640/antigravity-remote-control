package com.antigravity.remotecontrol.ui

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import com.antigravity.remotecontrol.R
import com.antigravity.remotecontrol.security.ISecurePreferencesManager
import com.antigravity.remotecontrol.security.SecurePreferencesManager
import com.antigravity.remotecontrol.security.UrlValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class UrlConfigDialog(
    private val context: Context,
    private val preferencesManager: ISecurePreferencesManager
) {

    fun show(
        isFirstRun: Boolean = false,
        onUrlSaved: ((String) -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_url_config, null)
        dialog.setContentView(view)

        dialog.setCancelable(!isFirstRun)
        dialog.setCanceledOnTouchOutside(!isFirstRun)

        val switchSslBypass = view.findViewById<MaterialSwitch>(R.id.switchSslBypass)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)

        if (preferencesManager is SecurePreferencesManager) {
            switchSslBypass.isChecked = preferencesManager.isSslBypassEnabled()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            if (preferencesManager is SecurePreferencesManager) {
                preferencesManager.setSslBypassEnabled(switchSslBypass.isChecked)
            }
            dialog.dismiss()
            onUrlSaved?.invoke("https://antigravity.google.com/")
        }

        dialog.show()
        return dialog
    }
}

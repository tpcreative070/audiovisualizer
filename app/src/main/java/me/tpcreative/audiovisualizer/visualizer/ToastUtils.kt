package me.tpcreative.audiovisualizer.visualizer

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import me.tpcreative.audiovisualizer.R

fun Context.showToast(@StringRes restId: Int) {
    Toast.makeText(this, R.string.no_permission, Toast.LENGTH_LONG).show()
}
package me.tpcreative.audiovisualizer.visualizer

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.withStyledAttributes
import me.tpcreative.audiovisualizer.R


class MyCustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AbstractComposeView(context, attrs, defStyle) {

    private val audioPlayer = AudioPlayer()
    var myProperty by mutableStateOf("A string")

    init {
        context.withStyledAttributes(attrs, R.styleable.MyStyleable) {
            myProperty = getString(R.styleable.MyStyleable_myAttribute) ?: "Null"
        }
    }

    // The important part
    @Composable
    override fun Content() {
        val visualizerData = remember { mutableStateOf(VisualizerData()) }
        audioPlayer.play(context.assets, "instru.mp3", visualizerData)
        MyComposable(title = myProperty,visualizerData)
    }
}

@Composable
fun MyComposable(title: String,  visualizerData: MutableState<VisualizerData>) {
    Text(text = title, textAlign = TextAlign.Center)
    LazyColumn {
        item {
            StackedBarEqualizer(
                Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(vertical = 20.dp)
                    .background(Color(0x50000000)),
                data = visualizerData.value,
                barCount = 1
            )
        }
    }
}
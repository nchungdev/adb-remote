package app.duinch.adbagent.features.pairing.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class TutorialSlide(
    val stepLabel: String,
    val title: String,
    val description: String,
    val illustrationType: Int
)

private val SLIDES = listOf(
    TutorialSlide(
        stepLabel = "BƯỚC 1 / 3",
        title = "Bật Gỡ lỗi không dây",
        description = "Bấm nút bên dưới để mở Cài đặt, sau đó bật công tắc \"Gỡ lỗi không dây\".",
        illustrationType = 1
    ),
    TutorialSlide(
        stepLabel = "BƯỚC 2 / 3",
        title = "Chọn Ghép nối bằng mã",
        description = "Chạm vào dòng \"Ghép nối bằng mã ghép nối\" để hiện hộp thoại chứa 6 chữ số.",
        illustrationType = 2
    ),
    TutorialSlide(
        stepLabel = "BƯỚC 3 / 3",
        title = "Chụp ảnh màn hình (Screenshot)",
        description = "Nhấn Nguồn + Giảm âm lượng. AI sẽ tự động đọc mã và ghép đôi tức thì!",
        illustrationType = 3
    )
)

@Composable
internal fun PairingTutorialCarousel(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val slide = SLIDES[page]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (slide.illustrationType) {
                    1 -> SlideOneToggleIllustration()
                    2 -> SlideTwoDialogIllustration()
                    else -> SlideThreeScreenshotIllustration()
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = slide.stepLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    minLines = 2
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(SLIDES.size) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(targetValue = if (isSelected) 18.dp else 6.dp, label = "dotWidth")
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "dotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(width)
                        .background(dotColor, CircleShape)
                )
            }
        }
    }
}

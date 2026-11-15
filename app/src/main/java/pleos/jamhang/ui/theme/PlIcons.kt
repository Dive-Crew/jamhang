package pleos.jamhang.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import pleos.jamhang.R

/**
 * Pleos Connect 아이콘 (design assets 2.15에서 벡터 추출, res/drawable/ic_pl_*).
 * Pleos 세트에 없는 아이콘(자물쇠·주차·기록·경로·책갈피)은 Material Icons를 그대로 쓴다.
 */
object PlIcons {
    val Close: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_close)
    val Check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_check)
    val ArrowRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_arrow_right)
    val ArrowLeft: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_arrow_left)
    val Sound: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_sound)
    val SoundMute: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_sound_mute)
    val Mic: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_mic)
    val Charging: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_pl_charging)
}
